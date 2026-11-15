package fr.cobblepulse.crates.config;

import com.google.gson.JsonElement;
import fr.cobblepulse.crates.pokemon.MonSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Une recompense de caisse : objet, commande(s), Pokemon precis ou Pokemon aleatoire tire dans un pool. */
public final class RewardDef {
    public static final List<String> TYPES = List.of("objet", "commande", "pokemon", "pokemon_aleatoire");

    /** Identifiant stable (journal, editeur). */
    public String id = "";
    public String type = "objet";
    /** Nom affiche (codes couleur &). Vide = automatique. */
    public String nom = "";
    /** Objet affiche (types commande et Pokemon aleatoire). Vide = icone par defaut. */
    public String icone = "";
    /** Poids relatif : chance = poids / somme des poids de la caisse. */
    public double poids = 10;
    public String rarete = "commun";
    /** Annonce a tout le serveur quand un joueur obtient ce lot. */
    public boolean annonce = false;
    /** Type objet : objet au format Minecraft ({"id": ..., "count": ..., "components": ...}). */
    public JsonElement objet;
    /** Type commande : commandes console (%player%, %uuid%). */
    public List<String> commandes = new ArrayList<>();
    /** Texte libre affiche sous le nom (infobulle). */
    public List<String> description = new ArrayList<>();
    public MonSpec pokemon;
    public RandomMon aleatoire;

    /** Pokemon aleatoire. */
    public static final class RandomMon {
        /** Nom du fichier pools/&lt;pool&gt;.json (all, legendary, ultra_beasts, fakemons...). */
        public String pool = "all";
        public int niveauMin = 5;
        public int niveauMax = 30;
        /** Chance d'etre chromatique, en pourcentage (0 a 100). */
        public double chanceShiny = 0;
        /** Nombre d'IV garantis a 31 (0 a 6), sur des statistiques tirees au hasard. */
        public int ivsParfaits = 0;
        /** base = forme normale ; aleatoire = forme tiree parmi les formes autorisees (regionales, variantes). */
        public String formes = "base";

        public void normalize() {
            if (pool == null || pool.isBlank()) pool = "all";
            pool = pool.trim().toLowerCase(Locale.ROOT).replace(".json", "");
            niveauMin = Math.max(1, Math.min(100, niveauMin));
            niveauMax = Math.max(niveauMin, Math.min(100, niveauMax));
            chanceShiny = Math.max(0, Math.min(100, chanceShiny));
            ivsParfaits = Math.max(0, Math.min(6, ivsParfaits));
            if (!"aleatoire".equals(formes)) formes = "base";
        }
    }

    public String type() {
        return type == null ? "objet" : type;
    }

    public void normalize() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString().substring(0, 8);
        if (type == null || !TYPES.contains(type)) type = "objet";
        if (nom == null) nom = "";
        if (icone == null) icone = "";
        if (rarete == null || rarete.isBlank()) rarete = "commun";
        if (!(poids >= 0) || Double.isInfinite(poids)) poids = 0;
        if (commandes == null) commandes = new ArrayList<>();
        commandes.removeIf(c -> c == null || c.isBlank());
        if (description == null) description = new ArrayList<>();
        if ("pokemon".equals(type)) {
            if (pokemon == null) pokemon = new MonSpec();
            pokemon.normalize();
        }
        if ("pokemon_aleatoire".equals(type)) {
            if (aleatoire == null) aleatoire = new RandomMon();
            aleatoire.normalize();
        }
    }
}
