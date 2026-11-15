package fr.cobblepulse.crates.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Edition d'une recompense : objet (y compris l'objet tenu), commande, Pokemon precis ou Pokemon aleatoire (pool). */
public final class RewardEditScreen extends EditScreen {
    private final List<String> rarityIds;
    private final List<String> rarityNames;
    private final List<Integer> rarityColors;
    private final List<String> poolIds;
    private final List<String> poolNames;
    private final double otherWeight;
    private List<String> checkErrors = new ArrayList<>();
    private String checkInfo = "";

    public RewardEditScreen(Screen parent, JsonObject reward, JsonObject admin, double otherWeight, Consumer<JsonObject> onSave) {
        super("Récompense", parent, reward, onSave);
        this.otherWeight = otherWeight;
        rarityIds = new ArrayList<>();
        rarityNames = new ArrayList<>();
        rarityColors = new ArrayList<>();
        for (JsonElement e : Ui.arr(Ui.obj(admin, "general"), "raretes")) {
            JsonObject r = e.getAsJsonObject();
            rarityIds.add(Ui.str(r, "id", "commun"));
            rarityNames.add(Ui.str(r, "nom", "Commun"));
            int c;
            try {
                c = Integer.parseInt(Ui.str(r, "couleur", "#B0B8C4").replace("#", ""), 16);
            } catch (Exception ex) {
                c = 0xB0B8C4;
            }
            rarityColors.add(c);
        }
        if (rarityIds.isEmpty()) {
            rarityIds.add("commun");
            rarityNames.add("Commun");
            rarityColors.add(0xB0B8C4);
        }
        poolIds = new ArrayList<>();
        poolNames = new ArrayList<>();
        for (JsonElement e : Ui.arr(admin, "pools")) {
            JsonObject p = e.getAsJsonObject();
            poolIds.add(Ui.str(p, "id", ""));
            poolNames.add(Ui.str(p, "name", "") + " (" + Ui.lng(p, "count", 0) + ")");
        }
        if (poolIds.isEmpty()) {
            poolIds.add("all");
            poolNames.add("all");
        }
    }

    @Override
    protected int previewWidth() {
        return 116;
    }

    @Override
    protected void fields(Form f) {
        f.onChange(() -> {});
        f.choice("Type", obj, "type", ClientData.REWARD_TYPES, ClientData.REWARD_TYPE_NAMES, "Maj + clic pour revenir en arrière");
        String type = Ui.str(obj, "type", "objet");
        f.onChange(() -> {
            if (!type.equals(Ui.str(obj, "type", "objet"))) {
                String t = Ui.str(obj, "type", "objet");
                JsonObject fresh = ClientData.newReward(t);
                for (String k : List.of("objet", "commandes", "pokemon", "aleatoire")) {
                    if (!obj.has(k) && fresh.has(k)) obj.add(k, fresh.get(k));
                }
                minecraft.execute(this::rebuild);
            }
        });
        f.section("Tirage");
        f.decimal("Poids", obj, "poids", "Poids relatif : chance = poids / somme des poids de la caisse (0 = jamais tiré)");
        f.choice("Rareté", obj, "rarete", rarityIds, rarityNames, "Couleur, son et particules à l'obtention (onglet Réglages)");
        f.bool("Annonce serveur", obj, "annonce", "Annonce à tout le serveur quand un joueur obtient ce lot");
        f.section("Affichage");
        f.text("Nom affiché", obj, "nom", "Vide = automatique ; codes couleur & et &#RRGGBB acceptés");
        EditBox icon = f.text("Icône", obj, "icone", "Objet affiché dans l'aperçu (vide = automatique ; Poké Ball pour un Pokémon aléatoire)");
        f.suggest(icon, ClientData::items);
        EditBox desc = f.free("Description", joined("description", " | "), "Lignes affichées au survol, séparées par |");
        desc.setResponder(v -> {
            JsonArray a = new JsonArray();
            for (String s : v.split("\\|")) if (!s.isBlank()) a.add(s.trim());
            obj.add("description", a);
        });
        switch (type) {
            case "objet" -> item(f);
            case "pokemon" -> pokemon(f);
            case "pokemon_aleatoire" -> random(f);
            default -> {
                f.section("Commandes console");
                EditBox cmds = f.free("Commandes", joined("commandes", "; "), "Séparées par ; — %player% = pseudo, %uuid% = UUID. Sans le /. Ex. : cobbledollars give %player% 500");
                cmds.setResponder(v -> {
                    JsonArray a = new JsonArray();
                    for (String c : v.split(";")) if (!c.isBlank()) a.add(c.trim());
                    obj.add("commandes", a);
                });
            }
        }
    }

