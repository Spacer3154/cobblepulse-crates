package fr.cobblepulse.crates.config;

import java.util.ArrayList;
import java.util.List;

/** Conteneurs lies aux caisses (emplacements.json). */
public final class Locations {
    public List<Loc> emplacements = new ArrayList<>();

    public static final class Loc {
        public String monde = "minecraft:overworld";
        public int x;
        public int y;
        public int z;
        public String caisse = "";

        public Loc() {}

        public Loc(String monde, int x, int y, int z, String caisse) {
            this.monde = monde;
            this.x = x;
            this.y = y;
            this.z = z;
            this.caisse = caisse;
        }

        public String key() {
            return key(monde, x, y, z);
        }

        public static String key(String monde, int x, int y, int z) {
            return monde + "|" + x + "|" + y + "|" + z;
        }
    }
}
