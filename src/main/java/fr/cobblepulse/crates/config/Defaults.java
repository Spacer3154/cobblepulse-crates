package fr.cobblepulse.crates.config;

import com.google.gson.JsonObject;
import fr.cobblepulse.crates.item.KeyType;
import fr.cobblepulse.crates.pokemon.MonSpec;

import java.util.ArrayList;
import java.util.List;

/** Contenu d'exemple : 10 caisses (une par cle) et 4 pools d'especes. */
public final class Defaults {
    private static int seq;

    private Defaults() {}

    // ------------------------------------------------------------------ Aides

    private static RewardDef base(String type, String rarity, double weight) {
        RewardDef r = new RewardDef();
        r.id = "r" + (++seq);
        r.type = type;
        r.rarete = rarity;
        r.poids = weight;
        r.annonce = "legendaire".equals(rarity) || "epique".equals(rarity);
        return r;
    }

    public static RewardDef item(String id, int count, String rarity, double weight) {
        RewardDef r = base("objet", rarity, weight);
        JsonObject o = new JsonObject();
        o.addProperty("id", id.contains(":") ? id : "cobblemon:" + id);
        o.addProperty("count", count);
        r.objet = o;
        return r;
    }

    public static RewardDef command(String name, String icon, String rarity, double weight, String... commands) {
        RewardDef r = base("commande", rarity, weight);
        r.nom = name;
        r.icone = icon;
        r.commandes = new ArrayList<>(List.of(commands));
        r.description = new ArrayList<>(List.of("&8Commande à adapter à l'économie du serveur"));
        return r;
    }

    public static RewardDef mon(String species, int level, boolean shiny, int perfectIvs, String rarity, double weight) {
        RewardDef r = base("pokemon", rarity, weight);
        MonSpec m = new MonSpec();
        m.species = species;
        m.level = level;
        m.shiny = shiny;
        String[] stats = {"hp", "atk", "def", "spa", "spd", "spe"};
        for (int i = 0; i < perfectIvs && i < 6; i++) m.ivs.put(stats[i], 31);
        r.pokemon = m;
        return r;
    }

    public static RewardDef random(String pool, int min, int max, double shinyPct, int perfectIvs, String forms, String icon, String name,
                                   String rarity, double weight) {
        RewardDef r = base("pokemon_aleatoire", rarity, weight);
        RewardDef.RandomMon a = new RewardDef.RandomMon();
        a.pool = pool;
        a.niveauMin = min;
        a.niveauMax = max;
        a.chanceShiny = shinyPct;
        a.ivsParfaits = perfectIvs;
        a.formes = forms;
        r.aleatoire = a;
        r.icone = icon;
        r.nom = name;
        return r;
    }

    private static CrateDef crate(KeyType key, String name, String desc, String animation, RewardDef... rewards) {
        CrateDef c = new CrateDef();
        c.id = key.shortName;
        c.nom = "&" + key.hex() + name;
        c.description = desc;
        c.cle = key.itemId();
        c.animation = animation;
        c.couleur = key.hex();
        c.particuleAttente = key.idleParticle;
        c.particuleOuverture = key.openParticle;
        c.recompenses = new ArrayList<>(List.of(rewards));
        c.normalize();
        return c;
    }

    private static RewardDef money(int amount, String rarity, double weight) {
        return command("&e" + amount + " CobbleDollars", "minecraft:gold_nugget", rarity, weight, "cobbledollars give %player% " + amount);
    }

    // ------------------------------------------------------------------ Caisses

