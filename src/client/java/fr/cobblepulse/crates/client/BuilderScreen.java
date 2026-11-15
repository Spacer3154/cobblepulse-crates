package fr.cobblepulse.crates.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Menu Voeu ADN : choix d'un Pokemon (equipe ou PC), puis remodelage contre des Voeux ADN :
 * chromatique, talent cache, forme, talent, nature, IV a 31 (par stat), repartition des EV (par session).
 */
public final class BuilderScreen extends Screen implements CratesClient.Toaster {
    private static final String[][] OPS = {
            {"shiny", "Chromatique", "Basculer en version chromatique"},
            {"hidden", "Talent caché", "Passer sur le talent caché"},
            {"form", "Forme", "Forme régionale ou variante"},
            {"ability", "Talent", "Choisir un autre talent standard"},
            {"nature", "Nature", "Modifier la nature"},
            {"ivs", "IV à 31", "Monter une statistique à 31"},
            {"evs", "EV", "Répartir librement les EV"}};
    private static final int IRIS = 0xC9B8FF;

    private JsonObject root;
    private int listTab;
    private int box;
    private int boxes;
    private JsonArray boxMons = new JsonArray();
    private String selUuid;
    private JsonObject sel;
    private String op;
    private String choice;
    private final Set<String> ivSel = new LinkedHashSet<>();
    private JsonObject evEdit;
    private final List<EditBox> evBoxes = new ArrayList<>();
    private int px, py, pw, ph;
    private String toast = "";
    private boolean toastOk;
    private long toastUntil;
    private boolean waiting;

    public BuilderScreen(JsonObject root) {
        super(Component.literal("Vœu ADN"));
        this.root = root;
        boxes = (int) Ui.lng(root, "boxes", 0);
        for (JsonElement e : Ui.arr(root, "party")) {
            JsonObject m = e.getAsJsonObject();
            if (m.has("uuid")) {
                select(m);
                break;
            }
        }
    }

    public void refresh(JsonObject r) {
        root = r;
        boxes = (int) Ui.lng(r, "boxes", 0);
        if (selUuid != null) {
            JsonObject m = findMon(selUuid);
            if (m != null) sel = m;
        }
        rebuild();
    }

    public void onBox(JsonObject o) {
        box = (int) Ui.lng(o, "box", 0);
        boxes = (int) Ui.lng(o, "boxes", boxes);
        boxMons = Ui.arr(o, "mons");
        rebuild();
    }

    public void onResult(JsonObject r) {
        waiting = false;
        JsonObject m = Ui.obj(r, "mon");
        root.addProperty("wishes", Ui.lng(r, "wishes", Ui.lng(root, "wishes", 0)));
        if (m.has("uuid")) {
            replaceMon(m);
            if (m.get("uuid").getAsString().equals(selUuid)) sel = m;
        }
        toast(true, Ui.str(r, "message", "Modification appliquée."));
        Ui.playUi(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1f);
        op = null;
        choice = null;
        ivSel.clear();
        evEdit = null;
        rebuild();
    }

    @Override
    public void toast(boolean ok, String message) {
        toast = message;
        toastOk = ok;
        toastUntil = System.currentTimeMillis() + 5000;
        if (!ok) waiting = false;
    }

    private JsonObject findMon(String uuid) {
        for (JsonElement e : Ui.arr(root, "party")) if (uuid.equals(Ui.str(e.getAsJsonObject(), "uuid", ""))) return e.getAsJsonObject();
        for (JsonElement e : boxMons) if (uuid.equals(Ui.str(e.getAsJsonObject(), "uuid", ""))) return e.getAsJsonObject();
        return null;
    }

