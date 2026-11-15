package fr.cobblepulse.crates.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Formulaire defilant : une ligne = un libelle + un champ lie a une propriete d'un objet JSON
 * (texte, entier, decimal, oui/non, choix, liste separee par des virgules, table cle:valeur, bouton).
 */
public final class Form {
    private static final int ROW = 22;

    private final class Row {
        final String label;
        final String tip;
        final List<AbstractWidget> widgets = new ArrayList<>();
        final boolean section;
        int y;

        Row(String label, String tip, boolean section) {
            this.label = label;
            this.tip = tip;
            this.section = section;
        }
    }

    private final Consumer<AbstractWidget> adder;
    private final List<Row> rows = new ArrayList<>();
    private final Map<EditBox, Function<String, List<String>>> suggesters = new java.util.HashMap<>();
    private int x;
    private int y;
    private int w;
    private int h;
    private int labelW;
    private double scroll;
    private List<String> shownSuggestions = new ArrayList<>();
    private EditBox suggestBox;
    private Runnable onChange = () -> {};

    public Form(Consumer<AbstractWidget> adder, int x, int y, int w, int h) {
        this.adder = adder;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.labelW = Math.min(170, Math.max(90, w * 2 / 5));
    }

    public Form onChange(Runnable r) {
        this.onChange = r == null ? () -> {} : r;
        return this;
    }

    public void setScroll(double s) {
        this.scroll = s;
    }

    public double scroll() {
        return scroll;
    }

    private Row row(String label, String tip) {
        Row r = new Row(label, tip, false);
        rows.add(r);
        return r;
    }

    private <T extends AbstractWidget> T add(Row r, T widget) {
        if (r.tip != null && !r.tip.isEmpty()) widget.setTooltip(Tooltip.create(Component.literal(r.tip)));
        r.widgets.add(widget);
        adder.accept(widget);
        return widget;
    }

    public void section(String title) {
        rows.add(new Row(title, null, true));
    }

    private EditBox box(Row r, String value) {
        EditBox b = new EditBox(Ui.font(), 0, 0, 100, 18, Component.literal(r.label));
        b.setMaxLength(4096);
        b.setValue(value == null ? "" : value);
        b.moveCursorToStart(false);
        return add(r, b);
    }

    public EditBox text(String label, JsonObject obj, String key, String tip) {
        Row r = row(label, tip);
        EditBox b = box(r, Ui.str(obj, key, ""));
        b.setResponder(v -> {
            obj.addProperty(key, v);
            onChange.run();
        });
        return b;
    }

