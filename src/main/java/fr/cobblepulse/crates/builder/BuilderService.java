package fr.cobblepulse.crates.builder;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.config.BuilderConfig;
import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.core.Msg;
import fr.cobblepulse.crates.core.Perms;
import fr.cobblepulse.crates.core.Reg;
import fr.cobblepulse.crates.core.TextUtil;
import fr.cobblepulse.crates.crate.CrateService;
import fr.cobblepulse.crates.net.Net;
import fr.cobblepulse.crates.net.Views;
import fr.cobblepulse.crates.pokemon.AbilityTools;
import fr.cobblepulse.crates.pokemon.FormTools;
import fr.cobblepulse.crates.pokemon.SpeciesLookup;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.joml.Vector4f;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Menu Voeu ADN : remodelage d'un Pokemon de l'equipe ou du PC contre des Voeux ADN.
 * Toutes les verifications (prix, legalite, propriete, combat) sont faites cote serveur.
 */
public final class BuilderService {
    public static final List<String> STAT_IDS = List.of("hp", "atk", "def", "spa", "spd", "spe");
    public static final Map<String, String> OPS = new LinkedHashMap<>();

    static {
        OPS.put("shiny", "Chromatique");
        OPS.put("hidden", "Talent caché");
        OPS.put("form", "Forme");
        OPS.put("ability", "Talent");
        OPS.put("nature", "Nature");
        OPS.put("ivs", "IV");
        OPS.put("evs", "EV");
    }

    private BuilderService() {}

    public static Stats stat(String id) {
        return switch (id) {
            case "hp" -> Stats.HP;
            case "atk" -> Stats.ATTACK;
            case "def" -> Stats.DEFENCE;
            case "spa" -> Stats.SPECIAL_ATTACK;
            case "spd" -> Stats.SPECIAL_DEFENCE;
            default -> Stats.SPEED;
        };
    }

    public static String statId(Stat s) {
        if (s == Stats.HP) return "hp";
        if (s == Stats.ATTACK) return "atk";
        if (s == Stats.DEFENCE) return "def";
        if (s == Stats.SPECIAL_ATTACK) return "spa";
        if (s == Stats.SPECIAL_DEFENCE) return "spd";
        if (s == Stats.SPEED) return "spe";
        return "";
    }

    public static Item currency() {
        return Reg.item(Cfg.builder.monnaie, null);
    }

    public static int wishes(ServerPlayer p) {
        return CrateService.countItem(p, currency());
    }

    // ------------------------------------------------------------------ Recherche du Pokemon

