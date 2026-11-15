package fr.cobblepulse.crates.client;

import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonObject;
import fr.cobblepulse.crates.pokemon.SpeciesLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Suggestions et apercus a partir des registres synchronises sur le client (especes, fakemons, capacites, objets). */
public final class ClientData {
    public static final List<String> NATURES = List.of("hardy", "lonely", "brave", "adamant", "naughty", "bold", "docile", "relaxed",
            "impish", "lax", "timid", "hasty", "serious", "jolly", "naive", "modest", "mild", "quiet", "bashful", "rash", "calm", "gentle",
            "sassy", "careful", "quirky");
    public static final List<String> NATURES_FR = List.of("Hardi", "Solo", "Brave", "Rigide", "Mauvais", "Assuré", "Docile", "Relax",
            "Malin", "Lâche", "Timide", "Pressé", "Sérieux", "Jovial", "Naïf", "Modeste", "Doux", "Discret", "Pudique", "Foufou", "Calme",
            "Gentil", "Malpoli", "Prudent", "Bizarre");
    public static final List<String> REWARD_TYPES = List.of("objet", "commande", "pokemon", "pokemon_aleatoire");
    public static final List<String> REWARD_TYPE_NAMES = List.of("Objet", "Commande", "Pokémon précis", "Pokémon aléatoire");
    public static final List<String> STATS = List.of("hp", "atk", "def", "spa", "spd", "spe");
    public static final List<String> STATS_FR = List.of("PV", "Attaque", "Défense", "Atq. Spé.", "Déf. Spé.", "Vitesse");
    public static final List<String> STATS_SHORT = List.of("PV", "Atq", "Déf", "AtqS", "DéfS", "Vit");

    private ClientData() {}

