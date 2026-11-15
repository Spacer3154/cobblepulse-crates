package fr.cobblepulse.crates.net;

import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.builder.BuilderService;
import fr.cobblepulse.crates.config.BuilderConfig;
import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.config.CrateDef;
import fr.cobblepulse.crates.config.GeneralConfig;
import fr.cobblepulse.crates.config.Locations;
import fr.cobblepulse.crates.config.RewardDef;
import fr.cobblepulse.crates.core.Json;
import fr.cobblepulse.crates.core.Msg;
import fr.cobblepulse.crates.core.Perms;
import fr.cobblepulse.crates.core.Players;
import fr.cobblepulse.crates.core.Reg;
import fr.cobblepulse.crates.core.TextUtil;
import fr.cobblepulse.crates.crate.CrateService;
import fr.cobblepulse.crates.crate.Links;
import fr.cobblepulse.crates.crate.Pools;
import fr.cobblepulse.crates.crate.Rewards;
import fr.cobblepulse.crates.crate.WorldFx;
import fr.cobblepulse.crates.pokemon.MonSpec;
import fr.cobblepulse.crates.pokemon.PokemonFactory;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reseau cote serveur : apercu / ouverture des caisses, editeur, menu Voeu ADN, positions des caisses. */
public final class Net {
    /** Auto-test : derniere donnee envoyee par type. */
    public static final Map<String, String> LAST_SENT = new HashMap<>();

