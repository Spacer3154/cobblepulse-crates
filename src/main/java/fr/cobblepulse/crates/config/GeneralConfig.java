package fr.cobblepulse.crates.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reglages generaux (config.json). */
public final class GeneralConfig {
    /** Maintenance de toutes les caisses. */
    public boolean maintenanceGlobale = false;
    /** Distance maximale (blocs) entre le joueur et la caisse pour l'ouvrir. */
    public double distanceMax = 8.0;
    public boolean annonces = true;
    /** Son "tic" de la roulette / roue (cote client). */
    public String sonTic = "minecraft:block.note_block.hat";
    public Hologramme hologramme = new Hologramme();
    public Particules particules = new Particules();
    /** Journal des ouvertures (config/cobblepulse_crates/journal/). */
    public boolean journal = true;
    public List<Rarete> raretes = new ArrayList<>();

    public static final class Hologramme {
        public boolean actif = true;
        public double dureeSecondes = 5.0;
        /** Hauteur au-dessus du bloc. */
        public double hauteur = 1.25;
        public double echelleObjet = 0.6;
    }

    public static final class Particules {
        /** Particules d'ambiance autour des caisses. */
        public boolean ambiance = true;
        public int intervalleTicks = 12;
        /** Les particules d'ambiance ne sont envoyees que si un joueur est a moins de N blocs. */
        public double rayonJoueurs = 24.0;
    }

    /** Rarete : couleur, son et particules a l'obtention. */
    public static final class Rarete {
        public String id = "commun";
        public String nom = "Commun";
        public String couleur = "#B0B8C4";
        public String son = "minecraft:entity.experience_orb.pickup";
        public double volume = 0.8;
        public double tonalite = 1.0;
        public String particule = "minecraft:happy_villager";
        public int quantite = 12;

        public Rarete() {}

        public Rarete(String id, String nom, String couleur, String son, double volume, double tonalite, String particule, int quantite) {
            this.id = id;
            this.nom = nom;
            this.couleur = couleur;
            this.son = son;
            this.volume = volume;
            this.tonalite = tonalite;
            this.particule = particule;
            this.quantite = quantite;
        }

        public int color() {
            try {
                return Integer.parseInt(couleur.replace("#", ""), 16);
            } catch (Exception e) {
                return 0xB0B8C4;
            }
        }
    }

    public static List<Rarete> defaultRarities() {
        List<Rarete> l = new ArrayList<>();
        l.add(new Rarete("commun", "Commun", "#B0B8C4", "minecraft:entity.experience_orb.pickup", 0.8, 1.0, "minecraft:happy_villager", 10));
        l.add(new Rarete("peu_commun", "Peu commun", "#6BE07A", "minecraft:entity.player.levelup", 0.6, 1.4, "minecraft:composter", 16));
        l.add(new Rarete("rare", "Rare", "#5FC8FF", "minecraft:block.amethyst_block.resonate", 1.0, 1.2, "minecraft:glow", 24));
        l.add(new Rarete("epique", "Épique", "#C48CFF", "minecraft:ui.toast.challenge_complete", 0.7, 1.3, "minecraft:witch", 40));
        l.add(new Rarete("legendaire", "Légendaire", "#FFD24A", "minecraft:ui.toast.challenge_complete", 1.0, 0.9, "minecraft:totem_of_undying", 80));
        return l;
    }

    public Rarete rarity(String id) {
        String k = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (Rarete r : raretes) if (r.id.equalsIgnoreCase(k)) return r;
        return raretes.isEmpty() ? new Rarete() : raretes.get(0);
    }

    public void normalize() {
        if (!(distanceMax > 0)) distanceMax = 8.0;
        if (sonTic == null) sonTic = "";
        if (hologramme == null) hologramme = new Hologramme();
        hologramme.dureeSecondes = Math.max(0.5, Math.min(60, hologramme.dureeSecondes));
        hologramme.echelleObjet = Math.max(0.1, Math.min(4, hologramme.echelleObjet));
        if (particules == null) particules = new Particules();
        particules.intervalleTicks = Math.max(2, particules.intervalleTicks);
        if (raretes == null || raretes.isEmpty()) raretes = defaultRarities();
        for (Rarete r : raretes) {
            if (r.id == null || r.id.isBlank()) r.id = "commun";
            r.id = r.id.trim().toLowerCase(Locale.ROOT);
            if (r.nom == null) r.nom = r.id;
            if (r.couleur == null || !r.couleur.matches("#?[0-9a-fA-F]{6}")) r.couleur = "#B0B8C4";
            if (!r.couleur.startsWith("#")) r.couleur = "#" + r.couleur;
            if (r.son == null) r.son = "";
            if (r.particule == null) r.particule = "";
            r.quantite = Math.max(0, Math.min(500, r.quantite));
        }
    }
}
