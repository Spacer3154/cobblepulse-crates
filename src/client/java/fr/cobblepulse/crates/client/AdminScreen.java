package fr.cobblepulse.crates.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Editeur graphique (/lootbox editor) : caisses (reglages, recompenses, conteneurs lies), reglages generaux et raretes,
 * prix du Voeu ADN. Les modifications sont envoyees au serveur, verifiees puis ecrites dans les fichiers JSON.
 */
public final class AdminScreen extends Screen implements CratesClient.Toaster {
    private static final String[] TABS = {"Caisses", "Réglages", "Vœu ADN"};
    private static final String[] SUBS = {"Réglages", "Récompenses", "Conteneurs"};
    private static final int ROW = 24;

    private JsonObject data;
    private final Map<String, JsonObject> work = new LinkedHashMap<>();
    private final Set<String> dirty = new HashSet<>();
    private JsonObject general;
    private JsonObject builder;
    private boolean generalDirty;
    private boolean builderDirty;
    private int tab;
    private int sub = 1;
    private String selected;
    private String lastSaved;
    private Form form;
    private double formScroll;
    private double listScroll;
    private double rewardScroll;
    private EditBox newName;
    private int px, py, pw, ph;
    private String toast = "";
    private boolean toastOk;
    private long toastUntil;
    private final List<String> warnings = new ArrayList<>();
    private int savedGuiScale = -1;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    public AdminScreen(JsonObject data) {
        super(Component.literal("Éditeur des caisses"));
        load(data, true);
    }

    private void load(JsonObject d, boolean all) {
        this.data = d;
        for (JsonElement e : Ui.arr(d, "crates")) {
            JsonObject c = e.getAsJsonObject();
            String id = Ui.str(c, "_id", "");
            if (all || !dirty.contains(id)) work.put(id, c.deepCopy());
        }
        work.keySet().removeIf(id -> {
            for (JsonElement e : Ui.arr(d, "crates")) if (Ui.str(e.getAsJsonObject(), "_id", "").equals(id)) return false;
            return !dirty.contains(id);
        });
        if (all || !generalDirty) general = Ui.obj(d, "general").deepCopy();
        if (all || !builderDirty) builder = Ui.obj(d, "builder").deepCopy();
        if (selected == null || !work.containsKey(selected)) selected = work.isEmpty() ? null : work.keySet().iterator().next();
    }

    public void refreshData(JsonObject d) {
        load(d, false);
        if (minecraft != null) rebuild();
    }

    public void onResult(JsonObject r) {
        boolean ok = Ui.bool(r, "ok", false);
        if (ok && lastSaved != null) {
            if (lastSaved.equals("#general")) generalDirty = false;
            else if (lastSaved.equals("#builder")) builderDirty = false;
            else dirty.remove(lastSaved);
        }
        warnings.clear();
        for (JsonElement e : Ui.arr(r, "warnings")) warnings.add(e.getAsString());
        toast(ok, Ui.str(r, "message", ""));
    }

    @Override
    public void toast(boolean ok, String message) {
        toast = message;
        toastOk = ok;
        toastUntil = System.currentTimeMillis() + 6000;
    }

    private boolean perm(String k) {
        return Ui.bool(Ui.obj(data, "perms"), k, false);
    }

    // ------------------------------------------------------------------ Echelle d'interface