    private Net() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Payloads.Action.TYPE, Payloads.Action.CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.Data.TYPE, Payloads.Data.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Action.TYPE, (payload, ctx) -> ctx.server().execute(() -> {
            try {
                handle(ctx.player(), payload.action(), Views.json(payload.json()));
            } catch (Throwable t) {
                Crates.LOG.error("[Crates] Action {} de {} en echec", payload.action(), ctx.player().getGameProfile().getName(), t);
                toast(ctx.player(), false, "Erreur : " + t.getMessage());
            }
        }));
    }

    public static boolean hasClient(ServerPlayer p) {
        return p != null && !Players.isTest(p.getUUID()) && p.connection != null && ServerPlayNetworking.canSend(p, Payloads.Data.TYPE);
    }

    public static void send(ServerPlayer p, String kind, JsonElement json) {
        String s = Json.COMPACT.toJson(json);
        LAST_SENT.put(kind, s);
        if (hasClient(p)) ServerPlayNetworking.send(p, new Payloads.Data(kind, s));
    }

    public static void toast(ServerPlayer p, boolean ok, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", ok);
        o.addProperty("message", message);
        send(p, "toast", o);
    }

    // ------------------------------------------------------------------ Positions des caisses (cote client)

    public static void sendPositions(ServerPlayer p) {
        JsonObject o = new JsonObject();
        String dim = Links.dim(p.level());
        o.addProperty("dim", dim);
        JsonArray a = new JsonArray();
        for (Locations.Loc l : Links.inDimension(dim)) a.add(BlockPos.asLong(l.x, l.y, l.z));
        o.add("pos", a);
        send(p, "positions", o);
    }

    public static void syncAllPositions() {
        MinecraftServer s = Crates.server();
        if (s == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) sendPositions(p);
    }

    // ------------------------------------------------------------------ Lecture des arguments

    static String str(JsonElement j, String key, String def) {
        try {
            JsonElement e = j.getAsJsonObject().get(key);
            return e == null || e.isJsonNull() ? def : e.getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    static int num(JsonElement j, String key, int def) {
        try {
            return j.getAsJsonObject().get(key).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    static BlockPos pos(JsonElement j) {
        try {
            JsonObject o = j.getAsJsonObject();
            if (!o.has("x")) return null;
            return new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Actions

    static void handle(ServerPlayer p, String action, JsonElement a) {
        if (action.startsWith("admin_")) {
            admin(p, action, a);
            return;
        }
        switch (action) {
            case "crate_preview" -> {
                BlockPos pos = pos(a);
                String id = str(a, "crate", "");
                if (pos != null && !id.equals(Links.crateAt(p.level(), pos))) pos = null;
                if (pos == null && !Perms.player(p, Perms.PREVIEW)) {
                    Msg.send(p, "general.no_permission");
                    return;
                }
                CrateService.preview(p, id, p.serverLevel(), pos);
            }
            case "crate_open" -> CrateService.open(p, str(a, "crate", ""), pos(a));
            case "crate_skip" -> CrateService.skip(p);
            case "builder_open" -> BuilderService.open(p);
            case "builder_refresh" -> {
                if (Cfg.builder.actif && Perms.player(p, Perms.BUILDER_OPEN)) send(p, "builder_data", BuilderService.root(p));
            }
            case "builder_box" -> {
                if (Cfg.builder.actif && Perms.player(p, Perms.BUILDER_OPEN)) send(p, "builder_box", BuilderService.box(p, num(a, "box", 0)));
            }
            case "builder_apply" -> BuilderService.apply(p, str(a, "uuid", ""), str(a, "op", ""), a.getAsJsonObject().get("value"));
            default -> Crates.LOG.debug("[Crates] Action inconnue {}", action);
        }
    }

    // ------------------------------------------------------------------ Editeur

    public static boolean openAdmin(ServerPlayer p) {
        if (!hasClient(p)) return false;
        send(p, "admin_open", Views.admin(p));
        return true;
    }

    private static boolean need(ServerPlayer p, String node) {
        if (Perms.admin(p, node)) return true;
        toast(p, false, "Permission manquante : " + node);
        return false;
    }

    private static void result(ServerPlayer p, boolean ok, String message, List<String> warnings) {
        JsonObject r = new JsonObject();
        r.addProperty("ok", ok);
        r.addProperty("message", message);
        JsonArray w = new JsonArray();
        for (String s : warnings) w.add(s);
        r.add("warnings", w);
        send(p, "admin_result", r);
        send(p, "admin_data", Views.admin(p));
    }

    private static void admin(ServerPlayer p, String action, JsonElement a) {
        if (!need(p, Perms.ADMIN_EDITOR)) return;
        String by = p.getGameProfile().getName();
        List<String> warnings = new ArrayList<>();
        switch (action) {
            case "admin_open" -> openAdmin(p);
            case "admin_save_crate" -> {
                String id = CrateDef.cleanId(str(a, "id", ""));
                JsonElement data = a.getAsJsonObject().get("data");
                boolean ok = saveCrate(p, id, data, warnings);
                result(p, ok, ok ? "Caisse « " + id + " » enregistrée" + (warnings.isEmpty() ? "." : " (" + warnings.size() + " avertissement(s)).")
                        : "Enregistrement refusé.", warnings);
                if (ok) Crates.LOG.info("[Crates] Caisse {} modifiee par {}", id, by);
            }
            case "admin_new_crate" -> {
                String id = CrateDef.cleanId(str(a, "id", ""));
                if (id.isEmpty() || Cfg.CRATES.containsKey(id)) {
                    result(p, false, id.isEmpty() ? "Nom invalide (lettres, chiffres, _ et -)." : "La caisse « " + id + " » existe déjà.", warnings);
                    return;
                }
                CrateDef c = new CrateDef();
                c.id = id;
                c.nom = "&f" + id;
                c.recompenses.add(fr.cobblepulse.crates.config.Defaults.item("rare_candy", 1, "commun", 10));
                Cfg.saveCrate(c);
                result(p, true, "Caisse « " + id + " » créée.", warnings);
                Crates.LOG.info("[Crates] Caisse {} creee par {}", id, by);
            }
            case "admin_delete_crate" -> {
                String id = CrateDef.cleanId(str(a, "id", ""));
                int n = Links.unlinkAll(id);
                boolean ok = Cfg.deleteCrate(id);
                syncAllPositions();
                result(p, ok, ok ? "Caisse « " + id + " » supprimée (" + n + " conteneur(s) délié(s)). Fichier renommé en " + id + ".json.supprime."
                        : "Caisse introuvable.", warnings);
                if (ok) Crates.LOG.info("[Crates] Caisse {} supprimee par {}", id, by);
            }
            case "admin_toggle" -> {
                if (!need(p, Perms.ADMIN_TOGGLE)) return;
                String id = str(a, "id", "*");
                if ("*".equals(id)) {
                    Cfg.general.maintenanceGlobale = !Cfg.general.maintenanceGlobale;
                    Cfg.saveGeneral();
                    result(p, true, Cfg.general.maintenanceGlobale ? "Maintenance globale activée." : "Maintenance globale désactivée.", warnings);
                } else {
                    CrateDef c = CrateService.crate(id);
                    if (c == null) return;
                    c.maintenance = !c.maintenance;
                    Cfg.saveCrate(c);
                    result(p, true, "Caisse « " + c.id + " » : maintenance " + (c.maintenance ? "activée." : "désactivée."), warnings);
                }
                Crates.LOG.info("[Crates] Maintenance {} basculee par {}", id, by);
            }
            case "admin_save_general" -> {
                try {
                    GeneralConfig g = Json.CFG.fromJson(a.getAsJsonObject().get("data"), GeneralConfig.class);
                    g.normalize();
                    if (!Perms.admin(p, Perms.ADMIN_TOGGLE)) g.maintenanceGlobale = Cfg.general.maintenanceGlobale;
                    for (GeneralConfig.Rarete r : g.raretes) {
                        if (!r.particule.isBlank() && WorldFx.particle(r.particule) == null) warnings.add("Rareté " + r.id + " : particule inconnue " + r.particule);
                    }
                    Cfg.general = g;
                    Cfg.saveGeneral();
                    WorldFx.clearCaches();
                    result(p, true, "Réglages enregistrés.", warnings);
                } catch (Exception e) {
                    result(p, false, "Format invalide : " + e.getMessage(), warnings);
                }
            }
            case "admin_save_builder" -> {
                try {
                    BuilderConfig b = Json.CFG.fromJson(a.getAsJsonObject().get("data"), BuilderConfig.class);
                    b.normalize();
                    if (!Reg.itemExists(b.monnaie)) warnings.add("Monnaie inconnue : " + b.monnaie);
                    Cfg.builder = b;
                    Cfg.saveBuilder();
                    result(p, true, "Prix du Vœu ADN enregistrés.", warnings);
                } catch (Exception e) {
                    result(p, false, "Format invalide : " + e.getMessage(), warnings);
                }
            }
            case "admin_unlink" -> {
                if (!need(p, Perms.ADMIN_UNSET)) return;
                BlockPos pos = pos(a);
                String dim = str(a, "monde", "");
                boolean ok = pos != null && Links.unlink(dim, pos);
                syncAllPositions();
                result(p, ok, ok ? "Conteneur délié." : "Conteneur introuvable.", warnings);
            }
            case "admin_tp" -> {
                BlockPos pos = pos(a);
                ResourceLocation dim = ResourceLocation.tryParse(str(a, "monde", ""));
                MinecraftServer s = p.getServer();
                ServerLevel level = dim == null || s == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
                if (pos == null || level == null) {
                    toast(p, false, "Monde introuvable.");
                    return;
                }
                p.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 1.8, 180f, 20f);
                toast(p, true, "Téléporté vers la caisse.");
            }
            case "admin_check_pokemon" -> {
                List<String> errors = new ArrayList<>();
                MonSpec spec = Json.CFG.fromJson(a.getAsJsonObject().get("spec"), MonSpec.class);
                Pokemon pk = spec == null ? null : PokemonFactory.create(spec, errors);
                JsonObject r = new JsonObject();
                r.addProperty("ok", pk != null && errors.isEmpty());
                JsonArray e = new JsonArray();
                for (String s : errors) e.add(s);
                r.add("errors", e);
                if (pk != null) {
                    r.addProperty("icon", Views.item(PokemonItem.from(pk), p.registryAccess()));
                    r.addProperty("name", Views.comp(pk.getDisplayName(false), p.registryAccess()));
                }
                send(p, "pokemon_check", r);
            }
            case "admin_reload" -> {
                if (!need(p, Perms.ADMIN_RELOAD)) return;
                reload();
                result(p, true, "Configuration rechargée : " + Cfg.CRATES.size() + " caisses, " + Cfg.POOLS.size() + " pools.", warnings);
            }
            default -> Crates.LOG.debug("[Crates] Action admin inconnue {}", action);
        }
    }

    public static void reload() {
        Cfg.loadAll();
        Links.rebuild();
        WorldFx.clearCaches();
        Rewards.clearCaches();
        syncAllPositions();
    }

    /** Enregistre une caisse modifiee dans l'editeur (avec verifications). */
    public static boolean saveCrate(ServerPlayer p, String id, JsonElement data, List<String> warnings) {
        if (id.isEmpty()) {
            warnings.add("Nom de caisse invalide");
            return false;
        }
        if (data == null || !data.isJsonObject()) {
            warnings.add("Données vides");
            return false;
        }
        CrateDef c;
        try {
            c = Json.CFG.fromJson(data, CrateDef.class);
        } catch (Exception e) {
            warnings.add("Format invalide : " + e.getMessage());
            return false;
        }
        c.id = id;
        c.normalize();
        CrateDef old = Cfg.CRATES.get(id);
        if (old != null && c.maintenance != old.maintenance && p != null && !Perms.admin(p, Perms.ADMIN_TOGGLE)) {
            c.maintenance = old.maintenance;
            warnings.add("Maintenance ignorée : permission " + Perms.ADMIN_TOGGLE + " manquante");
        }
        if (!Reg.itemExists(c.cle)) warnings.add("Clé inconnue : " + c.cle);
        if (!c.particuleAttente.isBlank() && WorldFx.particle(c.particuleAttente) == null) warnings.add("Particule inconnue : " + c.particuleAttente);
        if (!c.particuleOuverture.isBlank() && WorldFx.particle(c.particuleOuverture) == null) warnings.add("Particule inconnue : " + c.particuleOuverture);
        if (c.totalWeight() <= 0) warnings.add("Aucune récompense avec un poids positif : la caisse ne pourra pas être ouverte");
        for (RewardDef r : c.recompenses) checkReward(c, r, warnings);
        Cfg.saveCrate(c);
        return true;
    }

    private static void checkReward(CrateDef c, RewardDef r, List<String> warnings) {
        String where = "Récompense " + r.id + " (" + TextUtil.strip(Rewards.name(r).getString()) + ")";
        boolean known = false;
        for (GeneralConfig.Rarete x : Cfg.general.raretes) if (x.id.equals(r.rarete)) known = true;
        if (!known) warnings.add(where + " : rareté inconnue " + r.rarete);
        switch (r.type()) {
            case "objet" -> {
                if (Rewards.decodeItem(r.objet).isEmpty()) warnings.add(where + " : objet invalide");
            }
            case "pokemon" -> {
                List<String> errs = new ArrayList<>();
                if (PokemonFactory.create(Json.copy(r.pokemon, MonSpec.class), errs) == null || !errs.isEmpty()) {
                    warnings.add(where + " : " + (errs.isEmpty() ? "Pokémon invalide" : String.join(", ", errs)));
                }
            }
            case "pokemon_aleatoire" -> {
                if (!Cfg.POOLS.containsKey(r.aleatoire.pool)) warnings.add(where + " : pool inconnu " + r.aleatoire.pool);
                else if (Pools.species(r.aleatoire.pool).isEmpty()) warnings.add(where + " : pool vide " + r.aleatoire.pool);
            }
            case "commande" -> {
                if (r.commandes.isEmpty()) warnings.add(where + " : aucune commande");
            }
            default -> {
            }
        }
    }
}
