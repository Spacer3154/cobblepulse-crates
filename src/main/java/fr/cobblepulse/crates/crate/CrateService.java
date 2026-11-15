package fr.cobblepulse.crates.crate;

import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.config.CrateDef;
import fr.cobblepulse.crates.config.GeneralConfig;
import fr.cobblepulse.crates.config.RewardDef;
import fr.cobblepulse.crates.core.Msg;
import fr.cobblepulse.crates.core.Perms;
import fr.cobblepulse.crates.core.Players;
import fr.cobblepulse.crates.core.Reg;
import fr.cobblepulse.crates.core.Scheduler;
import fr.cobblepulse.crates.core.TextUtil;
import fr.cobblepulse.crates.net.Net;
import fr.cobblepulse.crates.net.Views;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Apercu, ouverture (cle consommee, tirage pondere, animation), remise de la recompense et effets. */
public final class CrateService {
    /** Ouverture en cours : la recompense est donnee a la fin de l'animation (ou plus tot : passer, deconnexion, arret). */
    public static final class Opening {
        public final UUID player;
        public final String playerName;
        public final CrateDef crate;
        public final RewardDef reward;
        public final Rewards.Prepared prepared;
        public final ResourceKey<Level> dim;
        public final BlockPos pos;
        public final long endTick;
        boolean done;

        Opening(ServerPlayer p, CrateDef crate, RewardDef reward, Rewards.Prepared prepared, ResourceKey<Level> dim, BlockPos pos, long endTick) {
            this.player = p.getUUID();
            this.playerName = p.getGameProfile().getName();
            this.crate = crate;
            this.reward = reward;
            this.prepared = prepared;
            this.dim = dim;
            this.pos = pos;
            this.endTick = endTick;
        }
    }

    private static final Map<UUID, Opening> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> LAST_CLICK = new HashMap<>();
    /** Auto-test : derniere recompense remise par joueur. */
    public static final Map<UUID, String> LAST_REWARD = new HashMap<>();

    private CrateService() {}

    public static CrateDef crate(String id) {
        return id == null ? null : Cfg.CRATES.get(CrateDef.cleanId(id));
    }

    public static boolean maintenance(CrateDef c) {
        return Cfg.general.maintenanceGlobale || c.maintenance;
    }

    public static boolean busy(ServerPlayer p) {
        Opening o = ACTIVE.get(p.getUUID());
        return o != null && !o.done;
    }

    // ------------------------------------------------------------------ Cles

    public static int countItem(ServerPlayer p, Item item) {
        if (item == null) return 0;
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** Retire {@code n} objets de l'inventaire (tout ou rien). */
    public static boolean takeItem(ServerPlayer p, Item item, int n) {
        if (countItem(p, item) < n) return false;
        int left = n;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            left -= take;
        }
        inv.setChanged();
        p.containerMenu.broadcastChanges();
        return true;
    }

    public static Item keyItem(CrateDef c) {
        return Reg.item(c.cle, null);
    }

    // ------------------------------------------------------------------ Tirage

    public static RewardDef roll(CrateDef c, Set<String> exclude) {
        double total = 0;
        for (RewardDef r : c.recompenses) if (r.poids > 0 && !exclude.contains(r.id)) total += r.poids;
        if (total <= 0) return null;
        double x = ThreadLocalRandom.current().nextDouble() * total;
        RewardDef last = null;
        for (RewardDef r : c.recompenses) {
            if (r.poids <= 0 || exclude.contains(r.id)) continue;
            last = r;
            x -= r.poids;
            if (x < 0) return r;
        }
        return last;
    }

    // ------------------------------------------------------------------ Apercu

    /** Clic (gauche ou droit) sur un conteneur lie : ouvre l'apercu. */
    public static void click(ServerPlayer p, ServerLevel level, BlockPos pos, String crateId) {
        long now = Scheduler.currentTick();
        Long last = LAST_CLICK.get(p.getUUID());
        if (last != null && now - last < 8) return;
        LAST_CLICK.put(p.getUUID(), now);
        preview(p, crateId, level, pos);
    }

    public static void preview(ServerPlayer p, String crateId, ServerLevel level, BlockPos pos) {
        CrateDef c = crate(crateId);
        if (c == null) {
            Msg.send(p, "caisse.unknown", "crate", crateId);
            return;
        }
        if (!Net.hasClient(p)) {
            Msg.send(p, "general.no_client");
            return;
        }
        Net.send(p, "crate_preview", Views.preview(p, c, pos));
    }

    // ------------------------------------------------------------------ Ouverture