    private void fitGuiScale() {
        com.mojang.blaze3d.platform.Window win = minecraft.getWindow();
        int cur = (int) win.getGuiScale();
        int s = cur;
        while (s > 1 && (win.getWidth() / s < 600 || win.getHeight() / s < 320)) s--;
        if (s != cur) {
            if (savedGuiScale < 0) savedGuiScale = cur;
            win.setGuiScale(s);
            this.width = win.getGuiScaledWidth();
            this.height = win.getGuiScaledHeight();
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (savedGuiScale > 0) {
            minecraft.getWindow().setGuiScale(savedGuiScale);
            savedGuiScale = -1;
        }
    }

    @Override
    protected void init() {
        fitGuiScale();
        pw = Math.min(width - 12, 720);
        ph = height - 12;
        px = (width - pw) / 2;
        py = 6;
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ Widgets

    private int listW() {
        return 168;
    }

    private void rebuild() {
        if (form != null) formScroll = form.scroll();
        clearWidgets();
        form = null;
        newName = null;
        int ty = py + 4;
        for (int i = 0; i < TABS.length; i++) {
            int idx = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(TABS[i]), x -> {
                tab = idx;
                formScroll = 0;
                rebuild();
            }).bounds(px + 132 + i * 80, ty, 76, 18).build());
            b.active = tab != i;
        }
        boolean gm = Ui.bool(general, "maintenance_globale", false);
        Button gmb = addRenderableWidget(Button.builder(Component.literal(gm ? "§cMaint. globale : OUI" : "Maint. globale : non"), x -> {
            JsonObject a = new JsonObject();
            a.addProperty("id", "*");
            Ui.send("admin_toggle", a);
        }).bounds(px + pw - 204, ty, 126, 18).build());
        gmb.setTooltip(Tooltip.create(Component.literal("Maintenance de toutes les caisses : aperçu possible, ouverture bloquée")));
        gmb.active = perm("toggle");
        addRenderableWidget(Button.builder(Component.literal("Recharger"), x -> Ui.send("admin_reload")).bounds(px + pw - 74, ty, 68, 18).build())
                .setTooltip(Tooltip.create(Component.literal("Relit les fichiers JSON (les modifications non enregistrées sont perdues)")));
        int top = py + 28;
        int bottom = py + ph - 6;
        if (tab == 0) {
            int lx = px + 6;
            newName = addRenderableWidget(new EditBox(font, lx, bottom - 20, listW() - 54, 18, Component.literal("nom")));
            newName.setHint(Component.literal("nouvelle_caisse").withStyle(ChatFormatting.DARK_GRAY));
            newName.setMaxLength(40);
            addRenderableWidget(Button.builder(Component.literal("Créer"), x -> {
                String n = newName.getValue().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                if (n.isEmpty()) return;
                JsonObject a = new JsonObject();
                a.addProperty("id", n);
                Ui.send("admin_new_crate", a);
                selected = n;
            }).bounds(lx + listW() - 50, bottom - 20, 50, 18).build());
            JsonObject c = selected == null ? null : work.get(selected);
            int rx = px + listW() + 14;
            int rw = pw - listW() - 20;
            if (c != null) {
                for (int i = 0; i < SUBS.length; i++) {
                    int idx = i;
                    String label = SUBS[i] + (i == 1 ? " (" + Ui.arr(c, "recompenses").size() + ")" : i == 2 ? " (" + Ui.arr(c, "_links").size() + ")" : "");
                    Button b = addRenderableWidget(Button.builder(Component.literal(label), x -> {
                        sub = idx;
                        formScroll = 0;
                        rebuild();
                    }).bounds(rx + i * 104, top + 2, 100, 18).build());
                    b.active = sub != i;
                }
                Button save = addRenderableWidget(Button.builder(Component.literal(dirty.contains(selected) ? "§eEnregistrer *" : "Enregistrer"), x -> saveCrate())
                        .bounds(px + pw - 178, bottom - 20, 86, 18).build());
                save.active = perm("editor");
                Button del = addRenderableWidget(Button.builder(Component.literal("§cSupprimer"), x -> {
                    if (!Screen.hasShiftDown()) {
                        toast(false, "Maj + clic sur « Supprimer » pour confirmer la suppression de " + selected + ".");
                        return;
                    }
                    JsonObject a = new JsonObject();
                    a.addProperty("id", selected);
                    lastSaved = selected;
                    dirty.remove(selected);
                    Ui.send("admin_delete_crate", a);
                }).bounds(px + pw - 88, bottom - 20, 82, 18).build());
                del.setTooltip(Tooltip.create(Component.literal("Maj + clic : supprime la caisse et délie ses conteneurs (le fichier est renommé en .supprime)")));
                if (sub == 0) {
                    form = new Form(this::addRenderableWidget, rx, top + 26, rw, bottom - top - 54).onChange(() -> markDirty());
                    crateFields(form, c);
                    form.setScroll(formScroll);
                    form.layout();
                } else if (sub == 1) {
                    addRenderableWidget(Button.builder(Component.literal("+ Ajouter"), x -> addReward("objet")).bounds(rx, bottom - 20, 70, 18).build())
                            .setTooltip(Tooltip.create(Component.literal("Nouvelle récompense (le type se choisit dans l'éditeur : objet, commande, Pokémon, Pokémon aléatoire)")));
                    addRenderableWidget(Button.builder(Component.literal("+ Objet en main"), x -> addReward("main")).bounds(rx + 74, bottom - 20, 96, 18).build())
                            .setTooltip(Tooltip.create(Component.literal("Ajoute l'objet exact tenu dans la main principale")));
                }
            }
        } else if (tab == 1) {
            form = new Form(this::addRenderableWidget, px + 10, top + 4, pw - 20, bottom - top - 32).onChange(() -> generalDirty = true);
            generalFields(form);
            form.setScroll(formScroll);
            form.layout();
            addRenderableWidget(Button.builder(Component.literal(generalDirty ? "§eEnregistrer *" : "Enregistrer"), x -> {
                JsonObject a = new JsonObject();
                a.add("data", general);
                lastSaved = "#general";
                Ui.send("admin_save_general", a);
            }).bounds(px + pw - 110, bottom - 20, 100, 18).build());
        } else {
            form = new Form(this::addRenderableWidget, px + 10, top + 4, pw - 20, bottom - top - 32).onChange(() -> builderDirty = true);
            builderFields(form);
            form.setScroll(formScroll);
            form.layout();
            addRenderableWidget(Button.builder(Component.literal(builderDirty ? "§eEnregistrer *" : "Enregistrer"), x -> {
                JsonObject a = new JsonObject();
                a.add("data", builder);
                lastSaved = "#builder";
                Ui.send("admin_save_builder", a);
            }).bounds(px + pw - 110, bottom - 20, 100, 18).build());
        }
    }