    private static String id(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static String statName(String id) {
        int i = STATS.indexOf(id);
        return i < 0 ? id : STATS_FR.get(i);
    }

    public static List<String> species(String prefix) {
        String p = id(prefix);
        List<String> out = new ArrayList<>();
        if (p.isEmpty()) return out;
        try {
            for (Species s : PokemonSpecies.getSpecies()) {
                ResourceLocation rid = s.getResourceIdentifier();
                boolean cobblemon = "cobblemon".equals(rid.getNamespace());
                if (id(s.getName()).startsWith(p) || id(rid.getPath()).startsWith(p) || id(rid.toString()).startsWith(p)) {
                    out.add(cobblemon ? rid.getPath() : rid.toString());
                }
                if (out.size() >= 8) break;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static List<String> moves(String prefix) {
        String p = id(prefix);
        List<String> out = new ArrayList<>();
        if (p.isEmpty()) return out;
        try {
            for (String n : Moves.names()) {
                if (id(n).startsWith(p)) out.add(id(n));
                if (out.size() >= 8) break;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static List<String> items(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (p.length() < 2) return out;
        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            String s = rl.toString();
            if (s.startsWith(p) || rl.getPath().startsWith(p)) out.add(s);
            if (out.size() >= 8) break;
        }
        return out;
    }

    public static List<String> particles(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (p.length() < 2) return out;
        for (ResourceLocation rl : BuiltInRegistries.PARTICLE_TYPE.keySet()) {
            if (rl.toString().startsWith(p) || rl.getPath().startsWith(p)) out.add(rl.toString());
            if (out.size() >= 8) break;
        }
        return out;
    }

    public static List<String> sounds(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (p.length() < 3) return out;
        for (ResourceLocation rl : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (rl.toString().startsWith(p) || rl.getPath().startsWith(p) || rl.getPath().contains(p)) out.add(rl.toString());
            if (out.size() >= 8) break;
        }
        return out;
    }

    /** Icone d'un Pokemon (espece, forme/aspects, chromatique). */
    public static ItemStack pokemonIcon(String species, String form, boolean shiny) {
        try {
            Species s = SpeciesLookup.resolve(species);
            if (s == null) return new ItemStack(Items.BARRIER);
            Set<String> aspects = new HashSet<>();
            if (shiny) aspects.add("shiny");
            if (form != null) {
                for (String a : form.split(",")) {
                    String v = a.trim().toLowerCase(Locale.ROOT);
                    if (v.isEmpty()) continue;
                    aspects.add(switch (v) {
                        case "alola", "alolan" -> "alolan";
                        case "galar", "galarian" -> "galarian";
                        case "hisui", "hisuian" -> "hisuian";
                        case "paldea", "paldean" -> "paldean";
                        default -> v;
                    });
                }
            }
            return PokemonItem.from(s, aspects, 1, new Vector4f(1f, 1f, 1f, 1f));
        } catch (Throwable t) {
            return new ItemStack(Items.BARRIER);
        }
    }

    public static String speciesName(String species) {
        try {
            Species s = SpeciesLookup.resolve(species);
            return s == null ? species : s.getTranslatedName().getString();
        } catch (Throwable t) {
            return species;
        }
    }

    // ------------------------------------------------------------------ Recompenses (editeur)

    public static ItemStack rewardIcon(JsonObject r) {
        String type = Ui.str(r, "type", "objet");
        String icon = Ui.str(r, "icone", "");
        switch (type) {
            case "objet" -> {
                if (!icon.isBlank()) return Ui.itemById(icon);
                ItemStack s = Ui.itemJson(r.get("objet"));
                return s.isEmpty() ? new ItemStack(Items.BARRIER) : s;
            }
            case "pokemon" -> {
                if (!icon.isBlank()) return Ui.itemById(icon);
                JsonObject m = Ui.obj(r, "pokemon");
                return pokemonIcon(Ui.str(m, "species", ""), Ui.str(m, "form", ""), Ui.bool(m, "shiny", false));
            }
            case "pokemon_aleatoire" -> {
                return Ui.itemById(icon.isBlank() ? "cobblemon:poke_ball" : icon);
            }
            default -> {
                return Ui.itemById(icon.isBlank() ? "minecraft:paper" : icon);
            }
        }
    }

    /** Libelle court d'une recompense (sans couleur). */
    public static String rewardLabel(JsonObject r) {
        String nom = Ui.str(r, "nom", "");
        if (!nom.isBlank()) return strip(nom);
        String type = Ui.str(r, "type", "objet");
        switch (type) {
            case "objet" -> {
                ItemStack s = Ui.itemJson(r.get("objet"));
                if (s.isEmpty()) return "Objet invalide";
                return s.getHoverName().getString() + (s.getCount() > 1 ? " ×" + s.getCount() : "");
            }
            case "pokemon" -> {
                JsonObject m = Ui.obj(r, "pokemon");
                return speciesName(Ui.str(m, "species", "?")) + " Nv. " + Ui.lng(m, "level", 5) + (Ui.bool(m, "shiny", false) ? " ★" : "");
            }
            case "pokemon_aleatoire" -> {
                JsonObject a = Ui.obj(r, "aleatoire");
                return "Pokémon aléatoire (" + Ui.str(a, "pool", "all") + ", Nv. " + Ui.lng(a, "niveau_min", 5) + "-" + Ui.lng(a, "niveau_max", 30) + ")";
            }
            default -> {
                return "Commande";
            }
        }
    }

    public static String strip(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)<#[0-9a-f]{6}>|</#>", "").replaceAll("(?i)[&§]#[0-9a-f]{6}", "").replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }

    public static JsonObject newReward(String type) {
        JsonObject r = new JsonObject();
        r.addProperty("id", java.util.UUID.randomUUID().toString().substring(0, 8));
        r.addProperty("type", type);
        r.addProperty("nom", "");
        r.addProperty("icone", "");
        r.addProperty("poids", 10);
        r.addProperty("rarete", "commun");
        r.addProperty("annonce", false);
        switch (type) {
            case "objet" -> {
                JsonObject o = new JsonObject();
                o.addProperty("id", "cobblemon:rare_candy");
                o.addProperty("count", 1);
                r.add("objet", o);
            }
            case "commande" -> {
                com.google.gson.JsonArray a = new com.google.gson.JsonArray();
                a.add("cobbledollars give %player% 500");
                r.add("commandes", a);
                r.addProperty("nom", "&e500 CobbleDollars");
                r.addProperty("icone", "minecraft:gold_nugget");
            }
            case "pokemon" -> r.add("pokemon", newMon());
            default -> {
                JsonObject a = new JsonObject();
                a.addProperty("pool", "all");
                a.addProperty("niveau_min", 10);
                a.addProperty("niveau_max", 30);
                a.addProperty("chance_shiny", 0);
                a.addProperty("ivs_parfaits", 0);
                a.addProperty("formes", "base");
                r.add("aleatoire", a);
            }
        }
        return r;
    }

    public static JsonObject newMon() {
        JsonObject m = new JsonObject();
        m.addProperty("species", "pikachu");
        m.addProperty("form", "");
        m.addProperty("nickname", "");
        m.addProperty("level", 10);
        m.addProperty("nature", "");
        m.addProperty("ability", "");
        m.addProperty("gender", "");
        m.addProperty("shiny", false);
        m.addProperty("held_item", "");
        m.addProperty("ball", "");
        JsonObject ivs = new JsonObject();
        JsonObject evs = new JsonObject();
        for (String s : STATS) {
            ivs.addProperty(s, -1);
            evs.addProperty(s, 0);
        }
        m.add("ivs", ivs);
        m.add("evs", evs);
        m.add("moves", new com.google.gson.JsonArray());
        m.addProperty("tera_type", "");
        return m;
    }
}