    public EditBox integer(String label, JsonObject obj, String key, int min, int max, String tip) {
        Row r = row(label, tip);
        EditBox b = box(r, String.valueOf(Ui.lng(obj, key, 0)));
        b.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,10}"));
        b.setResponder(v -> {
            try {
                long n = Long.parseLong(v.trim());
                obj.addProperty(key, (int) Math.max(min, Math.min(max, n)));
                b.setTextColor(0xFFE0E0E0);
                onChange.run();
            } catch (NumberFormatException e) {
                b.setTextColor(0xFFFF6B6B);
            }
        });
        return b;
    }

    public EditBox decimal(String label, JsonObject obj, String key, String tip) {
        Row r = row(label, tip);
        double d = Ui.dbl(obj, key, 0);
        EditBox b = box(r, d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d));
        b.setFilter(s -> s.isEmpty() || s.matches("-?\\d{0,12}([.,]\\d{0,6})?"));
        b.setResponder(v -> {
            try {
                obj.addProperty(key, Double.parseDouble(v.trim().replace(',', '.')));
                b.setTextColor(0xFFE0E0E0);
                onChange.run();
            } catch (NumberFormatException e) {
                b.setTextColor(0xFFFF6B6B);
            }
        });
        return b;
    }

    public Button bool(String label, JsonObject obj, String key, String tip) {
        Row r = row(label, tip);
        Button b = Button.builder(Component.literal(Ui.bool(obj, key, false) ? "Oui" : "Non"), btn -> {
            boolean v = !Ui.bool(obj, key, false);
            obj.addProperty(key, v);
            btn.setMessage(Component.literal(v ? "Oui" : "Non"));
            onChange.run();
        }).bounds(0, 0, 100, 18).build();
        return add(r, b);
    }

    /** Choix parmi des valeurs (libelles affiches). */
    public Button choice(String label, JsonObject obj, String key, List<String> values, List<String> names, String tip) {
        Row r = row(label, tip);
        String cur = Ui.str(obj, key, values.isEmpty() ? "" : values.get(0));
        int idx = Math.max(0, values.indexOf(cur));
        Button b = Button.builder(Component.literal(names.get(idx)), btn -> {
            String c = Ui.str(obj, key, values.get(0));
            int i = (values.indexOf(c) + (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? values.size() - 1 : 1)) % values.size();
            obj.addProperty(key, values.get(i));
            btn.setMessage(Component.literal(names.get(i)));
            onChange.run();
        }).bounds(0, 0, 100, 18).build();
        return add(r, b);
    }

    private static String joinList(JsonObject obj, String key) {
        List<String> out = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) for (JsonElement e : obj.getAsJsonArray(key)) out.add(e.getAsString());
        return String.join(", ", out);
    }

    /** Liste de textes separes par des virgules. */
    public EditBox list(String label, JsonObject obj, String key, String tip) {
        Row r = row(label, tip);
        EditBox b = box(r, joinList(obj, key));
        b.setResponder(v -> {
            JsonArray a = new JsonArray();
            for (String s : v.split(",")) if (!s.isBlank()) a.add(s.trim());
            obj.add(key, a);
            onChange.run();
        });
        return b;
    }

    public EditBox intList(String label, JsonObject obj, String key, String tip) {
        Row r = row(label, tip);
        EditBox b = box(r, joinList(obj, key));
        b.setResponder(v -> {
            JsonArray a = new JsonArray();
            for (String s : v.split(",")) {
                try {
                    if (!s.isBlank()) a.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            obj.add(key, a);
            onChange.run();
        });
        return b;
    }

    /** Table cle:valeur entiere ("7:500, 14:1000"). */
    public EditBox intMap(String label, JsonObject obj, String key, String tip) {
        Row r = row(label, tip);
        List<String> parts = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject(key).entrySet()) parts.add(e.getKey() + ":" + e.getValue().getAsString());
        }
        EditBox b = box(r, String.join(", ", parts));
        b.setResponder(v -> {
            JsonObject m = new JsonObject();
            for (String s : v.split(",")) {
                String[] kv = s.split(":");
                if (kv.length != 2) continue;
                try {
                    m.add(kv[0].trim(), new JsonPrimitive(Integer.parseInt(kv[1].trim())));
                } catch (NumberFormatException ignored) {
                }
            }
            obj.add(key, m);
            onChange.run();
        });
        return b;
    }

    public Button button(String label, String text, Runnable action, String tip) {
        Row r = row(label, tip);
        Button b = Button.builder(Component.literal(text), btn -> action.run()).bounds(0, 0, 100, 18).build();
        return add(r, b);
    }

    /** Ligne de plusieurs boutons. */
    public List<Button> buttons(String label, List<String> texts, List<Runnable> actions) {
        Row r = row(label, null);
        List<Button> out = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            Runnable a = actions.get(i);
            out.add(add(r, Button.builder(Component.literal(texts.get(i)), btn -> a.run()).bounds(0, 0, 60, 18).build()));
        }
        return out;
    }

    /** Plusieurs entiers sur une ligne (IV, EV...) lies aux cles d'un objet JSON. */
    public List<EditBox> intRow(String label, JsonObject obj, List<String> keys, int min, int max, String tip) {
        Row r = row(label, tip);
        List<EditBox> out = new ArrayList<>();
        for (String key : keys) {
            EditBox b = box(r, String.valueOf(Ui.lng(obj, key, 0)));
            b.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,4}"));
            b.setResponder(v -> {
                try {
                    obj.addProperty(key, (int) Math.max(min, Math.min(max, Long.parseLong(v.trim()))));
                    b.setTextColor(0xFFE0E0E0);
                    onChange.run();
                } catch (NumberFormatException e) {
                    b.setTextColor(0xFFFF6B6B);
                }
            });
            out.add(b);
        }
        return out;
    }

    /** Plusieurs textes sur une ligne lies aux elements d'un tableau JSON (ex. 4 capacites). */
    public List<EditBox> textRow(String label, JsonObject obj, String key, int count, String tip) {
        Row r = row(label, tip);
        List<EditBox> out = new ArrayList<>();
        List<EditBox> boxes = new ArrayList<>();
        JsonArray cur = obj.has(key) && obj.get(key).isJsonArray() ? obj.getAsJsonArray(key) : new JsonArray();
        for (int i = 0; i < count; i++) {
            EditBox b = box(r, i < cur.size() ? cur.get(i).getAsString() : "");
            boxes.add(b);
            out.add(b);
        }
        for (EditBox b : boxes) {
            b.setResponder(v -> {
                JsonArray a = new JsonArray();
                for (EditBox x : boxes) if (!x.getValue().isBlank()) a.add(x.getValue().trim());
                obj.add(key, a);
                onChange.run();
            });
        }
        return out;
    }

    /** Champ libre (non lie) : la valeur est lue par l'appelant. */
    public EditBox free(String label, String value, String tip) {
        Row r = row(label, tip);
        return box(r, value);
    }

    public void suggest(EditBox box, Function<String, List<String>> fn) {
        suggesters.put(box, fn);
    }

    public int contentHeight() {
        int hh = 0;
        for (Row r : rows) hh += r.section ? 16 : ROW;
        return hh + 4;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - h);
    }

    /** Place les champs selon le defilement (les champs hors de la zone sont masques). */
    public void layout() {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        int cy = y - (int) scroll;
        int fx = x + labelW + 4;
        int fw = w - labelW - 10;
        for (Row r : rows) {
            r.y = cy;
            int rh = r.section ? 16 : ROW;
            if (!r.widgets.isEmpty()) {
                int n = r.widgets.size();
                int gap = 4;
                int each = (fw - gap * (n - 1)) / n;
                for (int i = 0; i < n; i++) {
                    AbstractWidget wd = r.widgets.get(i);
                    wd.setX(fx + i * (each + gap));
                    wd.setY(cy + 1);
                    wd.setWidth(each);
                    boolean vis = cy >= y - 1 && cy + 19 <= y + h + 1;
                    wd.visible = vis;
                }
            }
            cy += rh;
        }
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!Ui.in(mx, my, x, y, w, h)) return false;
        scroll -= delta * 24;
        layout();
        return true;
    }

    public boolean mouseClicked(double mx, double my) {
        if (suggestBox != null && suggestBox.visible && suggestBox.isFocused() && !shownSuggestions.isEmpty()) {
            int sx = suggestBox.getX();
            int sy = suggestBox.getY() + 19;
            for (int i = 0; i < shownSuggestions.size(); i++) {
                if (Ui.in(mx, my, sx, sy + i * 11, suggestBox.getWidth(), 11)) {
                    String cur = suggestBox.getValue();
                    int comma = cur.lastIndexOf(',');
                    String head = comma >= 0 ? cur.substring(0, comma + 1) + " " : "";
                    suggestBox.setValue(head + shownSuggestions.get(i));
                    shownSuggestions = new ArrayList<>();
                    return true;
                }
            }
        }
        return false;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        g.enableScissor(x, y, x + w, y + h);
        for (Row r : rows) {
            if (r.y + 20 < y || r.y > y + h) continue;
            if (r.section) {
                Ui.text(g, r.label, x + 2, r.y + 5, Ui.GOLD);
                g.fill(x + 2 + Ui.font().width(r.label) + 4, r.y + 9, x + w - 6, r.y + 10, Ui.BORDER);
            } else {
                Ui.text(g, Ui.cut(r.label, labelW - 4), x + 4, r.y + 6, r.tip != null && Ui.in(mouseX, mouseY, x, r.y, labelW, 20) ? Ui.ACCENT : Ui.TEXT);
            }
        }
        g.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int th = Math.max(16, h * h / contentHeight());
            int ty = y + (int) ((h - th) * (scroll / max));
            g.fill(x + w - 3, y, x + w - 1, y + h, 0x40FFFFFF);
            g.fill(x + w - 3, ty, x + w - 1, ty + th, 0xC0FFFFFF);
        }
        // Infobulle du libelle
        for (Row r : rows) {
            if (r.tip != null && !r.section && r.y >= y && r.y + 20 <= y + h && Ui.in(mouseX, mouseY, x, r.y, labelW, 20)) {
                g.renderTooltip(Ui.font(), Ui.font().split(Component.literal(r.tip), 220), mouseX, mouseY);
            }
        }
        // Suggestions
        shownSuggestions = new ArrayList<>();
        suggestBox = null;
        for (Map.Entry<EditBox, Function<String, List<String>>> e : suggesters.entrySet()) {
            EditBox b = e.getKey();
            if (!b.visible || !b.isFocused()) continue;
            String cur = b.getValue();
            int comma = cur.lastIndexOf(',');
            String token = (comma >= 0 ? cur.substring(comma + 1) : cur).trim().toLowerCase(Locale.ROOT);
            if (token.length() < 2) continue;
            List<String> s = e.getValue().apply(token);
            if (s.isEmpty() || (s.size() == 1 && s.get(0).equalsIgnoreCase(token))) continue;
            suggestBox = b;
            shownSuggestions = s.subList(0, Math.min(6, s.size()));
            int sx = b.getX();
            int sy = b.getY() + 19;
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(sx, sy, sx + b.getWidth(), sy + shownSuggestions.size() * 11 + 1, 0xF0000000);
            for (int i = 0; i < shownSuggestions.size(); i++) {
                boolean hov = Ui.in(mouseX, mouseY, sx, sy + i * 11, b.getWidth(), 11);
                Ui.text(g, Ui.cut(shownSuggestions.get(i), b.getWidth() - 4), sx + 2, sy + 2 + i * 11, hov ? Ui.GOLD : Ui.TEXT);
            }
            g.pose().popPose();
            break;
        }
    }
}