    /** Auto-test : onglet et sous-onglet affiches. */
    void testTab(int t, int s, String crate) {
        tab = t;
        sub = s;
        if (crate != null && work.containsKey(crate)) selected = crate;
        formScroll = 0;
        rebuild();
    }

    JsonObject testReward(int i) {
        return Ui.arr(work.get(selected), "recompenses").get(i).getAsJsonObject();
    }

    JsonObject adminData() {
        return data;
    }

    private void markDirty() {
        if (selected != null) dirty.add(selected);
    }

    private void saveCrate() {
        JsonObject c = work.get(selected);
        if (c == null) return;
        JsonObject copy = c.deepCopy();
        copy.remove("_id");
        copy.remove("_links");
        JsonObject a = new JsonObject();
        a.addProperty("id", selected);
        a.add("data", copy);
        lastSaved = selected;
        Ui.send("admin_save_crate", a);
    }

    // ------------------------------------------------------------------ Formulaires

    private void crateFields(Form f, JsonObject c) {
        f.section("Caisse « " + selected + " »");
        f.text("Nom affiché", c, "nom", "Codes couleur & et &#RRGGBB acceptés");
        f.text("Description", c, "description", "Sous-titre de l'aperçu");
        EditBox key = f.text("Clé", c, "cle", "Objet clé (les 10 clés : cobblepulse_crates:clef_...)");
        f.suggest(key, s -> {
            List<String> out = new ArrayList<>();
            for (JsonElement e : Ui.arr(data, "keys")) if (e.getAsString().contains(s)) out.add(e.getAsString());
            if (out.isEmpty()) out.addAll(ClientData.items(s));
            return out;
        });
        f.integer("Clés requises", c, "cles_requises", 1, 64, null);
        f.section("Animation");
        f.choice("Animation", c, "animation", List.of("roulette", "roue", "instantanee"), List.of("Roulette (CS:GO)", "Roue", "Instantanée"), null);
        f.decimal("Durée (secondes)", c, "duree_animation", "De 1 à 20 secondes");
        f.bool("Ouverture rapide", c, "ouverture_rapide", "Le joueur peut passer l'animation");
        f.text("Couleur d'accent", c, "couleur", "#RRGGBB (bandeau de l'interface)");
        f.section("Monde");
        Button m = f.bool("Maintenance", c, "maintenance", "Aperçu possible, ouverture bloquée sans consommer de clé");
        m.active = perm("toggle");
        f.bool("Hologramme", c, "hologramme", "Affiche la récompense au-dessus de la caisse pendant quelques secondes");
        EditBox p1 = f.text("Particules (repos)", c, "particule_attente", "ex. minecraft:end_rod, dust #FFD24A 1.2 (vide = aucune)");
        f.suggest(p1, ClientData::particles);
        EditBox p2 = f.text("Particules (ouverture)", c, "particule_ouverture", "Spirale pendant l'animation");
        f.suggest(p2, ClientData::particles);
        EditBox s = f.text("Son d'ouverture", c, "son_ouverture", "Vide = son du conteneur (coffre, tonneau...)");
        f.suggest(s, ClientData::sounds);
    }