    public static List<CrateDef> crates() {
        seq = 0;
        List<CrateDef> l = new ArrayList<>();
        l.add(crate(KeyType.OBSIDIENNE, "Caisse d'Obsidienne", "&7Forgée dans la roche volcanique.", "roulette",
                item("fire_stone", 2, "commun", 28),
                item("exp_candy_m", 4, "commun", 28),
                item("heavy_ball", 8, "commun", 24),
                item("charcoal_stick", 1, "peu_commun", 14),
                item("magmarizer", 1, "peu_commun", 12),
                money(1000, "peu_commun", 12),
                item("rare_candy", 3, "rare", 8),
                random("all", 20, 35, 0, 3, "base", "cobblemon:great_ball", "&bPokémon mystère &7(3 IV parfaits)", "rare", 6),
                mon("larvesta", 20, false, 2, "epique", 3),
                item("ability_patch", 1, "legendaire", 1)));
        l.add(crate(KeyType.SONGES, "Caisse des Songes", "&7Un rêve pastel entre deux mondes.", "roue",
                item("moon_stone", 2, "commun", 28),
                item("exp_candy_s", 8, "commun", 28),
                item("dream_ball", 3, "peu_commun", 14),
                item("whipped_dream", 1, "peu_commun", 12),
                item("sachet", 1, "peu_commun", 12),
                item("timid_mint", 1, "rare", 7),
                random("all", 10, 25, 10, 2, "aleatoire", "cobblemon:dream_ball", "&dPokémon onirique &7(10 % chromatique)", "rare", 6),
                mon("munna", 15, true, 2, "epique", 3),
                item("ability_capsule", 1, "epique", 2)));
        l.add(crate(KeyType.RACINE, "Caisse-Racine", "&7Du bois ancien parcouru de pousses vivantes.", "roulette",
                item("leaf_stone", 2, "commun", 28),
                item("nest_ball", 8, "commun", 26),
                item("miracle_seed", 1, "peu_commun", 14),
                item("leftovers", 1, "rare", 8),
                item("adamant_mint", 1, "rare", 7),
                money(750, "commun", 20),
                random("all", 15, 30, 0, 3, "aleatoire", "cobblemon:nest_ball", "&aPokémon sauvage &7(formes régionales possibles)", "rare", 6),
                mon("bulbasaur", 10, false, 3, "epique", 3),
                item("exp_share", 1, "epique", 2)));
        l.add(crate(KeyType.GIVRE_ETOILE, "Caisse de Givre-Étoile", "&7L'éclat glacé d'une nuit polaire.", "roulette",
                item("ice_stone", 2, "commun", 28),
                item("exp_candy_m", 4, "commun", 26),
                item("premier_ball", 10, "commun", 22),
                item("never_melt_ice", 1, "peu_commun", 14),
                item("hp_up", 3, "peu_commun", 12),
                item("rare_candy", 3, "rare", 8),
                random("all", 20, 35, 5, 3, "aleatoire", "cobblemon:premier_ball", "&bPokémon des neiges &7(5 % chromatique)", "rare", 6),
                mon("frigibax", 20, false, 3, "epique", 3),
                random("legendary", 40, 50, 0, 3, "base", "cobblemon:master_ball", "&6Pokémon légendaire", "legendaire", 0.8)));
        l.add(crate(KeyType.SOLAIRE, "Caisse Solaire", "&7De l'or pur et un rubis brûlant.", "roulette",
                item("sun_stone", 2, "commun", 28),
                item("luxury_ball", 6, "commun", 24),
                money(1500, "peu_commun", 14),
                item("life_orb", 1, "rare", 8),
                item("choice_band", 1, "rare", 6),
                item("ability_capsule", 1, "rare", 6),
                random("all", 25, 40, 0, 4, "base", "cobblemon:luxury_ball", "&6Pokémon radieux &7(4 IV parfaits)", "epique", 4),
                mon("larvesta", 30, true, 3, "legendaire", 1),
                item("ability_patch", 1, "epique", 2)));
        l.add(crate(KeyType.PROFONDEURS, "Caisse des Profondeurs", "&7Marquée par la pression des abysses.", "roue",
                item("water_stone", 2, "commun", 28),
                item("dive_ball", 8, "commun", 26),
                item("mystic_water", 1, "peu_commun", 14),
                item("deep_sea_scale", 1, "peu_commun", 10),
                item("deep_sea_tooth", 1, "peu_commun", 10),
                item("prism_scale", 1, "rare", 7),
                random("all", 20, 35, 0, 3, "base", "cobblemon:dive_ball", "&3Pokémon des abysses &7(3 IV parfaits)", "rare", 6),
                mon("lapras", 25, false, 3, "epique", 3),
                random("legendary", 45, 55, 0, 3, "base", "cobblemon:master_ball", "&6Pokémon légendaire", "legendaire", 0.8)));
        l.add(crate(KeyType.OMBRE, "Caisse de l'Ombre", "&7Elle absorbe toute lumière.", "roulette",
                item("dusk_stone", 2, "commun", 28),
                item("dusk_ball", 8, "commun", 26),
                item("black_glasses", 1, "peu_commun", 12),
                item("spell_tag", 1, "peu_commun", 12),
                item("reaper_cloth", 1, "rare", 8),
                random("all", 20, 35, 5, 3, "base", "cobblemon:dusk_ball", "&8Pokémon de l'ombre &7(5 % chromatique)", "rare", 6),
                mon("gastly", 20, true, 2, "epique", 3),
                random("ultra_beasts", 40, 50, 0, 3, "base", "cobblemon:beast_ball", "&5Ultra-Chimère", "legendaire", 0.8)));
        l.add(crate(KeyType.TEMPS, "Caisse du Temps", "&7Des rouages en mouvement perpétuel.", "roulette",
                item("timer_ball", 8, "commun", 26),
                item("quick_ball", 8, "commun", 24),
                item("exp_candy_l", 3, "peu_commun", 16),
                item("metal_coat", 1, "peu_commun", 12),
                item("upgrade", 1, "peu_commun", 10),
                item("rare_candy", 5, "rare", 8),
                random("all", 30, 45, 0, 4, "aleatoire", "cobblemon:timer_ball", "&6Pokémon d'une autre époque &7(4 IV parfaits)", "epique", 4),
                random("legendary", 50, 60, 0, 3, "base", "cobblemon:master_ball", "&6Pokémon légendaire", "legendaire", 1)));
        l.add(crate(KeyType.CELESTE, "Caisse Céleste", "&7Bénie par les cieux.", "roue",
                item("shiny_stone", 1, "commun", 24),
                item("dawn_stone", 1, "commun", 24),
                item("sharp_beak", 1, "peu_commun", 14),
                item("exp_candy_l", 3, "peu_commun", 14),
                item("lucky_egg", 1, "rare", 7),
                random("all", 20, 40, 25, 3, "aleatoire", "cobblemon:premier_ball", "&fPokémon céleste &7(25 % chromatique)", "epique", 5),
                mon("swablu", 20, true, 3, "epique", 3),
                random("legendary", 50, 60, 10, 3, "base", "cobblemon:master_ball", "&6Pokémon légendaire &7(10 % chromatique)", "legendaire", 1)));
        l.add(crate(KeyType.COEUR_DRAGON, "Caisse Cœur de Dragon", "&7Une braise draconique éternelle.", "roulette",
                item("ultra_ball", 8, "commun", 26),
                item("rare_candy", 5, "commun", 24),
                item("dragon_fang", 1, "peu_commun", 14),
                item("dragon_scale", 1, "peu_commun", 12),
                money(3000, "rare", 8),
                mon("dratini", 20, false, 4, "epique", 4),
                mon("gible", 20, false, 4, "epique", 4),
                random("legendary", 50, 70, 0, 4, "base", "cobblemon:master_ball", "&6Pokémon légendaire &7(4 IV parfaits)", "legendaire", 1.5),
                item("master_ball", 1, "legendaire", 1)));
        return l;
    }

    // ------------------------------------------------------------------ Pools

    public static List<PoolDef> pools() {
        List<PoolDef> l = new ArrayList<>();
        PoolDef all = new PoolDef();
        all.id = "all";
        all.nom = "Tous les Pokémon";
        all.toutesEspeces = true;
        l.add(all);
        PoolDef leg = new PoolDef();
        leg.id = "legendary";
        leg.nom = "Légendaires et fabuleux";
        leg.etiquettes = new ArrayList<>(List.of("legendary", "mythical"));
        l.add(leg);
        PoolDef ub = new PoolDef();
        ub.id = "ultra_beasts";
        ub.nom = "Ultra-Chimères";
        ub.etiquettes = new ArrayList<>(List.of("ultra_beast"));
        l.add(ub);
        PoolDef fake = new PoolDef();
        fake.id = "fakemons";
        fake.nom = "Fakemons";
        fake.espacesDeNoms = new ArrayList<>(List.of("*"));
        fake.implementeesSeulement = false;
        l.add(fake);
        return l;
    }
}