    private void replaceMon(JsonObject m) {
        String uuid = Ui.str(m, "uuid", "");
        JsonArray party = Ui.arr(root, "party");
        for (int i = 0; i < party.size(); i++) {
            if (uuid.equals(Ui.str(party.get(i).getAsJsonObject(), "uuid", ""))) {
                m.addProperty("where", "party");
                m.addProperty("slot", i);
                party.set(i, m);
            }
        }
        for (int i = 0; i < boxMons.size(); i++) {
            if (uuid.equals(Ui.str(boxMons.get(i).getAsJsonObject(), "uuid", ""))) {
                m.addProperty("where", "pc");
                boxMons.set(i, m);
            }
        }
    }

    /** Auto-test : ouvre une action (et un choix). */
    void testOp(String o, String c) {
        op = o;
        choice = c;
        ivSel.clear();
        if ("ivs".equals(o)) {
            ivSel.add("atk");
            ivSel.add("spe");
        }
        evEdit = "evs".equals(o) ? Ui.obj(sel, "evs").deepCopy() : null;
        rebuild();
    }

    private void select(JsonObject m) {
        sel = m;
        selUuid = Ui.str(m, "uuid", null);
        op = null;
        choice = null;
        ivSel.clear();
        evEdit = null;
    }

    private int price(String op) {
        JsonObject p = Ui.obj(root, "prices");
        return switch (op) {
            case "ivs" -> (int) Ui.lng(p, "iv", 1);
            default -> (int) Ui.lng(p, op, 1);
        };
    }

    private boolean bypass() {
        return Ui.bool(root, "bypass", false);
    }

    private long wishes() {
        return Ui.lng(root, "wishes", 0);
    }

    // ------------------------------------------------------------------ Mise en page

