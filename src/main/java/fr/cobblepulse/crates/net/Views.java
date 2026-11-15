package fr.cobblepulse.crates.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.config.CrateDef;
import fr.cobblepulse.crates.config.GeneralConfig;
import fr.cobblepulse.crates.config.Locations;
import fr.cobblepulse.crates.config.PoolDef;
import fr.cobblepulse.crates.config.RewardDef;
import fr.cobblepulse.crates.core.ItemSerial;
import fr.cobblepulse.crates.core.Json;
import fr.cobblepulse.crates.core.Perms;
import fr.cobblepulse.crates.core.TextUtil;
import fr.cobblepulse.crates.crate.CrateService;
import fr.cobblepulse.crates.crate.Links;
import fr.cobblepulse.crates.crate.Pools;
import fr.cobblepulse.crates.crate.Rewards;
import fr.cobblepulse.crates.item.KeyType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Construction des donnees JSON envoyees aux ecrans. */
public final class Views {
    private Views() {}

    public static String comp(Component c, HolderLookup.Provider regs) {
        return Component.Serializer.toJson(c, regs);
    }

    public static String item(ItemStack s, HolderLookup.Provider regs) {
        return ItemSerial.encode(s, regs);
    }

    public static JsonElement json(String s) {
        try {
            return JsonParser.parseString(s == null || s.isEmpty() ? "{}" : s);
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static JsonObject rarity(String id) {
        GeneralConfig.Rarete r = Cfg.general.rarity(id);
        JsonObject o = new JsonObject();
        o.addProperty("id", r.id);
        o.addProperty("name", r.nom);
        o.addProperty("color", r.color());
        return o;
    }

    private static JsonObject reward(CrateDef c, RewardDef r, double total, HolderLookup.Provider regs) {
        JsonObject o = new JsonObject();
        o.addProperty("id", r.id);
        o.addProperty("type", r.type());
        o.addProperty("icon", item(Rewards.icon(r), regs));
        o.addProperty("name", comp(Rewards.coloredName(r, Rewards.name(r)), regs));
        JsonArray lore = new JsonArray();
        for (Component l : Rewards.lore(r)) lore.add(comp(l, regs));
        o.add("lore", lore);
        double chance = total <= 0 ? 0 : Math.max(0, r.poids) / total;
        o.addProperty("chance", chance);
        o.addProperty("pct", Rewards.percent(chance));
        o.add("rarity", rarity(r.rarete));
        o.addProperty("annonce", r.annonce);
        return o;
    }

    /** Recompenses de la caisse (poids > 0), de la plus probable a la plus rare. */
    public static JsonArray rewards(CrateDef c, HolderLookup.Provider regs) {
        double total = c.totalWeight();
        List<RewardDef> list = new ArrayList<>();
        for (RewardDef r : c.recompenses) if (r.poids > 0) list.add(r);
        list.sort(Comparator.comparingDouble((RewardDef r) -> -r.poids));
        JsonArray a = new JsonArray();
        for (RewardDef r : list) a.add(reward(c, r, total, regs));
        return a;
    }

    private static JsonObject crateHeader(ServerPlayer p, CrateDef c) {
        HolderLookup.Provider regs = p.registryAccess();
        JsonObject o = new JsonObject();
        o.addProperty("id", c.id);
        o.addProperty("name", comp(TextUtil.legacy(c.nom), regs));
        o.addProperty("desc", comp(TextUtil.legacy(c.description), regs));
        o.addProperty("color", c.color());
        Item key = CrateService.keyItem(c);
        ItemStack keyStack = key == null ? new ItemStack(Items.BARRIER) : new ItemStack(key);
        JsonObject k = new JsonObject();
        k.addProperty("icon", item(keyStack, regs));
        k.addProperty("name", comp(keyStack.getHoverName(), regs));
        k.addProperty("have", CrateService.countItem(p, key));
        k.addProperty("need", c.clesRequises);
        o.add("key", k);
        o.addProperty("maintenance", CrateService.maintenance(c));
        o.addProperty("globalMaintenance", Cfg.general.maintenanceGlobale);
        o.addProperty("bypass", Perms.bypassMaintenance(p));
        o.addProperty("perm", Perms.canOpen(p, c.id));
        o.addProperty("anim", c.animation);
        o.addProperty("duration", c.dureeAnimation);
        o.addProperty("skip", c.ouvertureRapide);
        o.addProperty("tick", Cfg.general.sonTic);
        o.addProperty("admin", Perms.admin(p, Perms.ADMIN_EDITOR));
        return o;
    }

    /** Ecran d'apercu : grille des recompenses (nom, %), cle requise, bouton d'ouverture. */
    public static JsonObject preview(ServerPlayer p, CrateDef c, BlockPos pos) {
        JsonObject o = crateHeader(p, c);
        o.addProperty("remote", pos == null);
        if (pos != null) {
            JsonArray a = new JsonArray();
            a.add(pos.getX());
            a.add(pos.getY());
            a.add(pos.getZ());
            o.add("pos", a);
        }
        o.addProperty("busy", CrateService.busy(p));
        o.add("rewards", rewards(c, p.registryAccess()));
        return o;
    }

    /** Debut de l'animation : resultat deja tire par le serveur. */
    public static JsonObject spin(ServerPlayer p, CrateDef c, RewardDef r, Rewards.Prepared prep, boolean instant) {
        HolderLookup.Provider regs = p.registryAccess();
        JsonObject o = crateHeader(p, c);
        o.addProperty("winner", r.id);
        o.addProperty("seed", ThreadLocalRandom.current().nextLong());
        o.addProperty("instant", instant);
        JsonObject res = new JsonObject();
        res.addProperty("icon", item(prep.icon, regs));
        GeneralConfig.Rarete rar = Cfg.general.rarity(r.rarete);
        res.addProperty("name", comp(Component.empty().withStyle(s -> s.withColor(rar.color())).append(prep.name), regs));
        res.add("rarity", rarity(r.rarete));
        o.add("result", res);
        o.add("rewards", rewards(c, regs));
        return o;
    }

    /** Fin de l'ouverture : cles restantes. */
    public static JsonObject done(ServerPlayer p, CrateDef c) {
        JsonObject o = new JsonObject();
        o.addProperty("id", c.id);
        o.addProperty("have", CrateService.countItem(p, CrateService.keyItem(c)));
        o.addProperty("need", c.clesRequises);
        return o;
    }

    // ------------------------------------------------------------------ Editeur

    public static JsonObject admin(ServerPlayer p) {
        JsonObject o = new JsonObject();
        JsonArray crates = new JsonArray();
        for (CrateDef c : Cfg.CRATES.values()) {
            JsonObject x = Json.CFG.toJsonTree(c).getAsJsonObject();
            x.addProperty("_id", c.id);
            JsonArray locs = new JsonArray();
            for (Locations.Loc l : Links.forCrate(c.id)) locs.add(Json.CFG.toJsonTree(l));
            x.add("_links", locs);
            crates.add(x);
        }
        o.add("crates", crates);
        JsonArray pools = new JsonArray();
        for (PoolDef pd : Cfg.POOLS.values()) {
            JsonObject x = new JsonObject();
            x.addProperty("id", pd.id);
            x.addProperty("name", TextUtil.strip(pd.nom));
            x.addProperty("count", Pools.species(pd.id).size());
            pools.add(x);
        }
        o.add("pools", pools);
        o.add("general", Json.CFG.toJsonTree(Cfg.general));
        o.add("builder", Json.CFG.toJsonTree(Cfg.builder));
        JsonArray keys = new JsonArray();
        for (KeyType k : KeyType.values()) keys.add(k.itemId());
        keys.add("cobblepulse_crates:voeu_adn");
        o.add("keys", keys);
        JsonObject perms = new JsonObject();
        perms.addProperty("editor", Perms.admin(p, Perms.ADMIN_EDITOR));
        perms.addProperty("toggle", Perms.admin(p, Perms.ADMIN_TOGGLE));
        perms.addProperty("unset", Perms.admin(p, Perms.ADMIN_UNSET));
        o.add("perms", perms);
        o.addProperty("dim", Links.dim(p.level()));
        return o;
    }
}