    private void generalFields(Form f) {
        f.section("Général");
        Button m = f.bool("Maintenance globale", general, "maintenance_globale", "Bloque l'ouverture de toutes les caisses");
        m.active = perm("toggle");
        f.decimal("Distance max. (blocs)", general, "distance_max", "Distance maximale entre le joueur et la caisse pour l'ouvrir");
        f.bool("Annonces", general, "annonces", "Annonces serveur des lots marqués « Annonce »");
        EditBox tick = f.text("Son « tic »", general, "son_tic", "Son joué à chaque lot qui passe sous le repère (côté client)");
        f.suggest(tick, ClientData::sounds);
        f.bool("Journal", general, "journal", "Journal des ouvertures et du Vœu ADN (config/cobblepulse_crates/journal)");
        JsonObject h = Ui.obj(general, "hologramme");
        f.section("Hologramme de récompense");
        f.bool("Actif", h, "actif", null);
        f.decimal("Durée (secondes)", h, "duree_secondes", null);
        f.decimal("Hauteur", h, "hauteur", "Hauteur au-dessus du bloc");
        f.decimal("Échelle de l'objet", h, "echelle_objet", null);
        JsonObject pa = Ui.obj(general, "particules");
        f.section("Particules d'ambiance");
        f.bool("Actives", pa, "ambiance", null);
        f.integer("Intervalle (ticks)", pa, "intervalle_ticks", 2, 200, null);
        f.decimal("Rayon joueurs (blocs)", pa, "rayon_joueurs", "Les particules ne sont envoyées que si un joueur est proche");
        int i = 0;
        for (JsonElement e : Ui.arr(general, "raretes")) {
            JsonObject r = e.getAsJsonObject();
            f.section("Rareté " + (++i) + " : " + Ui.str(r, "id", ""));
            f.text("Nom", r, "nom", null);
            f.text("Couleur", r, "couleur", "#RRGGBB");
            EditBox snd = f.text("Son", r, "son", "Son joué à la caisse à l'obtention (sons de packs de ressources acceptés)");
            f.suggest(snd, ClientData::sounds);
            f.decimal("Volume", r, "volume", null);
            f.decimal("Tonalité", r, "tonalite", "0,5 à 2");
            EditBox part = f.text("Particules", r, "particule", "ex. minecraft:totem_of_undying, dust #FFD24A 1.5");
            f.suggest(part, ClientData::particles);
            f.integer("Quantité", r, "quantite", 0, 500, null);
        }
    }

