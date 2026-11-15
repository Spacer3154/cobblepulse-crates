package fr.cobblepulse.crates.config;

import fr.cobblepulse.crates.item.KeyType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Une caisse (fichier caisses/&lt;nom&gt;.json). */
public final class CrateDef {
    public static final List<String> ANIMATIONS = List.of("roulette", "roue", "instantanee");

    /** Nom technique = nom du fichier (sans .json), utilise par /lootbox set &lt;nom&gt;. */
    public transient String id = "";
    /** Nom affiche (codes couleur &, &#RRGGBB). */
    public String nom = "&fCaisse";
    /** Sous-titre de l'apercu. */
    public String description = "";
    /** Objet cle (identifiant). */
    public String cle = KeyType.OBSIDIENNE.itemId();
    public int clesRequises = 1;
    /** roulette (facon CS:GO), roue, instantanee. */
    public String animation = "roulette";
    /** Duree de l'animation en secondes. */
    public double dureeAnimation = 6.0;
    /** Le joueur peut passer l'animation. */
    public boolean ouvertureRapide = true;
    /** Maintenance : apercu possible, ouverture bloquee (sans consommer de cle). */
    public boolean maintenance = false;
    /** Couleur d'accent de l'interface (#RRGGBB). */
    public String couleur = "#B48CF0";
    /** Particules autour de la caisse au repos (vide = aucune). Formats : minecraft:flame, dust #FF0000 1.0 */
    public String particuleAttente = "";
    /** Particules pendant l'ouverture. */
    public String particuleOuverture = "";
    /** Son joue a l'ouverture du conteneur (vide = son du conteneur). */
    public String sonOuverture = "";
    /** Hologramme temporaire de la recompense au-dessus de la caisse. */
    public boolean hologramme = true;
    public List<RewardDef> recompenses = new ArrayList<>();

    public static String cleanId(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_\\-]", "");
    }

    public void normalize() {
        if (nom == null || nom.isBlank()) nom = "&f" + id;
        if (description == null) description = "";
        if (cle == null || cle.isBlank()) cle = KeyType.OBSIDIENNE.itemId();
        cle = cle.trim().toLowerCase(Locale.ROOT);
        clesRequises = Math.max(1, Math.min(64, clesRequises));
        if (animation == null || !ANIMATIONS.contains(animation)) animation = "roulette";
        if (!(dureeAnimation > 0)) dureeAnimation = 6.0;
        dureeAnimation = Math.max(1.0, Math.min(20.0, dureeAnimation));
        if (couleur == null || !couleur.matches("#?[0-9a-fA-F]{6}")) couleur = "#B48CF0";
        if (!couleur.startsWith("#")) couleur = "#" + couleur;
        if (particuleAttente == null) particuleAttente = "";
        if (particuleOuverture == null) particuleOuverture = "";
        if (sonOuverture == null) sonOuverture = "";
        if (recompenses == null) recompenses = new ArrayList<>();
        recompenses.removeIf(r -> r == null);
        Set<String> ids = new HashSet<>();
        for (RewardDef r : recompenses) {
            r.normalize();
            while (!ids.add(r.id)) r.id = r.id + "x";
        }
    }

    public int color() {
        try {
            return Integer.parseInt(couleur.substring(1), 16);
        } catch (Exception e) {
            return 0xB48CF0;
        }
    }

    public double totalWeight() {
        double t = 0;
        for (RewardDef r : recompenses) t += Math.max(0, r.poids);
        return t;
    }

    public RewardDef reward(String rid) {
        for (RewardDef r : recompenses) if (r.id.equals(rid)) return r;
        return null;
    }
}