    private String joined(String key, String sep) {
        List<String> out = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) for (JsonElement e : obj.getAsJsonArray(key)) out.add(e.getAsString());
        return String.join(sep, out);
    }

    private void item(Form f) {
        f.section("Objet");
        f.button("Objet en main", "Prendre l'objet tenu", () -> {
            if (minecraft.player == null) return;
            ItemStack hand = minecraft.player.getMainHandItem();
            if (hand.isEmpty()) {
                message = "Tenez l'objet (avec ses enchantements, son nom...) dans la main principale.";
                messageColor = Ui.ERROR;
                return;
            }
            JsonElement e = Ui.encodeItem(hand);
            if (e != null) {
                obj.add("objet", e);
                message = "Objet copié : " + hand.getHoverName().getString() + " ×" + hand.getCount();
                messageColor = Ui.OK;
                minecraft.execute(this::rebuild);
            }
        }, "Copie l'objet exact (composants, enchantements, nom personnalisé) tenu dans la main principale.");
        JsonObject o = obj.has("objet") && obj.get("objet").isJsonObject() ? obj.getAsJsonObject("objet") : new JsonObject();
        if (!obj.has("objet") || !obj.get("objet").isJsonObject()) obj.add("objet", o);
        boolean custom = o.has("components") && o.getAsJsonObject("components").size() > 0;
        EditBox id = f.text("Identifiant", o, "id", custom ? "Objet personnalisé (composants conservés)" : "ex. cobblemon:rare_candy, minecraft:diamond, un objet de mod...");
        f.suggest(id, ClientData::items);
        f.integer("Quantité", o, "count", 1, 6400, "Peut dépasser 64 : l'objet est donné en plusieurs piles.");
    }

    private void random(Form f) {
        JsonObject a = Ui.obj(obj, "aleatoire");
        f.section("Pokémon aléatoire");
        f.choice("Pool", a, "pool", poolIds, poolNames, "Fichiers config/cobblepulse_crates/pools/*.json (nombre d'espèces entre parenthèses)");
        f.integer("Niveau min.", a, "niveau_min", 1, 100, null);
        f.integer("Niveau max.", a, "niveau_max", 1, 100, null);
        f.decimal("Chance chromatique (%)", a, "chance_shiny", "De 0 à 100");
        f.integer("IV parfaits garantis", a, "ivs_parfaits", 0, 6, "Nombre de statistiques (tirées au hasard) garanties à 31");
        f.choice("Formes", a, "formes", List.of("base", "aleatoire"), List.of("Forme normale", "Forme aléatoire (régionales, variantes)"),
                "Méga, Dynamax et formes de combat sont toujours exclues");
    }

    private void pokemon(Form f) {
        JsonObject m = Ui.obj(obj, "pokemon");
        f.section("Pokémon");
        EditBox sp = f.text("Espèce", m, "species", "Nom ou identifiant complet (fakemons : monpack:volt_mon)");
        f.suggest(sp, ClientData::species);
        f.text("Forme / aspects", m, "form", "Séparés par des virgules : alolan, galarian, hisuian, wash...");
        f.integer("Niveau", m, "level", 1, 100, null);
        f.bool("Chromatique", m, "shiny", null);
        List<String> nat = new ArrayList<>(List.of(""));
        nat.addAll(ClientData.NATURES);
        List<String> natFr = new ArrayList<>(List.of("Aléatoire"));
        natFr.addAll(ClientData.NATURES_FR);
        f.choice("Nature", m, "nature", nat, natFr, "Maj + clic pour reculer");
        f.text("Talent", m, "ability", "Vide = talent normal aléatoire (ex. intimidate)");
        f.choice("Genre", m, "gender", List.of("", "male", "female", "genderless"), List.of("Aléatoire", "Mâle", "Femelle", "Asexué"), null);
        EditBox held = f.text("Objet tenu", m, "held_item", "ex. cobblemon:leftovers");
        f.suggest(held, ClientData::items);
        EditBox ball = f.text("Poké Ball", m, "ball", "ex. cobblemon:ultra_ball (vide = Poké Ball)");
        f.suggest(ball, ClientData::items);
        f.text("Surnom", m, "nickname", null);
        f.section("IV / EV (PV / Atq / Déf / Atq.Spé / Déf.Spé / Vit)");
        f.intRow("IV (-1 = aléatoire)", Ui.obj(m, "ivs"), ClientData.STATS, -1, 31, "De 0 à 31, ou -1 pour un tirage aléatoire");
        f.intRow("EV", Ui.obj(m, "evs"), ClientData.STATS, 0, 252, "De 0 à 252 (total conseillé ≤ 510)");
        List<EditBox> moves = f.textRow("Capacités", m, "moves", 4, "Vide = capacités normales du niveau");
        for (EditBox b : moves) f.suggest(b, ClientData::moves);
        f.button("Vérification", "Vérifier sur le serveur", () -> {
            JsonObject a = new JsonObject();
            a.add("spec", m.deepCopy());
            Ui.send("admin_check_pokemon", a);
            checkInfo = "Vérification…";
            checkErrors = new ArrayList<>();
        }, "Crée le Pokémon côté serveur pour valider l'espèce, la forme, le talent, les capacités et les objets.");
    }

    /** Reponse du serveur a "Verifier". */
    public void onCheck(JsonObject r) {
        checkErrors = new ArrayList<>();
        for (JsonElement e : Ui.arr(r, "errors")) checkErrors.add(e.getAsString());
        checkInfo = Ui.bool(r, "ok", false) ? "Valide ✔ " + Ui.comp(Ui.str(r, "name", "")).getString() : "Erreurs :";
    }

    @Override
    protected void extra(GuiGraphics g, int mouseX, int mouseY) {
        int x = px + pw - 110;
        int y = py + 26;
        String rid = Ui.str(obj, "rarete", "commun");
        int idx = Math.max(0, rarityIds.indexOf(rid));
        int rc = rarityColors.get(Math.min(idx, rarityColors.size() - 1));
        Ui.panel(g, x, y, 100, 100, Ui.CARD, Ui.argb(0xFF, rc));
        for (int i = 0; i < 20; i++) g.fill(x + 1, y + 98 - i, x + 99, y + 99 - i, Ui.argb((int) (0x60 * (1 - i / 20f)), rc));
        Ui.item(g, ClientData.rewardIcon(obj), x + 18, y + 14, 4f, true);
        String label = ClientData.rewardLabel(obj);
        int ly = y + 106;
        for (FormattedCharSequence l : Ui.font().split(Component.literal(label), 104)) {
            if (ly > y + 136) break;
            g.drawString(Ui.font(), l, x - 2, ly, Ui.argb(0xFF, rc), false);
            ly += 10;
        }
        double w = Math.max(0, Ui.dbl(obj, "poids", 0));
        double total = otherWeight + w;
        String pct = total <= 0 ? "0 %" : String.format(Locale.FRANCE, "%.2f %%", w / total * 100);
        Ui.text(g, "Chance : " + pct, x - 2, ly + 2, Ui.TEXT);
        Ui.text(g, rarityNames.get(Math.min(idx, rarityNames.size() - 1)), x - 2, ly + 13, Ui.argb(0xFF, rc));
        if (!checkInfo.isEmpty()) {
            int cy = ly + 30;
            for (FormattedCharSequence l : Ui.font().split(Component.literal(checkInfo), 108)) {
                g.drawString(Ui.font(), l, x - 4, cy, checkErrors.isEmpty() ? Ui.OK : Ui.ERROR, false);
                cy += 10;
            }
            for (String e : checkErrors) {
                for (FormattedCharSequence l : Ui.font().split(Component.literal("• " + e), 108)) {
                    if (cy > py + ph - 30) break;
                    g.drawString(Ui.font(), l, x - 4, cy, Ui.ERROR, false);
                    cy += 10;
                }
            }
        }
    }
}
