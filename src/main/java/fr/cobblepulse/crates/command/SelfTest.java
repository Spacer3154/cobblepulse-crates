package fr.cobblepulse.crates.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.builder.BuilderService;
import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.config.CrateDef;
import fr.cobblepulse.crates.config.Defaults;
import fr.cobblepulse.crates.config.Locations;
import fr.cobblepulse.crates.config.RewardDef;
import fr.cobblepulse.crates.core.Json;
import fr.cobblepulse.crates.core.Players;
import fr.cobblepulse.crates.core.Scheduler;
import fr.cobblepulse.crates.core.TextUtil;
import fr.cobblepulse.crates.crate.CrateService;
import fr.cobblepulse.crates.crate.Links;
import fr.cobblepulse.crates.crate.Pools;
import fr.cobblepulse.crates.crate.Rewards;
import fr.cobblepulse.crates.crate.WorldFx;
import fr.cobblepulse.crates.integration.LuckPermsBridge;
import fr.cobblepulse.crates.item.KeyType;
import fr.cobblepulse.crates.item.ModItems;
import fr.cobblepulse.crates.net.Net;
import fr.cobblepulse.crates.pokemon.AbilityTools;
import fr.cobblepulse.crates.pokemon.FormTools;
import fr.cobblepulse.crates.pokemon.SpeciesLookup;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Auto-test (CI) : vrais joueurs factices, vraies caisses, vraies recompenses, Voeu ADN, LuckPerms, persistance. */
public final class SelfTest {
    private static final List<String> REPORT = new ArrayList<>();
    private static int pass;
    private static int fail;
    private static BlockPos chest;
    private static BlockPos barrel;

    private SelfTest() {}

    public static boolean enabled() {
        return "true".equals(System.getProperty("cobblepulse_crates.selftest"));
    }

    private static void check(String name, boolean ok, String detail) {
        String line = (ok ? "PASS " : "FAIL ") + name + (detail == null || detail.isEmpty() ? "" : " - " + detail);
        REPORT.add(line);
        if (ok) pass++;
        else fail++;
        Crates.LOG.info("[Crates][selftest] {}", line);
    }

    private static void info(String line) {
        REPORT.add("INFO " + line);
        Crates.LOG.info("[Crates][selftest] INFO {}", line);
    }

    private static FakePlayer fake(ServerLevel level, String name) {
        FakePlayer fp = FakePlayer.get(level, new GameProfile(UUID.nameUUIDFromBytes(("cpc-" + name).getBytes(StandardCharsets.UTF_8)), name));
        Players.registerTest(fp);
        registerInPlayerList(level.getServer(), fp);
        return fp;
    }

    /** Cobblemon resout les joueurs par UUID : le joueur factice est ajoute a l'index par UUID de la liste des joueurs. */
    @SuppressWarnings("unchecked")
    private static void registerInPlayerList(MinecraftServer server, ServerPlayer p) {
        try {
            for (java.lang.reflect.Field f : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                if (!(f.getGenericType() instanceof java.lang.reflect.ParameterizedType pt)) continue;
                java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                if (args.length == 2 && args[0] == UUID.class && args[1] == ServerPlayer.class) {
                    f.setAccessible(true);
                    ((Map<UUID, ServerPlayer>) f.get(server.getPlayerList())).put(p.getUUID(), p);
                    return;
                }
            }
        } catch (Throwable t) {
            Crates.LOG.warn("[Crates][selftest] index des joueurs : {}", t.toString());
        }
    }

    private static int count(ServerPlayer p, Item item) {
        return CrateService.countItem(p, item);
    }

    private static void clearInv(ServerPlayer p) {
        p.getInventory().clearContent();
    }

    private static int partyCount(ServerPlayer p, String species) {
        int n = 0;
        for (Pokemon pk : Cobblemon.INSTANCE.getStorage().getParty(p)) if (pk != null && pk.getSpecies().getName().equalsIgnoreCase(species)) n++;
        return n;
    }

    private static Pokemon lastPartyMon(ServerPlayer p) {
        Pokemon last = null;
        for (Pokemon pk : Cobblemon.INSTANCE.getStorage().getParty(p)) if (pk != null) last = pk;
        return last;
    }

    private static void clearParty(ServerPlayer p) {
        List<Pokemon> all = new ArrayList<>();
        for (Pokemon pk : Cobblemon.INSTANCE.getStorage().getParty(p)) if (pk != null) all.add(pk);
        for (Pokemon pk : all) Cobblemon.INSTANCE.getStorage().getParty(p).remove(pk);
    }