    public static Pokemon find(ServerPlayer p, UUID id) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(p);
        Pokemon pk = party.get(id);
        if (pk != null) return pk;
        if (!Cfg.builder.autoriserPc) return null;
        try {
            return Cobblemon.INSTANCE.getStorage().getPC(p).get(id);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean speciesForbidden(Species s) {
        for (String b : Cfg.builder.especesInterdites) {
            Species x = SpeciesLookup.resolve(b);
            if (x != null && x == s) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Vues

    private static JsonObject nature(Nature n, HolderLookup.Provider regs) {
        JsonObject o = new JsonObject();
        o.addProperty("id", n.getName().getPath());
        o.addProperty("name", Views.comp(Component.translatable(n.getDisplayName()), regs));
        o.addProperty("plus", n.getIncreasedStat() == null ? "" : statId(n.getIncreasedStat()));
        o.addProperty("minus", n.getDecreasedStat() == null ? "" : statId(n.getDecreasedStat()));
        return o;
    }

    private static JsonObject ability(PotentialAbility pa, HolderLookup.Provider regs) {
        JsonObject o = new JsonObject();
        o.addProperty("id", pa.getTemplate().getName());
        o.addProperty("name", Views.comp(Component.translatable(pa.getTemplate().getDisplayName()), regs));
        return o;
    }

    public static JsonObject mon(Pokemon pk, String where, int box, int slot, HolderLookup.Provider regs) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", pk.getUuid().toString());
        o.addProperty("where", where);
        o.addProperty("box", box);
        o.addProperty("slot", slot);
        o.addProperty("icon", Views.item(PokemonItem.from(pk), regs));
        o.addProperty("name", Views.comp(pk.getDisplayName(false), regs));
        o.addProperty("species", Views.comp(pk.getSpecies().getTranslatedName(), regs));
        o.addProperty("level", pk.getLevel());
        o.addProperty("shiny", pk.getShiny());
        o.addProperty("gender", pk.getGender().name().toLowerCase(Locale.ROOT));
        Nature eff = pk.getEffectiveNature();
        o.add("nature", nature(eff, regs));
        o.addProperty("minted", pk.getMintedNature() != null);
        JsonObject ab = new JsonObject();
        ab.addProperty("id", AbilityTools.current(pk));
        ab.addProperty("name", Views.comp(Component.translatable(pk.getAbility().getTemplate().getDisplayName()), regs));
        ab.addProperty("hidden", AbilityTools.hasHidden(pk));
        o.add("ability", ab);
        PotentialAbility ha = AbilityTools.hidden(pk);
        if (ha != null) o.add("hiddenAbility", ability(ha, regs));
        JsonArray abs = new JsonArray();
        for (PotentialAbility pa : AbilityTools.normal(pk)) abs.add(ability(pa, regs));
        o.add("abilities", abs);
        Species sp = pk.getSpecies();
        o.addProperty("form", FormTools.label(sp, pk.getForm()));
        JsonArray forms = new JsonArray();
        for (FormData f : FormTools.allowed(sp)) {
            JsonObject fo = new JsonObject();
            fo.addProperty("name", f.getName());
            fo.addProperty("label", FormTools.label(sp, f));
            fo.addProperty("current", FormTools.same(f, pk.getForm()));
            Set<String> aspects = new HashSet<>(f.getAspects());
            if (pk.getShiny()) aspects.add("shiny");
            try {
                fo.addProperty("icon", Views.item(PokemonItem.from(sp, aspects, 1, new Vector4f(1f, 1f, 1f, 1f)), regs));
            } catch (Throwable ignored) {
            }
            forms.add(fo);
        }
        o.add("forms", forms);
        JsonObject ivs = new JsonObject();
        JsonObject evs = new JsonObject();
        for (String s : STAT_IDS) {
            Integer iv = pk.getIvs().get(stat(s));
            Integer ev = pk.getEvs().get(stat(s));
            ivs.addProperty(s, iv == null ? 0 : iv);
            evs.addProperty(s, ev == null ? 0 : ev);
        }
        o.add("ivs", ivs);
        o.add("evs", evs);
        o.addProperty("locked", speciesForbidden(sp));
        return o;
    }

    private static JsonObject prices() {
        BuilderConfig.Prix p = Cfg.builder.prix;
        JsonObject o = new JsonObject();
        o.addProperty("shiny", p.shiny);
        o.addProperty("hidden", p.talentCache);
        o.addProperty("form", p.forme);
        o.addProperty("ability", p.talentNormal);
        o.addProperty("nature", p.nature);
        o.addProperty("iv", p.ivParStat);
        o.addProperty("evs", p.evParSession);
        return o;
    }

    public static JsonObject root(ServerPlayer p) {
        HolderLookup.Provider regs = p.registryAccess();
        JsonObject o = new JsonObject();
        o.addProperty("wishes", wishes(p));
        o.addProperty("bypass", Perms.builderBypass(p));
        o.addProperty("canUse", Perms.player(p, Perms.BUILDER_USE));
        o.addProperty("shinyReversible", Cfg.builder.shinyReversible);
        o.add("prices", prices());
        Item cur = currency();
        o.addProperty("currency", Views.item(cur == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(cur), regs));
        JsonArray party = new JsonArray();
        PlayerPartyStore store = Cobblemon.INSTANCE.getStorage().getParty(p);
        for (int i = 0; i < 6; i++) {
            Pokemon pk = store.get(i);
            party.add(pk == null ? new JsonObject() : mon(pk, "party", -1, i, regs));
        }
        o.add("party", party);
        o.addProperty("pc", Cfg.builder.autoriserPc);
        int boxes = 0;
        if (Cfg.builder.autoriserPc) {
            try {
                boxes = Cobblemon.INSTANCE.getStorage().getPC(p).getBoxes().size();
            } catch (Throwable ignored) {
            }
        }
        o.addProperty("boxes", boxes);
        JsonArray natures = new JsonArray();
        for (Nature n : Natures.all()) natures.add(nature(n, regs));
        o.add("natures", natures);
        return o;
    }

    public static JsonObject box(ServerPlayer p, int index) {
        HolderLookup.Provider regs = p.registryAccess();
        JsonObject o = new JsonObject();
        o.addProperty("box", index);
        JsonArray mons = new JsonArray();
        if (Cfg.builder.autoriserPc) {
            try {
                PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(p);
                List<PCBox> boxes = pc.getBoxes();
                o.addProperty("boxes", boxes.size());
                if (index >= 0 && index < boxes.size()) {
                    PCBox b = boxes.get(index);
                    o.addProperty("name", b.getName() == null ? "" : b.getName());
                    for (int i = 0; i < 30; i++) {
                        Pokemon pk = b.get(i);
                        mons.add(pk == null ? new JsonObject() : mon(pk, "pc", index, i, regs));
                    }
                }
            } catch (Throwable t) {
                Crates.LOG.warn("[Crates] PC de {} illisible : {}", p.getGameProfile().getName(), t.toString());
            }
        }
        o.add("mons", mons);
        return o;
    }

    // ------------------------------------------------------------------ Ouverture

    public static boolean open(ServerPlayer p) {
        if (!Cfg.builder.actif) {
            Msg.send(p, "builder.disabled");
            return false;
        }
        if (!Perms.player(p, Perms.BUILDER_OPEN)) {
            Msg.send(p, "general.no_permission");
            return false;
        }
        if (!Net.hasClient(p)) {
            Msg.send(p, "general.no_client");
            return false;
        }
        Net.send(p, "builder_open", root(p));
        return true;
    }

    // ------------------------------------------------------------------ Modifications

    /** Resultat d'une demande (auto-test et retour a l'ecran). */
    public record Result(boolean ok, String message, int cost) {}

    private static Result refuse(ServerPlayer p, String message) {
        Msg.send(p, "builder.refused", "reason", message);
        Net.toast(p, false, message);
        return new Result(false, message, 0);
    }

    public static Result apply(ServerPlayer p, String uuid, String op, JsonElement value) {
        BuilderConfig cfg = Cfg.builder;
        if (!cfg.actif) return refuse(p, "Le menu Vœu ADN est désactivé.");
        if (!Perms.player(p, Perms.BUILDER_USE)) return refuse(p, "Vous n'avez pas la permission d'utiliser le menu Vœu ADN.");
        UUID id;
        try {
            id = UUID.fromString(uuid);
        } catch (Exception e) {
            return refuse(p, "Pokémon invalide.");
        }
        Pokemon pk = find(p, id);
        if (pk == null) return refuse(p, "Pokémon introuvable dans votre équipe" + (cfg.autoriserPc ? " ou votre PC." : "."));
        if (BattleRegistry.getBattleByParticipatingPlayer(p) != null) return refuse(p, "Impossible pendant un combat.");
        if (speciesForbidden(pk.getSpecies())) return refuse(p, "Cette espèce ne peut pas être remodelée.");
        BuilderConfig.Prix prix = cfg.prix;
        int cost;
        String what;
        Runnable action;
        switch (op) {
            case "shiny" -> {
                boolean to = !pk.getShiny();
                if (!to && !cfg.shinyReversible) return refuse(p, "Ce Pokémon est déjà chromatique.");
                cost = prix.shiny;
                what = to ? "Version chromatique" : "Version normale";
                action = () -> pk.setShiny(to);
            }
            case "hidden" -> {
                PotentialAbility ha = AbilityTools.hidden(pk);
                if (ha == null) return refuse(p, "Ce Pokémon n'a pas de talent caché.");
                if (AbilityTools.hasHidden(pk)) return refuse(p, "Ce Pokémon a déjà son talent caché.");
                cost = prix.talentCache;
                what = "Talent caché";
                action = () -> AbilityTools.apply(pk, ha);
            }
            case "ability" -> {
                String name = str(value);
                PotentialAbility pa = AbilityTools.find(pk, name, false);
                if (pa == null) return refuse(p, "Talent indisponible pour ce Pokémon : " + name);
                if (pa.getTemplate().getName().equals(AbilityTools.current(pk))) return refuse(p, "Ce Pokémon a déjà ce talent.");
                cost = prix.talentNormal;
                what = "Talent";
                action = () -> AbilityTools.apply(pk, pa);
            }
            case "nature" -> {
                Nature n = Natures.getNature(str(value).contains(":") ? str(value) : "cobblemon:" + str(value).toLowerCase(Locale.ROOT));
                if (n == null) return refuse(p, "Nature inconnue : " + str(value));
                if (n == pk.getEffectiveNature()) return refuse(p, "Ce Pokémon a déjà cette nature.");
                cost = prix.nature;
                what = "Nature";
                action = () -> {
                    pk.setNature(n);
                    pk.setMintedNature(null);
                };
            }
            case "form" -> {
                FormData target = FormTools.byName(pk.getSpecies(), str(value));
                if (target == null) return refuse(p, "Forme inconnue : " + str(value));
                boolean ok = false;
                for (FormData f : FormTools.allowed(pk.getSpecies())) if (FormTools.same(f, target)) ok = true;
                if (!ok) return refuse(p, "Cette forme n'est pas disponible (Méga, Dynamax et formes de combat exclues).");
                if (FormTools.same(target, pk.getForm())) return refuse(p, "Ce Pokémon a déjà cette forme.");
                cost = prix.forme;
                what = "Forme " + FormTools.label(pk.getSpecies(), target);
                action = () -> {
                    if (!FormTools.apply(pk, target)) throw new IllegalStateException("forme non appliquée");
                };
            }
            case "ivs" -> {
                List<String> stats = new ArrayList<>();
                if (value != null && value.isJsonArray()) for (JsonElement e : value.getAsJsonArray()) stats.add(e.getAsString());
                stats.removeIf(s -> !STAT_IDS.contains(s));
                List<String> todo = new ArrayList<>();
                for (String s : new java.util.LinkedHashSet<>(stats)) {
                    Integer cur = pk.getIvs().get(stat(s));
                    if (cur == null || cur < 31) todo.add(s);
                }
                if (todo.isEmpty()) return refuse(p, "Choisissez au moins une statistique qui n'est pas déjà à 31.");
                cost = prix.ivParStat * todo.size();
                what = "IV à 31 (" + todo.size() + " stat" + (todo.size() > 1 ? "s" : "") + ")";
                action = () -> {
                    for (String s : todo) pk.setIV(stat(s), 31);
                };
            }
            case "evs" -> {
                if (value == null || !value.isJsonObject()) return refuse(p, "Répartition d'EV invalide.");
                JsonObject v = value.getAsJsonObject();
                Map<String, Integer> target = new LinkedHashMap<>();
                int total = 0;
                boolean changed = false;
                for (String s : STAT_IDS) {
                    int n;
                    try {
                        n = v.has(s) ? v.get(s).getAsInt() : 0;
                    } catch (Exception e) {
                        return refuse(p, "Valeur d'EV invalide (" + s + ").");
                    }
                    if (n < 0 || n > 252) return refuse(p, "Chaque statistique doit avoir entre 0 et 252 EV.");
                    total += n;
                    target.put(s, n);
                    Integer cur = pk.getEvs().get(stat(s));
                    if (cur == null || cur != n) changed = true;
                }
                if (total > 510) return refuse(p, "Total d'EV trop élevé : " + total + " / 510.");
                if (!changed) return refuse(p, "Aucun changement d'EV.");
                cost = prix.evParSession;
                what = "Répartition des EV";
                action = () -> {
                    for (Map.Entry<String, Integer> e : target.entrySet()) pk.setEV(stat(e.getKey()), e.getValue());
                };
            }
            default -> {
                return refuse(p, "Action inconnue : " + op);
            }
        }
        cost = Math.max(1, cost);
        boolean bypass = Perms.builderBypass(p);
        Item cur = currency();
        if (!bypass) {
            if (cur == null) return refuse(p, "Monnaie introuvable : " + cfg.monnaie);
            int have = CrateService.countItem(p, cur);
            if (have < cost) return refuse(p, "Il vous faut " + cost + " Vœu" + (cost > 1 ? "x" : "") + " ADN (vous en avez " + have + ").");
        }
        String before = describe(pk);
        try {
            action.run();
        } catch (Throwable t) {
            Crates.LOG.warn("[Crates] Voeu ADN {} sur {} : {}", op, pk.getSpecies().getName(), t.toString());
            return refuse(p, "La modification n'a pas pu être appliquée (" + t.getMessage() + "). Aucun Vœu ADN dépensé.");
        }
        if (!bypass) CrateService.takeItem(p, cur, cost);
        String monName = TextUtil.strip(pk.getDisplayName(false).getString());
        Msg.send(p, bypass ? "builder.done_bypass" : "builder.done", "what", what, "pokemon", monName, "cost", cost);
        log(p.getGameProfile().getName(), pk, op, before + " -> " + describe(pk), bypass ? 0 : cost);
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("message", what + " : " + monName + (bypass ? " (gratuit)" : " (-" + cost + " Vœu" + (cost > 1 ? "x" : "") + " ADN)"));
        r.addProperty("wishes", wishes(p));
        r.add("mon", mon(pk, Cobblemon.INSTANCE.getStorage().getParty(p).get(pk.getUuid()) != null ? "party" : "pc", -1, -1, p.registryAccess()));
        Net.send(p, "builder_result", r);
        return new Result(true, what, bypass ? 0 : cost);
    }

    private static String str(JsonElement e) {
        try {
            return e == null || e.isJsonNull() ? "" : e.getAsString();
        } catch (Exception x) {
            return "";
        }
    }

    private static String describe(Pokemon pk) {
        StringBuilder iv = new StringBuilder();
        StringBuilder ev = new StringBuilder();
        for (String s : STAT_IDS) {
            iv.append(iv.length() == 0 ? "" : "/").append(pk.getIvs().get(stat(s)));
            ev.append(ev.length() == 0 ? "" : "/").append(pk.getEvs().get(stat(s)));
        }
        return pk.getSpecies().getName() + "[" + pk.getForm().getName() + "]" + (pk.getShiny() ? "*" : "") + " " + pk.getEffectiveNature().getName().getPath()
                + " " + AbilityTools.current(pk) + " IV " + iv + " EV " + ev;
    }

    private static void log(String player, Pokemon pk, String op, String detail, int cost) {
        if (!Cfg.general.journal) return;
        LocalDateTime now = LocalDateTime.now();
        String line = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now) + " | " + player + " | " + pk.getUuid() + " | " + op + " | " + cost
                + " | " + detail + System.lineSeparator();
        Path f = Cfg.root().resolve("journal").resolve("voeu_adn-" + DateTimeFormatter.ofPattern("yyyy-MM").format(now) + ".log");
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            Crates.LOG.warn("[Crates] Journal Voeu ADN : {}", e.toString());
        }
    }
}
