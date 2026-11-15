package fr.cobblepulse.crates.crate;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.config.PoolDef;
import fr.cobblepulse.crates.pokemon.Names;
import fr.cobblepulse.crates.pokemon.SpeciesLookup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolution des pools d'especes (pools/*.json), avec cache invalide a chaque modification de configuration. */
public final class Pools {
    private static final Map<String, List<Species>> CACHE = new HashMap<>();
    private static int cacheRev = -1;

    private Pools() {}

    public static List<Species> species(String poolId) {
        if (cacheRev != Cfg.revision) {
            CACHE.clear();
            cacheRev = Cfg.revision;
        }
        String id = poolId == null ? "" : poolId.trim().toLowerCase(Locale.ROOT);
        return CACHE.computeIfAbsent(id, k -> {
            PoolDef def = Cfg.POOLS.get(k);
            return def == null ? List.of() : resolve(def);
        });
    }

    private static Set<String> labels(List<String> l) {
        Set<String> out = new HashSet<>();
        for (String s : l) if (s != null && !s.isBlank()) out.add(Names.labelId(s));
        return out;
    }

    public static List<Species> resolve(PoolDef def) {
        Set<String> inc = labels(def.etiquettes);
        Set<String> exc = labels(def.exclureEtiquettes);
        Set<String> ns = new HashSet<>();
        for (String s : def.espacesDeNoms) if (s != null && !s.isBlank()) ns.add(s.trim().toLowerCase(Locale.ROOT));
        Set<Species> excluded = new HashSet<>();
        for (String s : def.exclure) {
            Species sp = SpeciesLookup.resolve(s);
            if (sp != null) excluded.add(sp);
        }
        List<Species> out = new ArrayList<>();
        Set<Species> seen = new HashSet<>();
        for (Species s : PokemonSpecies.getSpecies()) {
            if (def.implementeesSeulement && !s.getImplemented()) continue;
            String namespace = s.getResourceIdentifier().getNamespace();
            boolean in = def.toutesEspeces;
            if (!in && !inc.isEmpty()) {
                for (String l : s.getLabels()) {
                    if (inc.contains(l.toLowerCase(Locale.ROOT))) {
                        in = true;
                        break;
                    }
                }
            }
            if (!in && !ns.isEmpty()) {
                in = ns.contains(namespace) || (ns.contains("*") && !"cobblemon".equals(namespace));
            }
            if (!in) continue;
            boolean out1 = excluded.contains(s);
            if (!out1 && !exc.isEmpty()) {
                for (String l : s.getLabels()) {
                    if (exc.contains(l.toLowerCase(Locale.ROOT))) {
                        out1 = true;
                        break;
                    }
                }
            }
            if (!out1 && seen.add(s)) out.add(s);
        }
        for (String s : def.especes) {
            Species sp = SpeciesLookup.resolve(s);
            if (sp != null && !excluded.contains(sp) && seen.add(sp)) out.add(sp);
        }
        return out;
    }
}