    private static int score(MinecraftServer s, String holder) {
        var obj = s.getScoreboard().getObjective("cpc_test");
        var info = obj == null ? null : s.getScoreboard().getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(holder), obj);
        return info == null ? 0 : info.value();
    }

    private static void cmd(MinecraftServer s, String c) {
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), c);
    }

    private static CrateDef testCrate(String id, RewardDef r, String anim) {
        CrateDef c = new CrateDef();
        c.id = id;
        c.nom = "&dCaisse de test";
        c.cle = KeyType.TEMPS.itemId();
        c.animation = anim;
        c.dureeAnimation = 1.0;
        c.particuleOuverture = "minecraft:end_rod";
        c.recompenses = new ArrayList<>(List.of(r));
        Cfg.saveCrate(c);
        return c;
    }

    private static Item key(KeyType k) {
        return ModItems.KEYS.get(k);
    }

    // ------------------------------------------------------------------ Lancement

    public static int run(CommandSourceStack src) {
        REPORT.clear();
        pass = 0;
        fail = 0;
        MinecraftServer s = src.getServer();
        ServerLevel level = s.overworld();
        try {
            staticChecks(s);
            worldChecks(s, level);
        } catch (Throwable t) {
            check("exception", false, t.toString());
            Crates.LOG.error("[Crates][selftest] exception", t);
        }
        return 1;
    }

    private static void staticChecks(MinecraftServer s) {
        check("caisses chargees", Cfg.CRATES.size() >= 10, Cfg.CRATES.size() + " caisses");
        check("pools charges", Cfg.POOLS.keySet().containsAll(List.of("all", "legendary", "ultra_beasts", "fakemons")), String.join(",", Cfg.POOLS.keySet()));
        int all = Pools.species("all").size();
        int leg = Pools.species("legendary").size();
        int ub = Pools.species("ultra_beasts").size();
        int fk = Pools.species("fakemons").size();
        check("pool all", all > 300, all + " especes");
        check("pool legendary", leg >= 10, leg + " especes implementees");
        check("pool ultra_beasts", ub >= 1, ub + " especes implementees");
        info("pool fakemons : " + fk + " especes (0 attendu sans datapack)");
        boolean legOk = true;
        for (Species sp : Pools.species("legendary")) {
            if (!sp.getLabels().contains("legendary") && !sp.getLabels().contains("mythical")) legOk = false;
        }
        check("pool legendary = legendaires/fabuleux", legOk, null);
        check("items enregistres", ModItems.KEYS.size() == 10 && ModItems.VOEU_ADN != null, null);
        for (KeyType k : KeyType.values()) {
            CrateDef c = Cfg.CRATES.get(k.shortName);
            check("caisse " + k.shortName + " -> cle", c != null && c.cle.equals(k.itemId()) && CrateService.keyItem(c) == key(k), c == null ? "absente" : c.cle);
        }
        check("givekey nom court", Commands.keyItem("coeur_dragon") == key(KeyType.COEUR_DRAGON) && Commands.keyItem("givre-etoile") == key(KeyType.GIVRE_ETOILE), null);
        check("givekey nom de caisse", Commands.keyItem("obsidienne") == key(KeyType.OBSIDIENNE), null);
        // Toutes les recompenses par defaut doivent etre valides (objets, especes, pools)
        int ok = 0;
        List<String> bad = new ArrayList<>();
        for (CrateDef c : Defaults.crates()) {
            for (RewardDef r : c.recompenses) {
                List<String> errs = new ArrayList<>();
                Rewards.Prepared p = Rewards.prepare(Json.copyCfg(r, RewardDef.class), errs);
                if (p == null) bad.add(c.id + "/" + r.id + " " + errs);
                else ok++;
            }
        }
        check("recompenses par defaut valides", bad.isEmpty(), ok + " ok" + (bad.isEmpty() ? "" : " ; invalides : " + bad));
        // Pourcentages
        CrateDef o = Cfg.CRATES.get("obsidienne");
        double sum = 0;
        for (var e : Net.LAST_SENT.entrySet()) e.getKey();
        for (RewardDef r : o.recompenses) sum += r.poids / o.totalWeight();
        check("somme des chances = 100 %", Math.abs(sum - 1.0) < 1e-9, String.valueOf(sum));
        check("format pourcentage", Rewards.percent(0.125).equals("12,5 %") && Rewards.percent(0.01).equals("1 %"), Rewards.percent(0.125) + " / " + Rewards.percent(0.01));
        // Formes autorisees
        for (String sp : List.of("vulpix", "rotom", "giratina", "arceus", "charizard", "meowth", "deoxys")) {
            Species x = SpeciesLookup.resolve(sp);
            if (x == null) continue;
            List<String> names = new ArrayList<>();
            for (FormData f : FormTools.allowed(x)) names.add(f.getName());
            List<String> allNames = new ArrayList<>();
            for (FormData f : FormTools.forms(x)) allNames.add(f.getName());
            info("formes " + sp + " : autorisees " + names + " / toutes " + allNames);
        }
        Species giratina = SpeciesLookup.resolve("giratina");
        if (giratina != null) {
            boolean originAllowed = false;
            for (FormData f : FormTools.allowed(giratina)) if (f.getName().toLowerCase().contains("origin")) originAllowed = true;
            check("forme a objet exclue (Giratina Originel)", !originAllowed, null);
        }
    }

    private static void worldChecks(MinecraftServer s, ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        chest = new BlockPos(spawn.getX() + 3, 120, spawn.getZ() + 3);
        barrel = chest.east(3);
        level.getChunk(chest);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), 3);
        level.setBlock(chest.below(), Blocks.STONE.defaultBlockState(), 3);
        check("conteneur reconnu", Commands.isContainer(level, chest) && Commands.isContainer(level, barrel), null);
        Links.link(level, chest, "obsidienne");
        Links.link(level, barrel, "selftest_b");
        check("liaison", "obsidienne".equals(Links.crateAt(level, chest)), Links.crateAt(level, chest));
        check("liaison multi-mondes (autre dimension)", Links.crateAt("minecraft:the_nether", chest) == null, null);

        FakePlayer p = fake(level, "CrateTester");
        p.setPos(chest.getX() + 0.5, chest.getY(), chest.getZ() + 2.5);
        clearInv(p);
        clearParty(p);

        // Apercu
        CrateService.click(p, level, chest, "obsidienne");
        String prev = Net.LAST_SENT.get("crate_preview");
        JsonObject pv = prev == null ? null : JsonParser.parseString(prev).getAsJsonObject();
        check("apercu", pv != null && pv.getAsJsonArray("rewards").size() == Cfg.CRATES.get("obsidienne").recompenses.size()
                && pv.getAsJsonArray("rewards").get(0).getAsJsonObject().has("pct"), pv == null ? "rien envoye" : pv.getAsJsonArray("rewards").size() + " lots");

        // Sans cle
        String r = CrateService.open(p, "obsidienne", chest);
        check("ouverture sans cle refusee", "caisse.no_key".equals(r), r);
        // Mauvais conteneur / a distance
        r = CrateService.open(p, "obsidienne", chest.north(5));
        check("ouverture a distance refusee", "caisse.remote".equals(r), r);
        // Trop loin
        p.getInventory().add(new ItemStack(key(KeyType.OBSIDIENNE), 5));
        p.setPos(chest.getX() + 30, chest.getY(), chest.getZ());
        r = CrateService.open(p, "obsidienne", chest);
        check("ouverture trop loin refusee", "caisse.too_far".equals(r) && count(p, key(KeyType.OBSIDIENNE)) == 5, r);
        p.setPos(chest.getX() + 0.5, chest.getY(), chest.getZ() + 2.5);
        // Maintenance de la caisse
        CrateDef obs = Cfg.CRATES.get("obsidienne");
        obs.maintenance = true;
        r = CrateService.open(p, "obsidienne", chest);
        check("maintenance caisse : cle conservee", "caisse.maintenance".equals(r) && count(p, key(KeyType.OBSIDIENNE)) == 5, r);
        obs.maintenance = false;
        Cfg.general.maintenanceGlobale = true;
        r = CrateService.open(p, "obsidienne", chest);
        check("maintenance globale : cle conservee", "caisse.maintenance_global".equals(r) && count(p, key(KeyType.OBSIDIENNE)) == 5, r);
        // Contournement LuckPerms
        boolean lp = LuckPermsBridge.present();
        if (lp) {
            LuckPermsBridge.setForTest(p.getUUID(), "cobblepulsecrates.bypass.maintenance", true);
            r = CrateService.open(p, "obsidienne", chest);
            check("bypass.maintenance (LuckPerms)", r == null && count(p, key(KeyType.OBSIDIENNE)) == 4, r);
            LuckPermsBridge.setForTest(p.getUUID(), "cobblepulsecrates.bypass.maintenance", null);
        } else {
            info("LuckPerms absent : contournement non teste");
        }
        Cfg.general.maintenanceGlobale = false;
        Cfg.saveGeneral();
        // Ouverture normale
        String before = CrateService.LAST_REWARD.get(p.getUUID());
        int keys = count(p, key(KeyType.OBSIDIENNE));
        if (CrateService.busy(p)) {
            // ouverture du test bypass en cours : on la termine
            CrateService.skip(p);
        }
        keys = count(p, key(KeyType.OBSIDIENNE));
        r = CrateService.open(p, "obsidienne", chest);
        check("ouverture : cle consommee", r == null && count(p, key(KeyType.OBSIDIENNE)) == keys - 1, r + " cles " + count(p, key(KeyType.OBSIDIENNE)));
        check("animation envoyee", Net.LAST_SENT.containsKey("crate_spin"), null);
        r = CrateService.open(p, "obsidienne", chest);
        check("une ouverture a la fois", "caisse.busy".equals(r), r);
        int ticks = (int) Math.round(obs.dureeAnimation * 20) + 10;
        Scheduler.later(ticks, () -> {
            String after = CrateService.LAST_REWARD.get(p.getUUID());
            check("recompense remise a la fin de l'animation", after != null && !after.equals(before) && !CrateService.busy(p), after);
            check("hologramme affiche", WorldFx.liveHolograms() >= 2, WorldFx.liveHolograms() + " entites");
            Scheduler.later((int) Math.round(Cfg.general.hologramme.dureeSecondes * 20) + 10, () ->
                    check("hologramme retire", WorldFx.liveHolograms() == 0, WorldFx.liveHolograms() + " entites"));
        });
        // Permission refusee (LuckPerms)
        if (lp) {
            FakePlayer q = fake(level, "CrateDenied");
            q.setPos(chest.getX() + 0.5, chest.getY(), chest.getZ() - 1.5);
            q.getInventory().add(new ItemStack(key(KeyType.OBSIDIENNE), 1));
            LuckPermsBridge.setForTest(q.getUUID(), "cobblepulsecrates.crate.open.obsidienne", false);
            String rq = CrateService.open(q, "obsidienne", chest);
            check("crate.open.<nom> refuse (LuckPerms)", "caisse.no_open_perm".equals(rq) && count(q, key(KeyType.OBSIDIENNE)) == 1, rq);
            LuckPermsBridge.setForTest(q.getUUID(), "cobblepulsecrates.crate.open.obsidienne", null);
        }
        Scheduler.later(ticks + 5, () -> rewardTypes(s, level));
    }

    /** Chaque type de recompense, sur une caisse de test liee au tonneau (animation courte, passer, instantanee). */
    private static void rewardTypes(MinecraftServer s, ServerLevel level) {
        try {
            FakePlayer p = fake(level, "CrateTypes");
            p.setPos(barrel.getX() + 0.5, barrel.getY(), barrel.getZ() + 2.5);
            clearInv(p);
            clearParty(p);
            Item k = key(KeyType.TEMPS);
            p.getInventory().add(new ItemStack(k, 10));
            // Objet + passer l'animation
            testCrate("selftest_b", Defaults.item("cobblemon:rare_candy", 70, "rare", 1), "roulette");
            String r = CrateService.open(p, "selftest_b", barrel);
            boolean lidOpen = level.getBlockState(barrel).getValue(BlockStateProperties.OPEN);
            CrateService.skip(p);
            check("type objet (70, passer)", r == null && count(p, BuiltinItem.RARE_CANDY.get()) == 70, r + " -> " + count(p, BuiltinItem.RARE_CANDY.get()));
            check("couvercle du tonneau ouvert pendant l'animation", lidOpen, null);
            // Commande
            cmd(s, "scoreboard objectives add cpc_test dummy");
            RewardDef c = Defaults.command("&eTest", "minecraft:paper", "commun", 1, "scoreboard players add %player% cpc_test 3");
            testCrate("selftest_b", c, "instantanee");
            r = CrateService.open(p, "selftest_b", barrel);
            check("type commande (instantanee)", r == null && score(s, "CrateTypes") == 3, r + " score " + score(s, "CrateTypes"));
            // Pokemon precis
            RewardDef m = Defaults.mon("eevee", 12, true, 3, "epique", 1);
            m.pokemon.nature = "adamant";
            testCrate("selftest_b", m, "instantanee");
            r = CrateService.open(p, "selftest_b", barrel);
            Pokemon e = lastPartyMon(p);
            check("type pokemon", r == null && e != null && e.getSpecies().getName().equalsIgnoreCase("eevee") && e.getShiny() && e.getLevel() == 12
                    && e.getIvs().get(Stats.HP) == 31 && e.getNature().getName().getPath().equals("adamant"), e == null ? r : e.getSpecies().getName());
            // Pokemon aleatoire : legendaire, 100 % chromatique, 6 IV parfaits
            RewardDef a = Defaults.random("legendary", 50, 50, 100, 6, "base", "cobblemon:master_ball", "", "legendaire", 1);
            testCrate("selftest_b", a, "instantanee");
            r = CrateService.open(p, "selftest_b", barrel);
            Pokemon l = lastPartyMon(p);
            boolean ivs = l != null;
            if (l != null) for (Stats st : new Stats[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED}) {
                if (l.getIvs().get(st) != 31) ivs = false;
            }
            check("type pokemon aleatoire (legendaire, shiny 100 %, 6 IV)", r == null && l != null && l.getShiny() && l.getLevel() == 50 && ivs
                    && (l.getSpecies().getLabels().contains("legendary") || l.getSpecies().getLabels().contains("mythical")), l == null ? r : l.getSpecies().getName());
            // Pokemon aleatoire : formes aleatoires (20 tirages, au moins une forme non standard attendue si possible)
            int nonStd = 0;
            List<String> errs = new ArrayList<>();
            RewardDef.RandomMon rm = new RewardDef.RandomMon();
            rm.pool = "all";
            rm.formes = "aleatoire";
            rm.ivsParfaits = 2;
            for (int i = 0; i < 60; i++) {
                Pokemon x = Rewards.rollPokemon(rm, errs);
                if (x != null && !FormTools.isStandard(x.getSpecies(), x.getForm())) nonStd++;
            }
            check("pokemon aleatoire avec formes", errs.isEmpty(), nonStd + "/60 formes non standard" + (errs.isEmpty() ? "" : " " + errs));
            // Pool vide : recompense ignoree et cle non consommee
            RewardDef empty = Defaults.random("fakemons", 5, 5, 0, 0, "base", "", "", "commun", 1);
            CrateDef ce = testCrate("selftest_b", empty, "instantanee");
            if (Pools.species("fakemons").isEmpty()) {
                int before = count(p, k);
                r = CrateService.open(p, "selftest_b", barrel);
                check("pool vide : cle conservee", "caisse.error".equals(r) && count(p, k) == before, r);
            }
            // Repli : une recompense invalide est ignoree au profit d'une valide
            ce.recompenses.add(Defaults.item("minecraft:diamond", 1, "rare", 1));
            Cfg.saveCrate(ce);
            int diamonds = count(p, Items.DIAMOND);
            for (int i = 0; i < 3; i++) CrateService.open(p, "selftest_b", barrel);
            check("recompense invalide ignoree (repli)", count(p, Items.DIAMOND) == diamonds + 3, (count(p, Items.DIAMOND) - diamonds) + " diamants");
            // Deconnexion pendant l'animation
            testCrate("selftest_b", Defaults.item("minecraft:emerald", 2, "commun", 1), "roue");
            r = CrateService.open(p, "selftest_b", barrel);
            CrateService.onDisconnect(p);
            check("deconnexion : recompense remise", r == null && count(p, Items.EMERALD) == 2, r);
            // Editeur : enregistrement aller-retour
            JsonObject data = Json.CFG.toJsonTree(Cfg.CRATES.get("obsidienne")).getAsJsonObject();
            data.addProperty("nom", "&5Obsidienne (test)");
            List<String> warn = new ArrayList<>();
            boolean saved = Net.saveCrate(null, "obsidienne", data, warn);
            Net.reload();
            check("editeur : enregistrement + rechargement", saved && "&5Obsidienne (test)".equals(Cfg.CRATES.get("obsidienne").nom) && warn.isEmpty(),
                    String.join(" | ", warn));
            data.addProperty("nom", Defaults.crates().get(0).nom);
            Net.saveCrate(null, "obsidienne", data, warn);
            JsonObject badData = Json.CFG.toJsonTree(Cfg.CRATES.get("obsidienne")).getAsJsonObject().deepCopy();
            badData.addProperty("cle", "minecraft:nope");
            JsonArray rw = badData.getAsJsonArray("recompenses");
            JsonObject badReward = rw.get(0).getAsJsonObject().deepCopy();
            badReward.add("objet", JsonParser.parseString("{\"id\":\"cobblemon:not_an_item\",\"count\":1}"));
            rw.add(badReward);
            List<String> w2 = new ArrayList<>();
            Net.saveCrate(null, "selftest_c", badData, w2);
            check("editeur : avertissements", w2.size() >= 2, String.join(" | ", w2));
            Cfg.deleteCrate("selftest_c");
            Cfg.deleteCrate("selftest_b");
            check("suppression de caisse", !Cfg.CRATES.containsKey("selftest_b") && !Files.exists(Cfg.cratesDir().resolve("selftest_b.json")), null);
            Links.unlink(Links.dim(level), barrel);
        } catch (Throwable t) {
            check("types de recompenses", false, t.toString());
            Crates.LOG.error("[Crates][selftest] exception", t);
        }
        builder(s, level);
    }

    // ------------------------------------------------------------------ Voeu ADN

    private static JsonObject evs(int hp, int atk, int spe) {
        JsonObject o = new JsonObject();
        o.addProperty("hp", hp);
        o.addProperty("atk", atk);
        o.addProperty("def", 0);
        o.addProperty("spa", 0);
        o.addProperty("spd", 0);
        o.addProperty("spe", spe);
        return o;
    }

    private static void builder(MinecraftServer s, ServerLevel level) {
        try {
            FakePlayer p = fake(level, "DnaTester");
            clearInv(p);
            clearParty(p);
            PokemonProperties pp = PokemonProperties.Companion.parse("vulpix level=20 shiny=no nature=hardy");
            Pokemon v = pp.create();
            for (Stats st : new Stats[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED}) v.setIV(st, 10);
            Cobblemon.INSTANCE.getStorage().getParty(p).add(v);
            String id = v.getUuid().toString();
            Item wish = ModItems.VOEU_ADN;
            var res = BuilderService.apply(p, id, "shiny", null);
            check("voeu : refus sans Voeu ADN", !res.ok() && !v.getShiny(), res.message());
            p.getInventory().add(new ItemStack(wish, 20));
            res = BuilderService.apply(p, id, "shiny", null);
            check("voeu : chromatique (5)", res.ok() && v.getShiny() && count(p, wish) == 15, res.message() + " reste " + count(p, wish));
            res = BuilderService.apply(p, id, "nature", new com.google.gson.JsonPrimitive("adamant"));
            check("voeu : nature (1)", res.ok() && v.getEffectiveNature().getName().getPath().equals("adamant") && count(p, wish) == 14, res.message());
            res = BuilderService.apply(p, id, "nature", new com.google.gson.JsonPrimitive("adamant"));
            check("voeu : meme nature refusee", !res.ok() && count(p, wish) == 14, res.message());
            JsonArray st = new JsonArray();
            st.add("atk");
            st.add("spe");
            res = BuilderService.apply(p, id, "ivs", st);
            check("voeu : IV a 31 (2 stats = 2)", res.ok() && v.getIvs().get(Stats.ATTACK) == 31 && v.getIvs().get(Stats.SPEED) == 31
                    && v.getIvs().get(Stats.HP) == 10 && count(p, wish) == 12, res.message());
            res = BuilderService.apply(p, id, "ivs", st);
            check("voeu : IV deja a 31 refuses", !res.ok() && count(p, wish) == 12, res.message());
            res = BuilderService.apply(p, id, "evs", evs(252, 252, 252));
            check("voeu : EV > 510 refuses", !res.ok() && count(p, wish) == 12, res.message());
            res = BuilderService.apply(p, id, "evs", evs(4, 252, 252));
            check("voeu : EV (1 par session)", res.ok() && v.getEvs().get(Stats.ATTACK) == 252 && v.getEvs().get(Stats.HP) == 4 && count(p, wish) == 11, res.message());
            String abBefore = AbilityTools.current(v);
            res = BuilderService.apply(p, id, "hidden", null);
            check("voeu : talent cache (3)", res.ok() && AbilityTools.hasHidden(v) && count(p, wish) == 8, abBefore + " -> " + AbilityTools.current(v));
            res = BuilderService.apply(p, id, "hidden", null);
            check("voeu : talent cache deja actif refuse", !res.ok(), res.message());
            FormData alola = FormTools.byName(v.getSpecies(), "alola");
            res = BuilderService.apply(p, id, "form", new com.google.gson.JsonPrimitive(alola == null ? "alola" : alola.getName()));
            check("voeu : forme Alola (2)", res.ok() && alola != null && FormTools.same(v.getForm(), alola) && v.getShiny() && count(p, wish) == 6,
                    res.message() + " forme " + v.getForm().getName() + " aspects " + v.getAspects());
            check("voeu : talent cache conserve apres changement de forme", AbilityTools.hasHidden(v), AbilityTools.current(v));
            info("vulpix apres forme : talent " + AbilityTools.current(v) + ", normaux " + AbilityTools.normal(v).stream().map(x -> x.getTemplate().getName()).toList());
            String other = null;
            for (var pa : AbilityTools.normal(v)) if (!pa.getTemplate().getName().equals(AbilityTools.current(v))) other = pa.getTemplate().getName();
            if (other != null) {
                res = BuilderService.apply(p, id, "ability", new com.google.gson.JsonPrimitive(other));
                check("voeu : talent normal (1)", res.ok() && AbilityTools.current(v).equals(other) && count(p, wish) == 5, res.message());
            }
            if (LuckPermsBridge.present()) {
                LuckPermsBridge.setForTest(p.getUUID(), "cobblepulsecrates.builder.bypass", true);
                int w = count(p, wish);
                res = BuilderService.apply(p, id, "form", new com.google.gson.JsonPrimitive(v.getSpecies().getStandardForm().getName()));
                check("voeu : builder.bypass gratuit + retour forme normale", res.ok() && count(p, wish) == w && FormTools.isStandard(v.getSpecies(), v.getForm()),
                        res.message() + " forme " + v.getForm().getName() + " aspects " + v.getAspects());
                LuckPermsBridge.setForTest(p.getUUID(), "cobblepulsecrates.builder.bypass", null);
                LuckPermsBridge.setForTest(p.getUUID(), "cobblepulsecrates.builder.use", false);
                res = BuilderService.apply(p, id, "shiny", null);
                check("voeu : builder.use refuse", !res.ok() && v.getShiny(), res.message());
                LuckPermsBridge.setForTest(p.getUUID(), "cobblepulsecrates.builder.use", null);
            }
            // Forme interdite
            Species chz = SpeciesLookup.resolve("charizard");
            if (chz != null) {
                Pokemon c = PokemonProperties.Companion.parse("charizard level=50").create();
                Cobblemon.INSTANCE.getStorage().getParty(p).add(c);
                boolean megaFound = false;
                for (FormData f : FormTools.forms(chz)) if (f.getName().toLowerCase().contains("mega") || f.getName().toLowerCase().contains("gmax")) megaFound = true;
                res = BuilderService.apply(p, c.getUuid().toString(), "form", new com.google.gson.JsonPrimitive("mega-x"));
                check("voeu : Mega refusee", !res.ok(), (megaFound ? "forme mega presente" : "pas de forme mega") + " : " + res.message());
            }
            // Pokemon du PC
            Pokemon pcMon = PokemonProperties.Companion.parse("ponyta level=15 nature=hardy").create();
            Cobblemon.INSTANCE.getStorage().getPC(p).add(pcMon);
            p.getInventory().add(new ItemStack(wish, 3));
            res = BuilderService.apply(p, pcMon.getUuid().toString(), "nature", new com.google.gson.JsonPrimitive("timid"));
            check("voeu : Pokemon du PC", res.ok() && pcMon.getEffectiveNature().getName().getPath().equals("timid"), res.message());
            // Vues
            JsonObject root = BuilderService.root(p);
            check("voeu : vue du menu", root.getAsJsonArray("party").size() == 6 && root.getAsJsonArray("natures").size() == 25, null);
            JsonObject box = BuilderService.box(p, 0);
            check("voeu : vue du PC", box.getAsJsonArray("mons").size() == 30, null);
            // Monnaie : /lootbox givewish via la fonction de don
            check("voeu : journal", !Cfg.general.journal || Files.exists(Cfg.root().resolve("journal")), null);
        } catch (Throwable t) {
            check("voeu adn", false, t.toString());
            Crates.LOG.error("[Crates][selftest] exception", t);
        }
        Crates.LOG.info("[Crates][selftest] SELFTEST SUMMARY pass={} fail={}", pass, fail);
    }

    // ------------------------------------------------------------------ Persistance et rapport

    public static int persisted(CommandSourceStack src) {
        REPORT.clear();
        pass = 0;
        fail = 0;
        ServerLevel level = src.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos c = new BlockPos(spawn.getX() + 3, 120, spawn.getZ() + 3);
        check("persistance : liaison conservee", "obsidienne".equals(Links.crateAt(level, c)), Links.crateAt(level, c));
        check("persistance : caisse supprimee absente", !Cfg.CRATES.containsKey("selftest_b"), null);
        check("persistance : emplacements.json", Files.exists(Cfg.root().resolve("emplacements.json")), null);
        int holos = 0;
        for (var e : level.getAllEntities()) if (e.getTags().contains(WorldFx.HOLO_TAG)) holos++;
        check("persistance : aucun hologramme orphelin", holos == 0, holos + " entites");
        try {
            String log = Files.readString(Files.list(Cfg.root().resolve("journal")).filter(f -> f.getFileName().toString().startsWith("ouvertures")).findFirst().orElseThrow());
            check("journal des ouvertures", log.contains("CrateTester") && log.contains("obsidienne"), log.lines().count() + " lignes");
        } catch (Exception e) {
            check("journal des ouvertures", false, e.toString());
        }
        Links.unlink(Links.dim(level), c);
        check("persistance : deliaison", Links.crateAt(level, c) == null, null);
        try {
            UUID dna = UUID.nameUUIDFromBytes("cpc-DnaTester".getBytes(StandardCharsets.UTF_8));
            Pokemon v = null;
            for (Pokemon pk : Cobblemon.INSTANCE.getStorage().getParty(dna, src.getServer().registryAccess())) {
                if (pk != null && pk.getSpecies().getName().equalsIgnoreCase("vulpix")) v = pk;
            }
            check("persistance : Pokemon remodele (chromatique, nature, IV, EV)", v != null && v.getShiny()
                    && v.getEffectiveNature().getName().getPath().equals("adamant") && v.getIvs().get(Stats.ATTACK) == 31
                    && v.getEvs().get(Stats.SPEED) == 252, v == null ? "introuvable" : v.getForm().getName() + " " + AbilityTools.current(v));
        } catch (Throwable t) {
            check("persistance : Pokemon remodele", false, t.toString());
        }
        Crates.LOG.info("[Crates][selftest] SELFTEST SUMMARY pass={} fail={}", pass, fail);
        return 1;
    }

    public static int report(CommandSourceStack src) {
        for (String l : REPORT) src.sendSystemMessage(TextUtil.legacy((l.startsWith("PASS") ? "&a" : l.startsWith("FAIL") ? "&c" : "&7") + l));
        Crates.LOG.info("[Crates][selftest] SELFTEST SUMMARY pass={} fail={}", pass, fail);
        return 1;
    }

    // ------------------------------------------------------------------ Test de bout en bout (vrai client)

    private static BlockPos liveChest;
    private static BlockPos liveBarrel;

    /** Prepare deux caisses devant le point d'apparition pour le client de test (ci/live.py). */
    public static int live(CommandSourceStack src) {
        ServerLevel level = src.getServer().overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX();
        int z = spawn.getZ();
        level.getChunk(new BlockPos(x, 64, z));
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + 2, z + 3);
        for (int dx = -2; dx <= 6; dx++) {
            for (int dz = -1; dz <= 7; dz++) {
                level.setBlock(new BlockPos(x + dx, y - 1, z + dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3);
                for (int dy = 0; dy < 4; dy++) level.setBlock(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        liveChest = new BlockPos(x + 1, y, z + 4);
        liveBarrel = new BlockPos(x + 4, y, z + 4);
        level.setBlock(liveChest, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
        level.setBlock(liveBarrel, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, net.minecraft.core.Direction.UP), 3);
        Links.link(level, liveChest, "obsidienne");
        Links.link(level, liveBarrel, "songes");
        Net.syncAllPositions();
        Crates.LOG.info("[Crates][selftest] live : caisses en {} et {}", liveChest.toShortString(), liveBarrel.toShortString());
        return 1;
    }

    /** Joueur de test connecte : place devant les caisses, cles, Voeux ADN et equipe. */
    public static void onJoin(ServerPlayer p) {
        if (liveChest == null || !"CratesTest".equals(p.getGameProfile().getName())) return;
        ServerLevel level = p.serverLevel();
        // Operateur (le pseudo hors ligne n'a pas le meme UUID que celui resolu par /op)
        p.getServer().getPlayerList().op(p.getGameProfile());
        p.teleportTo(level, liveChest.getX() + 2.0, liveChest.getY(), liveChest.getZ() - 3.0, 0f, 20f);
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(key(KeyType.OBSIDIENNE), 5));
        p.getInventory().add(new ItemStack(key(KeyType.SONGES), 5));
        p.getInventory().add(new ItemStack(ModItems.VOEU_ADN, 20));
        clearParty(p);
        for (String spec : List.of("vulpix level=20 nature=hardy", "eevee level=18 shiny=yes", "dratini level=30", "rotom level=25")) {
            try {
                Cobblemon.INSTANCE.getStorage().getParty(p).add(PokemonProperties.Companion.parse(spec).create());
            } catch (Throwable t) {
                Crates.LOG.warn("[Crates][selftest] live : {} : {}", spec, t.toString());
            }
        }
        Crates.LOG.info("[Crates][selftest] live : joueur pret");
    }

    /** Acces differe aux objets Cobblemon (resolus par identifiant). */
    private enum BuiltinItem {
        RARE_CANDY("cobblemon:rare_candy");

        private final String id;

        BuiltinItem(String id) {
            this.id = id;
        }

        Item get() {
            return fr.cobblepulse.crates.core.Reg.item(id, Items.AIR);
        }
    }

    static List<Locations.Loc> links() {
        return Links.all();
    }
}
