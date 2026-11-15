package fr.cobblepulse.crates.pokemon;

import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;

import java.util.ArrayList;
import java.util.List;

/** Talents normaux et talent cache d'un Pokemon (selon sa forme actuelle). */
public final class AbilityTools {
    private AbilityTools() {}

    private static List<PotentialAbility> pool(Pokemon pk) {
        List<PotentialAbility> out = new ArrayList<>();
        for (PotentialAbility pa : pk.getForm().getAbilities()) {
            try {
                if (pa.isSatisfiedBy(pk.getAspects())) out.add(pa);
            } catch (Throwable t) {
                out.add(pa);
            }
        }
        return out;
    }

    /** Talents standards (hors talent cache), sans doublon. */
    public static List<PotentialAbility> normal(Pokemon pk) {
        List<PotentialAbility> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (PotentialAbility pa : pool(pk)) {
            if (pa instanceof HiddenAbility) continue;
            String n = pa.getTemplate().getName();
            if (!seen.contains(n)) {
                seen.add(n);
                out.add(pa);
            }
        }
        return out;
    }

    public static PotentialAbility hidden(Pokemon pk) {
        for (PotentialAbility pa : pool(pk)) if (pa instanceof HiddenAbility) return pa;
        return null;
    }

    public static boolean hasHidden(Pokemon pk) {
        PotentialAbility h = hidden(pk);
        return h != null && h.getTemplate().getName().equals(pk.getAbility().getTemplate().getName());
    }

    public static String current(Pokemon pk) {
        return pk.getAbility().getTemplate().getName();
    }

    /** Applique le talent (comme le fait Cobblemon pour la propriete "hiddenability"). */
    public static void apply(Pokemon pk, PotentialAbility pa) {
        AbilityTemplate t = pa.getTemplate();
        pk.updateAbility(t.create(false, pa.getPriority()));
    }

    public static PotentialAbility find(Pokemon pk, String name, boolean hidden) {
        if (hidden) return hidden(pk);
        for (PotentialAbility pa : normal(pk)) if (pa.getTemplate().getName().equalsIgnoreCase(name)) return pa;
        return null;
    }
}