    private void builderFields(Form f) {
        f.section("Menu Vœu ADN (/cpbuilder, touche N)");
        f.bool("Actif", builder, "actif", null);
        EditBox cur = f.text("Monnaie", builder, "monnaie", "Objet dépensé (par défaut cobblepulse_crates:voeu_adn)");
        f.suggest(cur, ClientData::items);
        f.bool("Pokémon du PC", builder, "autoriser_pc", "Autorise le remodelage des Pokémon du PC (sinon équipe seulement)");
        f.bool("Chromatique réversible", builder, "shiny_reversible", "Un Pokémon chromatique peut redevenir normal");
        f.bool("Exclure formes à objet", builder, "exclure_formes_a_objet", "Plaques d'Arceus, Giratina Originel, Zacian Couronné...");
        JsonObject pr = Ui.obj(builder, "prix");
        f.section("Prix (minimum 1 Vœu ADN par action)");
        f.integer("Chromatique", pr, "shiny", 1, 999, null);
        f.integer("Talent caché", pr, "talent_cache", 1, 999, null);
        f.integer("Forme", pr, "forme", 1, 999, "Forme régionale ou variante (hors Méga / Dynamax)");
        f.integer("Talent normal", pr, "talent_normal", 1, 999, null);
        f.integer("Nature", pr, "nature", 1, 999, null);
        f.integer("IV à 31 (par stat)", pr, "iv_par_stat", 1, 999, null);
        f.integer("EV (par session)", pr, "ev_par_session", 1, 999, "Une répartition complète des EV = une session");
        f.section("Restrictions");
        f.list("Formes interdites", builder, "formes_interdites", "Noms de formes ou aspects, séparés par des virgules");
        EditBox sp = f.list("Espèces interdites", builder, "especes_interdites", "Espèces que le menu refuse de modifier");
        f.suggest(sp, ClientData::species);
    }

    // ------------------------------------------------------------------ Recompenses

    private JsonArray rewards() {
        return Ui.arr(work.get(selected), "recompenses");
    }

    private double totalWeight(JsonObject except) {
        double t = 0;
        for (JsonElement e : rewards()) if (e != except) t += Math.max(0, Ui.dbl(e.getAsJsonObject(), "poids", 0));
        return t;
    }

    private void addReward(String type) {
        JsonObject r;
        if ("main".equals(type)) {
            if (minecraft.player == null || minecraft.player.getMainHandItem().isEmpty()) {
                toast(false, "Tenez l'objet dans la main principale.");
                return;
            }
            r = ClientData.newReward("objet");
            r.add("objet", Ui.encodeItem(minecraft.player.getMainHandItem()));
        } else {
            r = ClientData.newReward(type);
        }
        edit(r, true);
    }

    private void edit(JsonObject reward, boolean isNew) {
        JsonObject copy = reward.deepCopy();
        double other = totalWeight(isNew ? null : reward);
        minecraft.setScreen(new RewardEditScreen(this, copy, data, other, saved -> {
            JsonArray list = rewards();
            if (isNew) {
                list.add(saved);
            } else {
                for (int i = 0; i < list.size(); i++) if (list.get(i) == reward) list.set(i, saved);
            }
            dirty.add(selected);
        }));
    }

    private void move(int i, int d) {
        JsonArray list = rewards();
        int j = i + d;
        if (j < 0 || j >= list.size()) return;
        JsonElement a = list.get(i);
        list.set(i, list.get(j));
        list.set(j, a);
        dirty.add(selected);
    }

