package fr.cobblepulse.crates.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Pool d'especes pour les recompenses "Pokemon aleatoire" (fichier pools/&lt;nom&gt;.json).
 * Une espece est retenue si elle correspond a l'un des criteres d'inclusion et a aucun critere d'exclusion.
 */
public final class PoolDef {
    public transient String id = "";
    public String nom = "";
    /** Toutes les especes. */
    public boolean toutesEspeces = false;
    /** Etiquettes Cobblemon (legendary, mythical, ultra_beast, paradox, starter...). */
    public List<String> etiquettes = new ArrayList<>();
    /** Espaces de noms des especes (datapacks de fakemons) ; "*" = tout sauf cobblemon. */
    public List<String> espacesDeNoms = new ArrayList<>();
    /** Especes ajoutees explicitement (nom ou identifiant complet). */
    public List<String> especes = new ArrayList<>();
    public List<String> exclure = new ArrayList<>();
    public List<String> exclureEtiquettes = new ArrayList<>();
    /** Ignore les especes non implementees (sans modele) de Cobblemon. */
    public boolean implementeesSeulement = true;

    public void normalize() {
        if (nom == null || nom.isBlank()) nom = id;
        if (etiquettes == null) etiquettes = new ArrayList<>();
        if (espacesDeNoms == null) espacesDeNoms = new ArrayList<>();
        if (especes == null) especes = new ArrayList<>();
        if (exclure == null) exclure = new ArrayList<>();
        if (exclureEtiquettes == null) exclureEtiquettes = new ArrayList<>();
    }
}