    @Override
    protected void init() {
        pw = Math.min(width - 12, 540);
        ph = Math.min(height - 12, 316);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int listW() {
        return 154;
    }

    private void rebuild() {
        if (minecraft == null) return;
        clearWidgets();
        evBoxes.clear();
        int lx = px + 8;
        int top = py + 30;
        Button t0 = addRenderableWidget(Button.builder(Component.literal("Équipe"), b -> {
            listTab = 0;
            rebuild();
        }).bounds(lx, top, 72, 16).build());
        t0.active = listTab != 0;
        if (Ui.bool(root, "pc", false)) {
            Button t1 = addRenderableWidget(Button.builder(Component.literal("PC"), b -> {
                listTab = 1;
                if (boxMons.isEmpty()) requestBox(box);
                rebuild();
            }).bounds(lx + 76, top, 72, 16).build());
            t1.active = listTab != 1;
        }
        if (listTab == 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> requestBox((box - 1 + Math.max(1, boxes)) % Math.max(1, boxes)))
                    .bounds(lx, top + 20, 18, 16).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> requestBox((box + 1) % Math.max(1, boxes)))
                    .bounds(lx + listW() - 22, top + 20, 18, 16).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(lx, py + ph - 24, 60, 18).build());
        if (sel == null || !sel.has("uuid")) return;
        int rx = px + listW() + 18;
        int rw = pw - listW() - 26;
        int ay = py + 112;
        boolean locked = Ui.bool(sel, "locked", false) || !Ui.bool(root, "canUse", true);
        if (op == null) {
            int cw = (rw - 6) / 2;
            for (int i = 0; i < OPS.length; i++) {
                String[] o = OPS[i];
                int bx = rx + (i % 2) * (cw + 6);
                int by = ay + (i / 2) * 26;
                int cost = price(o[0]);
                String label = o[1] + "  ·  " + cost + " ✦";
                Button b = addRenderableWidget(Button.builder(Component.literal(label), x -> {
                    op = o[0];
                    choice = null;
                    ivSel.clear();
                    evEdit = o[0].equals("evs") ? Ui.obj(sel, "evs").deepCopy() : null;
                    rebuild();
                }).bounds(bx, by, cw, 20).build());
                String why = disabledReason(o[0]);
                b.active = !locked && why == null;
                b.setTooltip(Tooltip.create(Component.literal(o[2] + (why == null ? "" : "\n§c" + why))));
            }
        } else {
            opWidgets(rx, ay, rw);
            Button cancel = addRenderableWidget(Button.builder(Component.literal("Retour"), x -> {
                op = null;
                rebuild();
            }).bounds(rx, py + ph - 24, 70, 18).build());
            cancel.active = !waiting;
            int cost = currentCost();
            String c = bypass() ? "Confirmer (gratuit)" : "Confirmer (-" + cost + " ✦ Vœu" + (cost > 1 ? "x" : "") + " ADN)";
            Button ok = addRenderableWidget(Button.builder(Component.literal(c), x -> confirm()).bounds(px + pw - 178, py + ph - 24, 170, 18).build());
            ok.active = !waiting && ready() && (bypass() || wishes() >= cost);
        }
    }

    private String disabledReason(String o) {
        switch (o) {
            case "shiny" -> {
                if (Ui.bool(sel, "shiny", false) && !Ui.bool(root, "shinyReversible", true)) return "Déjà chromatique";
            }
            case "hidden" -> {
                if (!sel.has("hiddenAbility")) return "Pas de talent caché";
                if (Ui.bool(Ui.obj(sel, "ability"), "hidden", false)) return "Talent caché déjà actif";
            }
            case "form" -> {
                if (Ui.arr(sel, "forms").size() < 2) return "Aucune autre forme disponible";
            }
            case "ability" -> {
                if (Ui.arr(sel, "abilities").size() < 2 && !Ui.bool(Ui.obj(sel, "ability"), "hidden", false)) return "Aucun autre talent standard";
            }
            case "ivs" -> {
                boolean any = false;
                for (String s : ClientData.STATS) if (Ui.lng(Ui.obj(sel, "ivs"), s, 0) < 31) any = true;
                if (!any) return "Tous les IV sont déjà à 31";
            }
            default -> {
            }
        }
        return null;
    }

    private int currentCost() {
        if (op == null) return 0;
        if ("ivs".equals(op)) return Math.max(1, ivSel.size()) * price("ivs");
        return price(op);
    }

    private boolean ready() {
        return switch (op) {
            case "shiny", "hidden" -> true;
            case "form", "ability", "nature" -> choice != null;
            case "ivs" -> !ivSel.isEmpty();
            case "evs" -> evEdit != null && evTotal() <= 510 && evChanged();
            default -> false;
        };
    }

    private int evTotal() {
        int t = 0;
        for (String s : ClientData.STATS) t += (int) Ui.lng(evEdit, s, 0);
        return t;
    }

    private boolean evChanged() {
        JsonObject cur = Ui.obj(sel, "evs");
        for (String s : ClientData.STATS) if (Ui.lng(cur, s, 0) != Ui.lng(evEdit, s, 0)) return true;
        return false;
    }

    private void opWidgets(int rx, int ay, int rw) {
        switch (op) {
            case "form" -> {
                int i = 0;
                int cw = (rw - 6) / 2;
                for (JsonElement e : Ui.arr(sel, "forms")) {
                    JsonObject f = e.getAsJsonObject();
                    String name = Ui.str(f, "name", "");
                    boolean cur = Ui.bool(f, "current", false);
                    Button b = addRenderableWidget(Button.builder(Component.literal((name.equals(choice) ? "▶ " : "") + Ui.str(f, "label", name) + (cur ? " (actuelle)" : "")),
                            x -> {
                                choice = name;
                                rebuild();
                            }).bounds(rx + (i % 2) * (cw + 6), ay + 14 + (i / 2) * 22, cw, 18).build());
                    b.active = !cur;
                    i++;
                    if (i >= 12) break;
                }
            }
            case "ability" -> {
                int i = 0;
                for (JsonElement e : Ui.arr(sel, "abilities")) {
                    JsonObject a = e.getAsJsonObject();
                    String id = Ui.str(a, "id", "");
                    boolean cur = id.equals(Ui.str(Ui.obj(sel, "ability"), "id", ""));
                    Button b = addRenderableWidget(Button.builder(Component.literal((id.equals(choice) ? "▶ " : "")).append(Ui.comp(Ui.str(a, "name", id)))
                            .append(Component.literal(cur ? " (actuel)" : "")), x -> {
                        choice = id;
                        rebuild();
                    }).bounds(rx, ay + 14 + i * 22, rw, 18).build());
                    b.active = !cur;
                    i++;
                }
            }
            case "nature" -> {
                int cols = 5;
                int cw = (rw - (cols - 1) * 3) / cols;
                int i = 0;
                String current = Ui.str(Ui.obj(sel, "nature"), "id", "");
                for (JsonElement e : Ui.arr(root, "natures")) {
                    JsonObject n = e.getAsJsonObject();
                    String id = Ui.str(n, "id", "");
                    Component name = Ui.comp(Ui.str(n, "name", id));
                    Button b = addRenderableWidget(Button.builder(Component.literal(id.equals(choice) ? "§e" : "").append(name), x -> {
                        choice = id;
                        rebuild();
                    }).bounds(rx + (i % cols) * (cw + 3), ay + 14 + (i / cols) * 20, cw, 17).build());
                    String plus = Ui.str(n, "plus", "");
                    String minus = Ui.str(n, "minus", "");
                    b.setTooltip(Tooltip.create(Component.literal(plus.equals(minus) || plus.isEmpty() ? "Neutre"
                            : "+ " + ClientData.statName(plus) + "  /  − " + ClientData.statName(minus))));
                    b.active = !id.equals(current);
                    i++;
                }
            }
            case "ivs" -> {
                JsonObject ivs = Ui.obj(sel, "ivs");
                int cw = (rw - 6) / 2;
                for (int i = 0; i < 6; i++) {
                    String s = ClientData.STATS.get(i);
                    long v = Ui.lng(ivs, s, 0);
                    boolean on = ivSel.contains(s);
                    Button b = addRenderableWidget(Button.builder(Component.literal((on ? "§a☑ " : "☐ ") + ClientData.STATS_FR.get(i) + " : " + v + (on ? " → 31" : "")),
                            x -> {
                                if (!ivSel.remove(s)) ivSel.add(s);
                                rebuild();
                            }).bounds(rx + (i % 2) * (cw + 6), ay + 14 + (i / 2) * 22, cw, 18).build());
                    b.active = v < 31;
                }
            }
            case "evs" -> {
                for (int i = 0; i < 6; i++) {
                    String s = ClientData.STATS.get(i);
                    EditBox e = addRenderableWidget(new EditBox(font, rx + 80, ay + 14 + i * 20, 46, 16, Component.literal(s)));
                    e.setValue(String.valueOf(Ui.lng(evEdit, s, 0)));
                    e.setFilter(v -> v.isEmpty() || v.matches("\\d{0,3}"));
                    e.setResponder(v -> {
                        int n;
                        try {
                            n = v.isEmpty() ? 0 : Integer.parseInt(v);
                        } catch (NumberFormatException x) {
                            n = 0;
                        }
                        evEdit.addProperty(s, Math.max(0, Math.min(252, n)));
                        updateConfirm();
                    });
                    evBoxes.add(e);
                    int idx = i;
                    addRenderableWidget(Button.builder(Component.literal("0"), x -> setEv(idx, 0)).bounds(rx + 130, ay + 14 + i * 20, 22, 16).build());
                    addRenderableWidget(Button.builder(Component.literal("252"), x -> setEv(idx, 252)).bounds(rx + 154, ay + 14 + i * 20, 30, 16).build());
                }
            }
            default -> {
            }
        }
    }

    private void setEv(int idx, int v) {
        evEdit.addProperty(ClientData.STATS.get(idx), v);
        evBoxes.get(idx).setValue(String.valueOf(v));
        updateConfirm();
    }

    private void updateConfirm() {
        // Le bouton de confirmation est reconstruit au prochain rafraichissement de l'ecran
        for (var w : children()) {
            if (w instanceof Button b && b.getMessage().getString().startsWith("Confirmer")) {
                b.active = !waiting && ready() && (bypass() || wishes() >= currentCost());
            }
        }
    }

    private void confirm() {
        if (sel == null || op == null || waiting) return;
        JsonObject a = new JsonObject();
        a.addProperty("uuid", Ui.str(sel, "uuid", ""));
        a.addProperty("op", op);
        switch (op) {
            case "form", "ability", "nature" -> a.add("value", new JsonPrimitive(choice));
            case "ivs" -> {
                JsonArray arr = new JsonArray();
                for (String s : ivSel) arr.add(s);
                a.add("value", arr);
            }
            case "evs" -> a.add("value", evEdit.deepCopy());
            default -> {
            }
        }
        waiting = true;
        Ui.send("builder_apply", a);
        rebuild();
    }

    private void requestBox(int b) {
        JsonObject a = new JsonObject();
        a.addProperty("box", b);
        Ui.send("builder_box", a);
    }

    // ------------------------------------------------------------------ Rendu

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph, Ui.PANEL, Ui.BORDER);
        Ui.hgradient(g, px + 1, py + 1, pw - 2, 24, Ui.argb(0x60, IRIS), Ui.argb(0x00, 0xA6E4FF));
        g.fill(px + 1, py + 25, px + pw - 1, py + 26, Ui.argb(0xB0, IRIS));
        g.fill(px + listW() + 12, py + 30, px + listW() + 13, py + ph - 6, Ui.BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Ui.text(g, "✦ Vœu ADN — Remodelage", px + 8, py + 9, Ui.argb(0xFF, IRIS));
        String w = bypass() ? "∞ (gratuit)" : String.valueOf(wishes());
        int ww = font.width(w);
        Ui.item(g, Ui.item(Ui.str(root, "currency", "")), px + pw - ww - 28, py + 4, 1f, false);
        Ui.text(g, w, px + pw - ww - 8, py + 9, Ui.TEXT);
        monList(g, mouseX, mouseY);
        if (sel == null || !sel.has("uuid")) {
            Ui.centered(g, "Choisissez un Pokémon à gauche.", px + listW() + 18 + (pw - listW() - 26) / 2, py + 120, Ui.MUTED);
        } else {
            details(g, mouseX, mouseY);
        }
        if (!toast.isEmpty() && System.currentTimeMillis() < toastUntil) {
            int x0 = px + 74;
            Ui.text(g, Ui.cut(toast, pw - 260), x0 + (op == null ? 0 : 76), py + ph - 19, toastOk ? Ui.OK : Ui.ERROR);
        }
    }

    private final List<int[]> slotHits = new ArrayList<>();
    private final List<JsonObject> slotMons = new ArrayList<>();

    private void monList(GuiGraphics g, int mouseX, int mouseY) {
        slotHits.clear();
        slotMons.clear();
        int lx = px + 8;
        int top = py + 50;
        if (listTab == 0) {
            JsonArray party = Ui.arr(root, "party");
            for (int i = 0; i < 6; i++) {
                int x = lx;
                int y = top + i * 34 - 4;
                JsonObject m = i < party.size() ? party.get(i).getAsJsonObject() : new JsonObject();
                slot(g, mouseX, mouseY, m, x, y, listW(), 31, true);
            }
        } else {
            Ui.centered(g, "Boîte " + (box + 1) + " / " + Math.max(1, boxes), lx + listW() / 2, py + 54, Ui.TEXT);
            int cell = 25;
            int gx = lx + (listW() - 6 * cell) / 2;
            for (int i = 0; i < 30; i++) {
                JsonObject m = i < boxMons.size() ? boxMons.get(i).getAsJsonObject() : new JsonObject();
                slot(g, mouseX, mouseY, m, gx + (i % 6) * cell, py + 68 + (i / 6) * cell, cell - 2, cell - 2, false);
            }
        }
    }

    private void slot(GuiGraphics g, int mouseX, int mouseY, JsonObject m, int x, int y, int w, int h, boolean wide) {
        boolean has = m.has("uuid");
        boolean isSel = has && Ui.str(m, "uuid", "").equals(selUuid);
        boolean hov = has && Ui.in(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, isSel ? Ui.CARD_ON : hov ? 0xFF1E2633 : Ui.CARD);
        if (isSel) Ui.frame(g, x, y, w, h, Ui.argb(0xFF, IRIS));
        if (!has) return;
        if (wide) {
            Ui.item(g, Ui.item(Ui.str(m, "icon", "")), x + 3, y + 3, 1.5f, false);
            g.drawString(font, Ui.comp(Ui.str(m, "name", "")), x + 30, y + 5, 0xFFFFFFFF, false);
            String sub = "Nv. " + Ui.lng(m, "level", 1) + (Ui.bool(m, "shiny", false) ? "  ★" : "");
            Ui.text(g, sub, x + 30, y + 17, Ui.bool(m, "shiny", false) ? Ui.GOLD : Ui.MUTED);
        } else {
            Ui.item(g, Ui.item(Ui.str(m, "icon", "")), x + (w - 16) / 2, y + (h - 16) / 2, 1f, false);
            if (Ui.bool(m, "shiny", false)) Ui.scaled(g, "★", x + w - 6, y + 1, Ui.GOLD, 0.75f);
        }
        slotHits.add(new int[]{x, y, w, h});
        slotMons.add(m);
        if (hov && !wide) {
            g.renderTooltip(font, List.of(Ui.comp(Ui.str(m, "name", "")).getVisualOrderText(),
                    Component.literal("Nv. " + Ui.lng(m, "level", 1)).withStyle(ChatFormatting.GRAY).getVisualOrderText()), mouseX, mouseY);
        }
    }

    private void details(GuiGraphics g, int mouseX, int mouseY) {
        int rx = px + listW() + 18;
        int rw = pw - listW() - 26;
        int y = py + 32;
        Ui.panel(g, rx, y, 70, 70, Ui.CARD, Ui.BORDER);
        Ui.item(g, Ui.item(Ui.str(sel, "icon", "")), rx + 7, y + 7, 3.5f, false);
        int tx = rx + 78;
        g.drawString(font, Ui.comp(Ui.str(sel, "name", "")), tx, y + 2, 0xFFFFFFFF, true);
        String info = Ui.comp(Ui.str(sel, "species", "")).getString() + "  ·  Nv. " + Ui.lng(sel, "level", 1) + "  ·  Forme " + Ui.str(sel, "form", "");
        Ui.text(g, Ui.cut(info, rw - 80), tx, y + 13, Ui.MUTED);
        if (Ui.bool(sel, "shiny", false)) Ui.text(g, "★ Chromatique", tx, y + 24, Ui.GOLD);
        JsonObject nat = Ui.obj(sel, "nature");
        Component natName = Component.literal("Nature : ").withStyle(ChatFormatting.GRAY).append(Ui.comp(Ui.str(nat, "name", "")).copy().withStyle(ChatFormatting.WHITE));
        g.drawString(font, natName, tx, y + 35, 0xFFFFFFFF, false);
        JsonObject ab = Ui.obj(sel, "ability");
        Component abName = Component.literal("Talent : ").withStyle(ChatFormatting.GRAY).append(Ui.comp(Ui.str(ab, "name", "")).copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(Ui.bool(ab, "hidden", false) ? " (caché)" : "").withStyle(ChatFormatting.LIGHT_PURPLE));
        g.drawString(font, abName, tx, y + 46, 0xFFFFFFFF, false);
        if (Ui.bool(sel, "locked", false)) Ui.text(g, "Cette espèce ne peut pas être remodelée.", tx, y + 58, Ui.ERROR);
        // Statistiques
        int sy = y + 76;
        JsonObject ivs = Ui.obj(sel, "ivs");
        JsonObject evs = op != null && op.equals("evs") && evEdit != null ? evEdit : Ui.obj(sel, "evs");
        if (op == null) {
            Ui.scaled(g, "Statistiques (survol pour le détail)", rx, sy - 2, Ui.MUTED, 0.75f);
            int colW = (rw + 4) / 3;
            int bw = colW - 34;
            for (int i = 0; i < 6; i++) {
                String s = ClientData.STATS.get(i);
                int cx = rx + (i % 3) * colW;
                int yy = sy + 6 + (i / 3) * 14;
                long iv = Ui.lng(ivs, s, 0);
                long ev = Ui.lng(evs, s, 0);
                Ui.text(g, ClientData.STATS_SHORT.get(i), cx, yy + 2, Ui.MUTED);
                Ui.bar(g, cx + 28, yy + 2, bw, 4, iv / 31.0, iv >= 31 ? Ui.OK : Ui.ACCENT);
                Ui.bar(g, cx + 28, yy + 7, bw, 4, ev / 252.0, Ui.PURPLE);
                if (Ui.in(mouseX, mouseY, cx, yy, bw + 28, 12)) {
                    g.renderTooltip(font, Component.literal(ClientData.STATS_FR.get(i) + " — IV " + iv + " / 31  ·  EV " + ev + " / 252"), mouseX, mouseY);
                }
            }
            g.fill(rx + rw - 52, sy - 1, rx + rw - 49, sy + 2, Ui.ACCENT);
            Ui.scaled(g, "IV", rx + rw - 47, sy - 2, Ui.MUTED, 0.75f);
            g.fill(rx + rw - 30, sy - 1, rx + rw - 27, sy + 2, Ui.PURPLE);
            Ui.scaled(g, "EV", rx + rw - 25, sy - 2, Ui.MUTED, 0.75f);
        } else {
            opHeader(g, rx, py + 104, rw);
        }
    }

    private void opHeader(GuiGraphics g, int rx, int y, int rw) {
        String title = "";
        for (String[] o : OPS) if (o[0].equals(op)) title = o[1] + " — " + o[2];
        Ui.text(g, title, rx, y - 2, Ui.argb(0xFF, IRIS));
        int ay = py + 112;
        switch (op) {
            case "shiny" -> {
                boolean s = Ui.bool(sel, "shiny", false);
                Ui.text(g, s ? "Le Pokémon redeviendra de couleur normale." : "Le Pokémon deviendra chromatique ★.", rx, ay + 18, Ui.TEXT);
            }
            case "hidden" -> {
                JsonObject h = Ui.obj(sel, "hiddenAbility");
                g.drawString(font, Component.literal("Talent caché : ").withStyle(ChatFormatting.GRAY).append(Ui.comp(Ui.str(h, "name", "")).copy()
                        .withStyle(ChatFormatting.LIGHT_PURPLE)), rx, ay + 18, 0xFFFFFFFF, false);
            }
            case "evs" -> {
                for (int i = 0; i < 6; i++) Ui.text(g, ClientData.STATS_FR.get(i), rx, ay + 18 + i * 20, Ui.TEXT);
                int total = evTotal();
                Ui.text(g, "Total : " + total + " / 510", rx + 196, ay + 18, total > 510 ? Ui.ERROR : Ui.OK);
                Ui.text(g, "Max. 252 par stat.", rx + 196, ay + 30, Ui.MUTED);
            }
            case "ivs" -> Ui.text(g, "Coût : " + price("ivs") + " ✦ par statistique choisie.", rx, ay + 84, Ui.MUTED);
            default -> {
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int i = 0; i < slotHits.size(); i++) {
            int[] h = slotHits.get(i);
            if (Ui.in(mx, my, h[0], h[1], h[2], h[3])) {
                if (waiting) return true;
                select(slotMons.get(i));
                Ui.playUi(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.4f);
                rebuild();
                return true;
            }
        }
        return false;
    }

    static ItemStack none() {
        return ItemStack.EMPTY;
    }
}