    // ------------------------------------------------------------------ Rendu

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph, Ui.PANEL, Ui.BORDER);
        g.fill(px + 1, py + 25, px + pw - 1, py + 26, Ui.BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hits.clear();
        Ui.text(g, "✦ Éditeur des caisses", px + 8, py + 9, Ui.GOLD);
        int top = py + 28;
        int bottom = py + ph - 6;
        if (tab == 0) {
            crateList(g, mouseX, mouseY, top, bottom - 26);
            JsonObject c = selected == null ? null : work.get(selected);
            int rx = px + listW() + 14;
            int rw = pw - listW() - 20;
            g.fill(px + listW() + 9, top, px + listW() + 10, bottom, Ui.BORDER);
            if (c == null) {
                Ui.centered(g, "Créez une caisse à gauche.", rx + rw / 2, top + 40, Ui.MUTED);
            } else if (sub == 1) {
                rewardList(g, mouseX, mouseY, rx, top + 38, rw, bottom - top - 66, c);
            } else if (sub == 2) {
                linkList(g, mouseX, mouseY, rx, top + 38, rw, c);
            }
        }
        if (form != null) form.render(g, mouseX, mouseY);
        int my = bottom - 15;
        if (!toast.isEmpty() && System.currentTimeMillis() < toastUntil) {
            int x0 = tab == 0 ? px + listW() + 14 : px + 10;
            int maxW = px + pw - (tab == 0 ? 220 : 120) - x0;
            Ui.text(g, Ui.cut(toast, maxW), x0, my, toastOk ? Ui.OK : Ui.ERROR);
            if (!warnings.isEmpty() && Ui.in(mouseX, mouseY, x0, my - 2, maxW, 12)) {
                List<Component> lines = new ArrayList<>();
                for (String w : warnings) lines.add(Component.literal("• " + w).withStyle(ChatFormatting.GOLD));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            } else if (!warnings.isEmpty()) {
                Ui.text(g, Ui.cut("⚠ " + warnings.size() + " avertissement(s) (survol)", maxW), x0, my - 11, Ui.GOLD);
            }
        }
    }

    private void crateList(GuiGraphics g, int mouseX, int mouseY, int top, int bottom) {
        int x = px + 6;
        int w = listW();
        Ui.text(g, "Caisses (" + work.size() + ")", x + 2, top + 6, Ui.MUTED);
        int y0 = top + 18;
        int maxScroll = Math.max(0, work.size() * ROW - (bottom - y0));
        listScroll = Math.max(0, Math.min(maxScroll, listScroll));
        g.enableScissor(x, y0, x + w, bottom);
        int i = 0;
        for (Map.Entry<String, JsonObject> e : work.entrySet()) {
            String id = e.getKey();
            JsonObject c = e.getValue();
            int y = y0 + i * ROW - (int) listScroll;
            i++;
            if (y + ROW < y0 || y > bottom) continue;
            boolean sel = id.equals(selected);
            boolean hov = Ui.in(mouseX, mouseY, x, y, w, ROW - 2) && mouseY >= y0 && mouseY < bottom;
            int col = 0xB48CF0;
            try {
                col = Integer.parseInt(Ui.str(c, "couleur", "#B48CF0").replace("#", ""), 16);
            } catch (Exception ignored) {
            }
            g.fill(x, y, x + w, y + ROW - 2, sel ? Ui.CARD_ON : hov ? 0xFF1E2633 : Ui.CARD);
            g.fill(x, y, x + 2, y + ROW - 2, Ui.argb(0xFF, col));
            Ui.item(g, Ui.itemById(Ui.str(c, "cle", "")), x + 5, y + 3, 1f, false);
            String name = ClientData.strip(Ui.str(c, "nom", id));
            Ui.text(g, Ui.cut((dirty.contains(id) ? "* " : "") + name, w - 30), x + 24, y + 3, sel ? Ui.TEXT : Ui.lighten(col, 0.2f) | 0xFF000000);
            String info = Ui.arr(c, "recompenses").size() + " lots · " + Ui.arr(c, "_links").size() + " cont.";
            Ui.scaled(g, info, x + 24, y + 13, Ui.MUTED, 0.75f);
            if (Ui.bool(c, "maintenance", false)) {
                Ui.scaled(g, "MAINT.", x + w - 30, y + 13, Ui.ERROR, 0.75f);
            }
            hits.add(new Hit(x, Math.max(y, y0), w, Math.min(y + ROW - 2, bottom) - Math.max(y, y0), () -> {
                if (!id.equals(selected)) {
                    if (form != null) formScroll = 0;
                    selected = id;
                    rewardScroll = 0;
                    formScroll = 0;
                    rebuild();
                }
            }));
        }
        g.disableScissor();
    }

    private void smallButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, String label, int color, String tip, Runnable action) {
        boolean hov = Ui.in(mouseX, mouseY, x, y, 14, 14);
        g.fill(x, y, x + 14, y + 14, hov ? 0xFF2E3A4C : 0xFF202836);
        Ui.frame(g, x, y, 14, 14, hov ? Ui.ACCENT : Ui.BORDER);
        Ui.centered(g, label, x + 7, y + 3, color);
        hits.add(new Hit(x, y, 14, 14, action));
        if (hov && tip != null) g.renderTooltip(font, Component.literal(tip), mouseX, mouseY);
    }

    private void rewardList(GuiGraphics g, int mouseX, int mouseY, int x, int y0, int w, int h, JsonObject c) {
        JsonArray list = Ui.arr(c, "recompenses");
        double total = 0;
        for (JsonElement e : list) total += Math.max(0, Ui.dbl(e.getAsJsonObject(), "poids", 0));
        Map<String, Integer> rc = new LinkedHashMap<>();
        Map<String, String> rn = new LinkedHashMap<>();
        for (JsonElement e : Ui.arr(general, "raretes")) {
            JsonObject r = e.getAsJsonObject();
            try {
                rc.put(Ui.str(r, "id", ""), Integer.parseInt(Ui.str(r, "couleur", "#B0B8C4").replace("#", ""), 16));
            } catch (Exception ex) {
                rc.put(Ui.str(r, "id", ""), 0xB0B8C4);
            }
            rn.put(Ui.str(r, "id", ""), Ui.str(r, "nom", ""));
        }
        int maxScroll = Math.max(0, list.size() * ROW - h);
        rewardScroll = Math.max(0, Math.min(maxScroll, rewardScroll));
        Ui.text(g, Ui.cut("Poids total : " + Ui.num(Math.round(total)) + "  ·  clic sur une ligne pour la modifier", w), x, y0 - 11, Ui.MUTED);
        g.enableScissor(x, y0, x + w, y0 + h);
        List<Runnable> deferred = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            JsonObject r = list.get(i).getAsJsonObject();
            int y = y0 + i * ROW - (int) rewardScroll;
            if (y + ROW < y0 || y > y0 + h) continue;
            String rid = Ui.str(r, "rarete", "commun");
            int col = rc.getOrDefault(rid, 0xB0B8C4);
            boolean hov = Ui.in(mouseX, mouseY, x, y, w - 92, ROW - 2) && mouseY >= y0 && mouseY < y0 + h;
            g.fill(x, y, x + w, y + ROW - 2, hov ? Ui.CARD_ON : Ui.CARD);
            g.fill(x, y, x + 2, y + ROW - 2, Ui.argb(0xFF, col));
            ItemStack icon = ClientData.rewardIcon(r);
            Ui.item(g, icon, x + 5, y + 3, 1f, true);
            double wgt = Math.max(0, Ui.dbl(r, "poids", 0));
            String pct = total <= 0 ? "0 %" : String.format(Locale.FRANCE, "%.2f %%", wgt / total * 100);
            Ui.text(g, Ui.cut(ClientData.rewardLabel(r), w - 230), x + 26, y + 3, Ui.TEXT);
            String type = ClientData.REWARD_TYPE_NAMES.get(Math.max(0, ClientData.REWARD_TYPES.indexOf(Ui.str(r, "type", "objet"))));
            Ui.scaled(g, type + (Ui.bool(r, "annonce", false) ? "  ·  ✦ annonce" : ""), x + 26, y + 13, Ui.MUTED, 0.75f);
            Ui.text(g, rn.getOrDefault(rid, rid), x + w - 196, y + 3, Ui.argb(0xFF, col));
            Ui.scaled(g, "poids " + RewardFmt.num(wgt), x + w - 196, y + 13, Ui.MUTED, 0.75f);
            Ui.text(g, pct, x + w - 128, y + 7, Ui.TEXT);
            int bx = x + w - 88;
            int by = y + 4;
            if (y >= y0 - 2 && y + ROW <= y0 + h + 2) {
                int idx = i;
                smallButton(g, mouseX, mouseY, bx, by, "▲", Ui.TEXT, "Monter", () -> move(idx, -1));
                smallButton(g, mouseX, mouseY, bx + 17, by, "▼", Ui.TEXT, "Descendre", () -> move(idx, 1));
                smallButton(g, mouseX, mouseY, bx + 34, by, "⧉", Ui.TEXT, "Dupliquer", () -> {
                    JsonObject copy = r.deepCopy();
                    copy.addProperty("id", java.util.UUID.randomUUID().toString().substring(0, 8));
                    rewards().add(copy);
                    dirty.add(selected);
                });
                smallButton(g, mouseX, mouseY, bx + 51, by, "✕", Ui.ERROR, "Supprimer (Maj + clic)", () -> {
                    if (!Screen.hasShiftDown()) {
                        toast(false, "Maj + clic pour supprimer la récompense.");
                        return;
                    }
                    rewards().remove(idx);
                    dirty.add(selected);
                });
                int rowY = y;
                hits.add(new Hit(x, Math.max(rowY, y0), w - 92, Math.min(rowY + ROW - 2, y0 + h) - Math.max(rowY, y0), () -> edit(r, false)));
            }
            if (hov) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(ClientData.rewardLabel(r)));
                tip.add(Component.literal("Identifiant : " + Ui.str(r, "id", "")).withStyle(ChatFormatting.DARK_GRAY));
                deferred.add(() -> g.renderComponentTooltip(font, tip, mouseX, mouseY));
            }
        }
        g.disableScissor();
        if (list.isEmpty()) Ui.centered(g, "Aucune récompense : ajoutez-en avec les boutons ci-dessous.", x + w / 2, y0 + 30, Ui.MUTED);
        for (Runnable r : deferred) r.run();
    }

    private void linkList(GuiGraphics g, int mouseX, int mouseY, int x, int y0, int w, JsonObject c) {
        JsonArray links = Ui.arr(c, "_links");
        Ui.text(g, Ui.cut("Lier : visez un conteneur et /lootbox set " + selected + "  ·  délier : /lootbox unset " + selected, w), x, y0 - 11, Ui.MUTED);
        if (links.isEmpty()) {
            Ui.centered(g, "Aucun conteneur lié à cette caisse.", x + w / 2, y0 + 30, Ui.MUTED);
            return;
        }
        for (int i = 0; i < links.size(); i++) {
            JsonObject l = links.get(i).getAsJsonObject();
            int y = y0 + i * ROW;
            if (y > py + ph - 40) break;
            g.fill(x, y, x + w, y + ROW - 2, Ui.CARD);
            String dim = Ui.str(l, "monde", "");
            String txt = dim + "   " + Ui.lng(l, "x", 0) + "  " + Ui.lng(l, "y", 0) + "  " + Ui.lng(l, "z", 0);
            Ui.text(g, txt, x + 8, y + 7, dim.equals(Ui.str(data, "dim", "")) ? Ui.TEXT : Ui.MUTED);
            JsonObject args = new JsonObject();
            args.addProperty("monde", dim);
            args.addProperty("x", Ui.lng(l, "x", 0));
            args.addProperty("y", Ui.lng(l, "y", 0));
            args.addProperty("z", Ui.lng(l, "z", 0));
            textButton(g, mouseX, mouseY, x + w - 120, y + 3, 56, "Aller", Ui.ACCENT, () -> Ui.send("admin_tp", args));
            if (perm("unset")) textButton(g, mouseX, mouseY, x + w - 60, y + 3, 56, "Délier", Ui.ERROR, () -> Ui.send("admin_unlink", args));
        }
    }

    private void textButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String label, int color, Runnable action) {
        boolean hov = Ui.in(mouseX, mouseY, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, hov ? 0xFF2E3A4C : 0xFF202836);
        Ui.frame(g, x, y, w, 16, hov ? color : Ui.BORDER);
        Ui.centered(g, label, x + w / 2, y + 4, color);
        hits.add(new Hit(x, y, w, 16, action));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (form != null && form.mouseClicked(mx, my)) return true;
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (Ui.in(mx, my, h.x(), h.y(), h.w(), h.h())) {
                    Ui.playUi(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.4f);
                    h.action().run();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (form != null && form.mouseScrolled(mx, my, sy)) return true;
        if (tab == 0 && mx < px + listW() + 10) {
            listScroll -= sy * 24;
            return true;
        }
        if (tab == 0 && sub == 1) {
            rewardScroll -= sy * 24;
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    /** Formatage des nombres (poids). */
    static final class RewardFmt {
        static String num(double v) {
            return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.FRANCE, "%.2f", v);
        }
    }
}
