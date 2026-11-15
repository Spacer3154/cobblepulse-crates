package fr.cobblepulse.crates.pokemon;

import com.cobblemon.mod.common.api.pokemon.feature.ChoiceSpeciesFeatureProvider;
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeature;
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeatureProvider;
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatureProvider;
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures;
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import fr.cobblepulse.crates.config.BuilderConfig;
import fr.cobblepulse.crates.config.Cfg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Formes d'une espece (regionales, variantes) : liste des formes autorisees (hors Mega, Dynamax et formes de combat)
 * et changement de forme d'un Pokemon existant via ses caracteristiques d'espece (species features) et ses aspects.
 */
public final class FormTools {
    private FormTools() {}

    private static String key(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Forme standard + formes alternatives, sans doublon. */
    public static List<FormData> forms(Species s) {
        Map<String, FormData> out = new LinkedHashMap<>();
        FormData std = s.getStandardForm();
        if (std != null) out.put(key(std.getName()), std);
        for (FormData f : s.getForms()) out.putIfAbsent(key(f.getName()), f);
        return new ArrayList<>(out.values());
    }

    public static boolean isStandard(Species s, FormData f) {
        FormData std = s.getStandardForm();
        return f == null || std == null || key(f.getName()).equals(key(std.getName())) || f.getAspects().isEmpty();
    }

    /** Forme interdite par la configuration (Mega, Dynamax, formes de combat, formes a objet). */
    public static boolean forbidden(FormData f) {
        BuilderConfig cfg = Cfg.builder;
        Set<String> bad = new HashSet<>();
        for (String b : cfg.formesInterdites) bad.add(key(b));
        if (bad.contains(key(f.getName()))) return true;
        for (String a : f.getAspects()) if (bad.contains(key(a))) return true;
        String n = key(f.getName());
        if (n.startsWith("mega") || n.contains("gmax") || n.contains("gigantamax") || n.contains("dynamax")) return true;
        if (cfg.exclureFormesAObjet) {
            try {
                if (f.getRequiredItem() != null && !f.getRequiredItem().isBlank()) return true;
                if (f.getRequiredItems() != null && !f.getRequiredItems().isEmpty()) return true;
                if (f.getRequiredMove() != null && !f.getRequiredMove().isBlank()) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /** Formes proposees pour une espece (forme standard incluse). */
    public static List<FormData> allowed(Species s) {
        List<FormData> out = new ArrayList<>();
        for (FormData f : forms(s)) {
            if (isStandard(s, f) || !forbidden(f)) out.add(f);
        }
        return out;
    }

    public static boolean same(FormData a, FormData b) {
        if (a == null || b == null) return a == b;
        return key(a.getName()).equals(key(b.getName()));
    }

    public static FormData byName(Species s, String name) {
        String k = key(name);
        for (FormData f : forms(s)) {
            if (key(f.getName()).equals(k)) return f;
            for (String a : f.getAspects()) if (key(a).equals(k)) return f;
        }
        return null;
    }

    /** Nom affiche d'une forme ("Normale", "Alola", "Lavage"...). */
    public static String label(Species s, FormData f) {
        if (isStandard(s, f)) return "Normale";
        String n = f.getName();
        return switch (key(n)) {
            case "alola" -> "Alola";
            case "galar" -> "Galar";
            case "hisui" -> "Hisui";
            case "paldea" -> "Paldea";
            default -> n.replace('-', ' ');
        };
    }

    /** Applique la forme {@code target} au Pokemon ; renvoie true si la forme obtenue est bien celle demandee. */
    public static boolean apply(Pokemon pk, FormData target) {
        Species sp = pk.getSpecies();
        if (same(pk.getForm(), target)) return true;
        Set<String> targetAspects = new HashSet<>(target.getAspects());
        Set<String> formAspects = new HashSet<>();
        for (FormData f : forms(sp)) formAspects.addAll(f.getAspects());
        Set<String> handled = new HashSet<>();
        for (SpeciesFeatureProvider<?> provider : SpeciesFeatures.getFeaturesFor(sp)) {
            try {
                if (provider instanceof FlagSpeciesFeatureProvider fp && fp.isAspect()) {
                    for (String k : fp.getKeys()) {
                        if (!formAspects.contains(k) && !targetAspects.contains(k)) continue;
                        boolean want = targetAspects.contains(k);
                        new FlagSpeciesFeature(k, want).apply(pk);
                        if (want) handled.add(k);
                    }
                } else if (provider instanceof ChoiceSpeciesFeatureProvider cp && cp.isAspect() && !cp.getKeys().isEmpty()) {
                    String fk = cp.getKeys().get(0);
                    List<? extends String> choices = cp.getChoices();
                    String chosen = null;
                    String neutral = null;
                    for (String choice : choices) {
                        String aspect = cp.getAspect(new StringSpeciesFeature(fk, choice));
                        if (aspect != null && targetAspects.contains(aspect)) {
                            chosen = choice;
                            handled.add(aspect);
                        }
                        if (neutral == null && (aspect == null || !formAspects.contains(aspect))) neutral = choice;
                    }
                    if (chosen == null) {
                        StringSpeciesFeature cur = cp.get(pk);
                        String curAspect = cur == null ? null : cp.getAspect(cur);
                        if (curAspect != null && formAspects.contains(curAspect) && !targetAspects.contains(curAspect)) {
                            String def = cp.getDefault();
                            chosen = def != null && !"random".equals(def) && choices.contains(def) ? def : neutral;
                        }
                    }
                    if (chosen != null) new StringSpeciesFeature(fk, chosen).apply(pk);
                }
            } catch (Throwable ignored) {
            }
        }
        Set<String> forced = new HashSet<>(pk.getForcedAspects());
        forced.removeIf(a -> formAspects.contains(a) && !targetAspects.contains(a));
        for (String a : targetAspects) if (!handled.contains(a)) forced.add(a);
        pk.setForcedAspects(forced);
        pk.updateAspects();
        pk.updateForm();
        if (!same(pk.getForm(), target)) {
            try {
                pk.setForm(target);
            } catch (Throwable ignored) {
            }
        }
        return same(pk.getForm(), target);
    }
}
