package fr.cobblepulse.crates.crate;

import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.config.Locations;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Index des conteneurs lies aux caisses (multi-mondes). */
public final class Links {
    private static final Map<String, Locations.Loc> BY_KEY = new HashMap<>();
    private static int builtFor = -1;

    private Links() {}

    public static String dim(Level level) {
        return level.dimension().location().toString();
    }

    public static String dim(ResourceKey<Level> key) {
        return key.location().toString();
    }

    private static void ensure() {
        if (builtFor != Cfg.revision) rebuild();
    }

    public static void rebuild() {
        BY_KEY.clear();
        for (Locations.Loc l : Cfg.locations.emplacements) BY_KEY.put(l.key(), l);
        builtFor = Cfg.revision;
    }

    /** Caisse liee a ce bloc (null si aucune). */
    public static String crateAt(Level level, BlockPos pos) {
        ensure();
        Locations.Loc l = BY_KEY.get(Locations.Loc.key(dim(level), pos.getX(), pos.getY(), pos.getZ()));
        return l == null ? null : l.caisse;
    }

    public static String crateAt(String dim, BlockPos pos) {
        ensure();
        Locations.Loc l = BY_KEY.get(Locations.Loc.key(dim, pos.getX(), pos.getY(), pos.getZ()));
        return l == null ? null : l.caisse;
    }

    public static void link(Level level, BlockPos pos, String crate) {
        ensure();
        unlinkRaw(dim(level), pos);
        Locations.Loc l = new Locations.Loc(dim(level), pos.getX(), pos.getY(), pos.getZ(), crate);
        Cfg.locations.emplacements.add(l);
        Cfg.saveLocations();
        rebuild();
    }

    private static boolean unlinkRaw(String dim, BlockPos pos) {
        return Cfg.locations.emplacements.removeIf(l -> l.monde.equals(dim) && l.x == pos.getX() && l.y == pos.getY() && l.z == pos.getZ());
    }

    public static boolean unlink(String dim, BlockPos pos) {
        boolean r = unlinkRaw(dim, pos);
        if (r) {
            Cfg.saveLocations();
            rebuild();
        }
        return r;
    }

    /** Delie tous les conteneurs d'une caisse ; renvoie le nombre. */
    public static int unlinkAll(String crate) {
        int before = Cfg.locations.emplacements.size();
        Cfg.locations.emplacements.removeIf(l -> l.caisse.equalsIgnoreCase(crate));
        int n = before - Cfg.locations.emplacements.size();
        if (n > 0) {
            Cfg.saveLocations();
            rebuild();
        }
        return n;
    }

    public static List<Locations.Loc> forCrate(String crate) {
        List<Locations.Loc> out = new ArrayList<>();
        for (Locations.Loc l : Cfg.locations.emplacements) if (l.caisse.equalsIgnoreCase(crate)) out.add(l);
        return out;
    }

    public static List<Locations.Loc> inDimension(String dim) {
        List<Locations.Loc> out = new ArrayList<>();
        for (Locations.Loc l : Cfg.locations.emplacements) if (l.monde.equals(dim)) out.add(l);
        return out;
    }

    public static List<Locations.Loc> all() {
        return Cfg.locations.emplacements;
    }

    /** Positions connues du client (mises a jour par le reseau) : annulent le minage et l'usage cote client. */
    public static final java.util.Set<Long> CLIENT_POSITIONS = java.util.concurrent.ConcurrentHashMap.newKeySet();
}
