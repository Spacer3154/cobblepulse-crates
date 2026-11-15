package fr.cobblepulse.crates.config;

import java.util.ArrayList;
import java.util.List;

/** Menu Voeu ADN (voeu_adn.json) : prix et restrictions. */
public final class BuilderConfig {
    public boolean actif = true;
    /** Objet utilise comme monnaie. */
    public String monnaie = "cobblepulse_crates:voeu_adn";
    public Prix prix = new Prix();
    /** Les Pokemon du PC peuvent etre modifies (sinon equipe seulement). */
    public boolean autoriserPc = true;
    /** Un Pokemon chromatique peut redevenir normal (meme prix). */
    public boolean shinyReversible = true;
    /** Formes jamais proposees (nom de forme ou aspect, sans tenir compte des majuscules). */
    public List<String> formesInterdites = new ArrayList<>(List.of("mega", "mega_x", "mega_y", "megax", "megay", "gmax", "gigantamax",
            "dynamax", "eternamax", "primal", "ultra", "zen", "galar_zen", "galarzen", "blade", "school", "busted", "complete", "pirouette",
            "hangry", "noice", "gulping", "gorging", "stellar", "terastal", "crowned", "sunshine", "hero", "white_striped_eternal"));
    /** Especes que le menu refuse de modifier. */
    public List<String> especesInterdites = new ArrayList<>();
    /** Refuse les formes qui exigent un objet tenu ou une capacite (plaques d'Arceus, Giratina Originel...). */
    public boolean exclureFormesAObjet = true;

    public static final class Prix {
        public int shiny = 5;
        public int talentCache = 3;
        public int forme = 2;
        public int talentNormal = 1;
        public int nature = 1;
        public int ivParStat = 1;
        public int evParSession = 1;
    }

    public void normalize() {
        if (monnaie == null || monnaie.isBlank()) monnaie = "cobblepulse_crates:voeu_adn";
        if (prix == null) prix = new Prix();
        // Cout minimum obligatoire : 1 Voeu ADN par action
        prix.shiny = Math.max(1, prix.shiny);
        prix.talentCache = Math.max(1, prix.talentCache);
        prix.forme = Math.max(1, prix.forme);
        prix.talentNormal = Math.max(1, prix.talentNormal);
        prix.nature = Math.max(1, prix.nature);
        prix.ivParStat = Math.max(1, prix.ivParStat);
        prix.evParSession = Math.max(1, prix.evParSession);
        if (formesInterdites == null) formesInterdites = new ArrayList<>();
        if (especesInterdites == null) especesInterdites = new ArrayList<>();
    }
}