    /**
     * Ouvre la caisse liee au conteneur {@code pos}.
     * @return null si l'ouverture a commence, sinon la cle du message d'erreur.
     */
    public static String open(ServerPlayer p, String crateId, BlockPos pos) {
        CrateDef c = crate(crateId);
        if (c == null) return fail(p, "caisse.unknown", "crate", crateId);
        if (busy(p)) return fail(p, "caisse.busy");
        if (pos == null || !c.id.equals(Links.crateAt(p.level(), pos))) return fail(p, "caisse.remote");
        double d = Cfg.general.distanceMax;
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > d * d) return fail(p, "caisse.too_far");
        if (!Perms.canOpen(p, c.id)) return fail(p, "caisse.no_open_perm");
        if (maintenance(c) && !Perms.bypassMaintenance(p)) {
            return fail(p, Cfg.general.maintenanceGlobale ? "caisse.maintenance_global" : "caisse.maintenance", "crate", TextUtil.strip(c.nom));
        }
        Item key = keyItem(c);
        if (key == null) return fail(p, "caisse.bad_key", "key", c.cle);
        if (countItem(p, key) < c.clesRequises) {
            return fail(p, "caisse.no_key", "need", c.clesRequises, "key", new ItemStack(key).getHoverName().getString());
        }
        if (c.totalWeight() <= 0) return fail(p, "caisse.empty");
        // Tirage ; une recompense impossible a preparer (pool vide, objet invalide...) est retiree et on retire au sort
        Set<String> tried = new HashSet<>();
        List<String> errors = new ArrayList<>();
        RewardDef r = null;
        Rewards.Prepared prep = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            r = roll(c, tried);
            if (r == null) break;
            List<String> errs = new ArrayList<>();
            prep = Rewards.prepare(r, errs);
            if (prep != null) break;
            errors.addAll(errs);
            Crates.LOG.warn("[Crates] Caisse {} : recompense {} ignoree ({})", c.id, r.id, String.join(", ", errs));
            tried.add(r.id);
            r = null;
        }
        if (prep == null) return fail(p, "caisse.error", "error", String.join(", ", errors));
        if (!takeItem(p, key, c.clesRequises)) return fail(p, "caisse.no_key", "need", c.clesRequises, "key", new ItemStack(key).getHoverName().getString());
        boolean instant = "instantanee".equals(c.animation);
        int ticks = instant ? 1 : (int) Math.round(c.dureeAnimation * 20) + 6;
        ServerLevel level = p.serverLevel();
        Opening o = new Opening(p, c, r, prep, level.dimension(), pos.immutable(), Scheduler.currentTick() + ticks);
        ACTIVE.put(p.getUUID(), o);
        Net.send(p, "crate_spin", Views.spin(p, c, r, prep, instant));
        WorldFx.openLid(level, pos, c);
        if (instant) complete(o, p);
        return null;
    }

    private static String fail(ServerPlayer p, String key, Object... kv) {
        Msg.send(p, key, kv);
        Net.toast(p, false, TextUtil.strip(Msg.format(key, kv)).replaceFirst("^.*» ", ""));
        return key;
    }

    public static void skip(ServerPlayer p) {
        Opening o = ACTIVE.get(p.getUUID());
        if (o != null && !o.done && o.crate.ouvertureRapide) complete(o, p);
    }

    public static void tick(MinecraftServer server) {
        long now = Scheduler.currentTick();
        if (!ACTIVE.isEmpty()) {
            for (Opening o : new ArrayList<>(ACTIVE.values())) {
                if (o.done) continue;
                if (now >= o.endTick) {
                    complete(o, Players.get(o.player));
                } else if (now % 3 == 0) {
                    ParticleOptions part = WorldFx.particle(o.crate.particuleOuverture);
                    if (part != null) WorldFx.spiral(server.getLevel(o.dim), o.pos, part, now);
                }
            }
        }
        WorldFx.idle(server, now);
    }

    /** Remise de la recompense et effets de fin. */
    static void complete(Opening o, ServerPlayer p) {
        if (o.done) return;
        o.done = true;
        ACTIVE.remove(o.player);
        MinecraftServer server = Crates.server();
        if (p == null) {
            Crates.LOG.error("[Crates] {} introuvable a la fin de l'ouverture de {} : recompense {} non remise", o.playerName, o.crate.id, o.reward.id);
            return;
        }
        try {
            o.prepared.give(p);
        } catch (Throwable t) {
            Crates.LOG.error("[Crates] Erreur en donnant {} ({}) a {}", o.reward.id, o.crate.id, o.playerName, t);
        }
        LAST_REWARD.put(o.player, o.crate.id + ":" + o.reward.id);
        GeneralConfig.Rarete rar = Cfg.general.rarity(o.reward.rarete);
        MutableComponent name = Component.empty().withStyle(s -> s.withColor(rar.color())).append(o.prepared.name);
        ServerLevel level = server == null ? null : server.getLevel(o.dim);
        if (level != null) {
            WorldFx.reveal(level, o.pos, rar);
            int holo = (int) Math.round(Cfg.general.hologramme.dureeSecondes * 20);
            if (o.crate.hologramme && Cfg.general.hologramme.actif) WorldFx.hologram(level, o.pos, o.prepared.icon, name, holo);
            WorldFx.closeLidLater(level, o.pos, o.crate.hologramme && Cfg.general.hologramme.actif ? holo : 30);
        }
        MutableComponent hoverName = name.copy();
        if (!o.prepared.icon.isEmpty()) {
            hoverName = hoverName.withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(o.prepared.icon))));
        }
        if (Msg.reachable(p)) p.sendSystemMessage(Msg.withComponent("caisse.won", "reward", hoverName, "crate", o.crate.nom));
        if (o.reward.annonce && Cfg.general.annonces) {
            Msg.broadcast(Msg.withComponent("caisse.announce", "reward", hoverName, "player", o.playerName, "crate", o.crate.nom));
        }
        OpenLog.log(o.playerName, o.crate.id, o.reward.id + " " + TextUtil.strip(o.prepared.name.getString()), rar.id, "");
        Net.send(p, "crate_done", Views.done(p, o.crate));
    }

    public static void onDisconnect(ServerPlayer p) {
        Opening o = ACTIVE.get(p.getUUID());
        if (o != null && !o.done) complete(o, p);
        LAST_CLICK.remove(p.getUUID());
    }

    /** Arret du serveur : toutes les ouvertures en cours sont terminees immediatement. */
    public static void shutdown() {
        for (Opening o : new ArrayList<>(ACTIVE.values())) {
            if (!o.done) complete(o, Players.get(o.player));
        }
        ACTIVE.clear();
        LAST_CLICK.clear();
    }

    public static int active() {
        return ACTIVE.size();
    }
}
