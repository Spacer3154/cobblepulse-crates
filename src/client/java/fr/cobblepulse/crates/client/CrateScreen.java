package fr.cobblepulse.crates.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ecran d'une caisse : apercu des recompenses (nom et % au survol, bouton d'ouverture), puis animation
 * (roulette facon CS:GO ou roue) avec ralentissement progressif, tics sonores et mise en avant du lot gagnant.
 */
public final class CrateScreen extends Screen implements CratesClient.Toaster {
    private enum Mode { PREVIEW, SPIN, RESULT }

    private static final int CELL_W = 46;
    private static final int CELL_H = 54;
    private static final int CARD_W = 62;
    private static final int CARD_GAP = 4;
    private static final int STRIP_LEN = 64;
    private static final int STRIP_WIN = 56;

    private JsonObject data;
    private final List<JsonObject> rewards = new ArrayList<>();
    private Mode mode = Mode.PREVIEW;
    private int px, py, pw, ph;
    private double scroll;
    private int accent = 0xB48CF0;

    // Animation
    private String anim = "roulette";
    private long spinStart;
    private long spinMs;
    private boolean instant;
    private boolean skipAllowed;
    private String tickSound = "minecraft:block.note_block.hat";
    private final List<JsonObject> strip = new ArrayList<>();
    private double stripTarget;
    private int lastTick = Integer.MIN_VALUE;
    private final List<JsonObject> wheel = new ArrayList<>();
    private int wheelWinner;
    private double wheelTarget;
    private JsonObject result;
    private long resultAt;
    private int keysHave;
    private int keysNeed = 1;

    private String toast = "";
    private boolean toastOk;
    private long toastUntil;

    /** Auto-test : force le survol d'une recompense (-1 = souris). */
    int debugHover = -1;
    private Button openBtn;
    private Button skipBtn;
    private Button againBtn;
    private Button backBtn;

    public CrateScreen(JsonObject data) {
        super(Component.literal("Caisse"));
        this.data = data;
        readRewards(data);
        readHeader(data);
    }

    public String crateId() {
        return Ui.str(data, "id", "");
    }

    private void readHeader(JsonObject d) {
        accent = (int) Ui.lng(d, "color", 0xB48CF0);
        JsonObject k = Ui.obj(d, "key");
        keysHave = (int) Ui.lng(k, "have", 0);
        keysNeed = (int) Ui.lng(k, "need", 1);
        anim = Ui.str(d, "anim", "roulette");
        skipAllowed = Ui.bool(d, "skip", true);
        tickSound = Ui.str(d, "tick", tickSound);
    }

    private void readRewards(JsonObject d) {
        if (!d.has("rewards")) return;
        rewards.clear();
        for (JsonElement e : Ui.arr(d, "rewards")) rewards.add(e.getAsJsonObject());
    }

    // ------------------------------------------------------------------ Donnees du serveur

    public void showPreview(JsonObject d) {
        data = d;
        readRewards(d);
        readHeader(d);
        mode = Mode.PREVIEW;
        result = null;
        rebuild();
    }

    public void startSpin(JsonObject d) {
        JsonObject keep = data;
        data = d;
        if (keep != null && keep.has("pos") && !d.has("pos")) d.add("pos", keep.get("pos"));
        readRewards(d);
        readHeader(d);
        result = Ui.obj(d, "result");
        instant = Ui.bool(d, "instant", false) || "instantanee".equals(anim);
        spinMs = (long) (Ui.dbl(d, "duration", 6.0) * 1000);
        spinStart = System.currentTimeMillis();
        lastTick = Integer.MIN_VALUE;
        Random rnd = new Random(Ui.lng(d, "seed", 1L));
        String winner = Ui.str(d, "winner", "");
        JsonObject win = null;
        for (JsonObject r : rewards) if (Ui.str(r, "id", "").equals(winner)) win = r;
        if (win == null) {
            win = new JsonObject();
            win.addProperty("icon", Ui.str(result, "icon", ""));
            win.addProperty("name", Ui.str(result, "name", ""));
            win.add("rarity", Ui.obj(result, "rarity"));
        }
        if ("roue".equals(anim)) buildWheel(rnd, win);
        else buildStrip(rnd, win);
        if (instant) {
            mode = Mode.RESULT;
            resultAt = System.currentTimeMillis();
            playReveal();
        } else {
            mode = Mode.SPIN;
        }
        rebuild();
    }

    public void onDone(JsonObject d) {
        keysHave = (int) Ui.lng(d, "have", keysHave);
        keysNeed = (int) Ui.lng(d, "need", keysNeed);
        if (mode == Mode.RESULT) rebuild();
    }

    @Override
    public void toast(boolean ok, String message) {
        toast = message;
        toastOk = ok;
        toastUntil = System.currentTimeMillis() + 4000;
        if (mode == Mode.SPIN && !ok) mode = Mode.PREVIEW;
        if (!ok) rebuild();
    }

    private JsonObject pick(Random rnd) {
        double total = 0;
        for (JsonObject r : rewards) total += Math.max(0.0001, Ui.dbl(r, "chance", 0));
        double x = rnd.nextDouble() * total;
        for (JsonObject r : rewards) {
            x -= Math.max(0.0001, Ui.dbl(r, "chance", 0));
            if (x <= 0) return r;
        }
        return rewards.isEmpty() ? new JsonObject() : rewards.get(rewards.size() - 1);
    }

    private void buildStrip(Random rnd, JsonObject win) {
        strip.clear();
        for (int i = 0; i < STRIP_LEN; i++) strip.add(i == STRIP_WIN ? win : pick(rnd));
        double jitter = (rnd.nextDouble() - 0.5) * 0.8 * CARD_W;
        stripTarget = STRIP_WIN * (CARD_W + CARD_GAP) - jitter;
    }

    private void buildWheel(Random rnd, JsonObject win) {
        wheel.clear();
        List<JsonObject> pool = new ArrayList<>(rewards);
        int n = Math.max(8, Math.min(12, pool.size()));
        List<JsonObject> seg = new ArrayList<>();
        // Chaque recompense au moins une fois (si possible), puis complement pondere
        java.util.Collections.shuffle(pool, rnd);
        for (JsonObject r : pool) if (seg.size() < n) seg.add(r);
        while (seg.size() < n) seg.add(pick(rnd));
        wheelWinner = rnd.nextInt(n);
        seg.set(wheelWinner, win);
        // Le lot gagnant ne doit pas apparaitre ailleurs avec la meme identite (lisibilite)
        wheel.addAll(seg);
        double theta = Math.PI * 2 / n;
        double jitter = (rnd.nextDouble() - 0.5) * 0.7 * theta;
        wheelTarget = Math.PI * 2 * 5 + (Math.PI * 2 - (wheelWinner + 0.5) * theta) + jitter;
    }

    // ------------------------------------------------------------------ Mise en page

    private int savedScale = -1;

    @Override
    public void removed() {
        super.removed();
        savedScale = Ui.restoreScale(savedScale);
    }

    @Override
    protected void init() {
        savedScale = Ui.fitScale(this, 420, 260, savedScale);
        pw = Math.min(width - 16, 460);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        rebuild();
    }

    private void rebuild() {
        if (minecraft == null) return;
        clearWidgets();
        int by = py + ph - 26;
        if (mode == Mode.PREVIEW) {
            String reason = blockReason();
            String label = reason == null ? "Ouvrir  (" + keysNeed + " × " + keyName() + ")" : reason;
            openBtn = addRenderableWidget(Button.builder(Component.literal(label), b -> open()).bounds(px + pw / 2 - 110, by, 220, 20).build());
            openBtn.active = reason == null;
            if (Ui.bool(data, "admin", false)) {
                addRenderableWidget(Button.builder(Component.literal("Éditeur"), b -> Ui.send("admin_open")).bounds(px + pw - 76, by, 68, 20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(px + 8, by, 68, 20).build());
        } else if (mode == Mode.SPIN) {
            if (skipAllowed) {
                skipBtn = addRenderableWidget(Button.builder(Component.literal("Passer »"), b -> skip()).bounds(px + pw / 2 - 50, by, 100, 20).build());
            }
        } else {
            boolean can = keysHave >= keysNeed && blockReason() == null;
            againBtn = addRenderableWidget(Button.builder(Component.literal("Rouvrir  (" + keysHave + " clé" + (keysHave > 1 ? "s" : "") + ")"), b -> open())
                    .bounds(px + pw / 2 - 80, by, 160, 20).build());
            againBtn.active = can;
            backBtn = addRenderableWidget(Button.builder(Component.literal("Aperçu"), b -> back()).bounds(px + 8, by, 68, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(px + pw - 76, by, 68, 20).build());
        }
    }

    private String keyName() {
        return Ui.comp(Ui.str(Ui.obj(data, "key"), "name", "")).getString();
    }

    /** Raison pour laquelle l'ouverture est impossible (null = possible). */
    private String blockReason() {
        if (!data.has("pos") || Ui.bool(data, "remote", false)) return "Aperçu à distance";
        if (Ui.bool(data, "maintenance", false) && !Ui.bool(data, "bypass", false)) return "En maintenance";
        if (!Ui.bool(data, "perm", true)) return "Permission requise";
        if (rewards.isEmpty()) return "Aucune récompense";
        if (keysHave < keysNeed) return "Clé manquante (" + keysHave + "/" + keysNeed + ")";
        return null;
    }

    private void open() {
        JsonArray pos = Ui.arr(data, "pos");
        if (pos.size() < 3) return;
        JsonObject a = new JsonObject();
        a.addProperty("crate", crateId());
        a.addProperty("x", pos.get(0).getAsInt());
        a.addProperty("y", pos.get(1).getAsInt());
        a.addProperty("z", pos.get(2).getAsInt());
        Ui.send("crate_open", a);
        if (openBtn != null) openBtn.active = false;
        if (againBtn != null) againBtn.active = false;
    }

    /** Auto-test : bouton d'ouverture. */
    void testOpen() {
        open();
    }

    private void skip() {
        Ui.send("crate_skip");
        spinStart = System.currentTimeMillis() - spinMs - 1;
    }

    private void back() {
        JsonArray pos = Ui.arr(data, "pos");
        JsonObject a = new JsonObject();
        a.addProperty("crate", crateId());
        if (pos.size() >= 3) {
            a.addProperty("x", pos.get(0).getAsInt());
            a.addProperty("y", pos.get(1).getAsInt());
            a.addProperty("z", pos.get(2).getAsInt());
        }
        Ui.send("crate_preview", a);
        mode = Mode.PREVIEW;
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ Animation

    private double progress() {
        if (spinMs <= 0) return 1;
        return Math.max(0, Math.min(1, (System.currentTimeMillis() - spinStart) / (double) spinMs));
    }

    private static double ease(double t) {
        return 1 - Math.pow(1 - t, 4);
    }

    @Override
    public void tick() {
        super.tick();
        if (mode == Mode.SPIN && progress() >= 1) {
            mode = Mode.RESULT;
            resultAt = System.currentTimeMillis();
            playReveal();
            rebuild();
        }
    }

    private void playReveal() {
        String rid = Ui.str(Ui.obj(result, "rarity"), "id", "commun");
        switch (rid) {
            case "legendaire" -> Ui.playUi(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.6f);
            case "epique" -> Ui.playUi(SoundEvents.PLAYER_LEVELUP, 1.2f, 0.6f);
            default -> Ui.playUi(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);
        }
    }

    private void tickSound(int idx, double t) {
        if (idx != lastTick) {
            if (lastTick != Integer.MIN_VALUE) Ui.playUi(tickSound, (float) (0.9 + 0.6 * t), 0.45f);
            lastTick = idx;
        }
    }

    // ------------------------------------------------------------------ Rendu

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph, Ui.PANEL, Ui.BORDER);
        Ui.hgradient(g, px + 1, py + 1, pw - 2, 30, Ui.argb(0x70, accent), Ui.argb(0x00, accent));
        g.fill(px + 1, py + 31, px + pw - 1, py + 32, Ui.argb(0xC0, accent));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        header(g);
        switch (mode) {
            case PREVIEW -> preview(g, mouseX, mouseY);
            case SPIN, RESULT -> {
                if (instant) instantResult(g);
                else if ("roue".equals(anim)) wheel(g);
                else roulette(g);
            }
        }
        if (!toast.isEmpty() && System.currentTimeMillis() < toastUntil) {
            int w = Ui.font().width(toast) + 12;
            int x = px + (pw - w) / 2;
            int y = py + ph - 44;
            g.fill(x, y, x + w, y + 14, toastOk ? 0xE0183A22 : 0xE03A1818);
            Ui.centered(g, toast, px + pw / 2, y + 3, toastOk ? Ui.OK : Ui.ERROR);
        }
    }

    private void header(GuiGraphics g) {
        Component name = Ui.comp(Ui.str(data, "name", ""));
        g.pose().pushPose();
        g.pose().translate(px + 10, py + 6, 0);
        g.pose().scale(1.25f, 1.25f, 1f);
        g.drawString(font, name, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        Component desc = Ui.comp(Ui.str(data, "desc", ""));
        g.drawString(font, desc, px + 10, py + 20, Ui.MUTED, false);
        // Cle requise
        JsonObject k = Ui.obj(data, "key");
        ItemStack key = Ui.item(Ui.str(k, "icon", ""));
        String count = keysHave + " / " + keysNeed;
        int cw = font.width(count);
        int kx = px + pw - 10 - cw;
        Ui.item(g, key, kx - 20, py + 7, 1f, false);
        Ui.text(g, count, kx, py + 12, keysHave >= keysNeed ? Ui.OK : Ui.ERROR);
        if (Ui.bool(data, "maintenance", false)) {
            String m = Ui.bool(data, "globalMaintenance", false) ? "⚠ Maintenance globale" : "⚠ Caisse en maintenance";
            if (Ui.bool(data, "bypass", false)) m += " (contournement)";
            int mw = font.width(m) + 10;
            g.fill(px + pw / 2 - mw / 2, py + 34, px + pw / 2 + mw / 2, py + 46, 0xC0501818);
            Ui.centered(g, m, px + pw / 2, py + 36, 0xFFFF9C9C);
        }
    }

    // -------- Apercu

    private int gridTop() {
        return py + (Ui.bool(data, "maintenance", false) ? 50 : 40);
    }

    private int cols() {
        return Math.max(1, (pw - 20) / CELL_W);
    }

    private int maxScroll() {
        int rows = (rewards.size() + cols() - 1) / cols();
        int h = py + ph - 34 - gridTop();
        return Math.max(0, rows * CELL_H - h);
    }

    private void preview(GuiGraphics g, int mouseX, int mouseY) {
        int cols = cols();
        int gx = px + (pw - cols * CELL_W) / 2;
        int top = gridTop();
        int bottom = py + ph - 34;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        JsonObject hovered = null;
        g.enableScissor(px + 2, top, px + pw - 2, bottom);
        for (int i = 0; i < rewards.size(); i++) {
            JsonObject r = rewards.get(i);
            int cx = gx + (i % cols) * CELL_W;
            int cy = top + (i / cols) * CELL_H - (int) scroll;
            if (cy + CELL_H < top || cy > bottom) continue;
            int rc = (int) Ui.lng(Ui.obj(r, "rarity"), "color", 0xB0B8C4);
            boolean hov = debugHover >= 0 ? debugHover == i : Ui.in(mouseX, mouseY, cx + 2, cy + 2, CELL_W - 4, CELL_H - 4) && mouseY >= top && mouseY < bottom;
            card(g, cx + 2, cy + 2, CELL_W - 4, CELL_H - 4, rc, hov);
            Ui.icon(g, Ui.item(Ui.str(r, "icon", "")), cx + CELL_W / 2, cy + 19, 1.5f, 1.5f, true);
            String pct = Ui.str(r, "pct", "");
            Ui.scaled(g, pct, cx + CELL_W / 2 - (int) (font.width(pct) * 0.75f / 2), cy + 37, hov ? 0xFFFFFFFF : Ui.lighten(rc, 0.35f) | 0xFF000000, 0.75f);
            if (hov) hovered = r;
        }
        g.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int h = bottom - top;
            int th = Math.max(14, h * h / (h + max));
            int ty = top + (int) ((h - th) * (scroll / max));
            g.fill(px + pw - 5, top, px + pw - 3, bottom, 0x30FFFFFF);
            g.fill(px + pw - 5, ty, px + pw - 3, ty + th, 0xA0FFFFFF);
        }
        if (rewards.isEmpty()) Ui.centered(g, "Aucune récompense", px + pw / 2, top + 30, Ui.MUTED);
        if (hovered != null) {
            if (debugHover >= 0) {
                int hx = gx + (debugHover % cols) * CELL_W + CELL_W;
                int hy = top + (debugHover / cols) * CELL_H + 10;
                tooltip(g, hovered, hx, hy);
            } else {
                tooltip(g, hovered, mouseX, mouseY);
            }
        }
    }

    private void card(GuiGraphics g, int x, int y, int w, int h, int rarity, boolean hov) {
        g.fill(x, y, x + w, y + h, hov ? Ui.CARD_ON : Ui.CARD);
        for (int i = 0; i < 12; i++) g.fill(x + 1, y + h - 2 - i, x + w - 1, y + h - 1 - i, Ui.argb((int) (0x50 * (1 - i / 12f)), rarity));
        g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, Ui.argb(0xFF, rarity));
        Ui.frame(g, x, y, w, h, Ui.argb(hov ? 0xFF : 0x90, hov ? Ui.lighten(rarity, 0.3f) : Ui.mix(rarity, 0x2E3A4C, 0.5f)));
    }

    private void tooltip(GuiGraphics g, JsonObject r, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Ui.comp(Ui.str(r, "name", "")).getVisualOrderText());
        JsonObject rar = Ui.obj(r, "rarity");
        int rc = (int) Ui.lng(rar, "color", 0xB0B8C4);
        lines.add(Component.literal(Ui.str(rar, "name", "")).withStyle(s -> s.withColor(rc)).getVisualOrderText());
        lines.add(Component.literal("Chance : ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Ui.str(r, "pct", "")).withStyle(ChatFormatting.WHITE)).getVisualOrderText());
        for (JsonElement e : Ui.arr(r, "lore")) {
            for (FormattedCharSequence l : font.split(Ui.comp(e.getAsString()), 220)) lines.add(l);
        }
        if (Ui.bool(r, "annonce", false)) lines.add(Component.literal("✦ Annoncé à tout le serveur").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC).getVisualOrderText());
        g.renderTooltip(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mode == Mode.PREVIEW) {
            scroll -= sy * 24;
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // -------- Roulette

    private void roulette(GuiGraphics g) {
        int rx = px + 10;
        int rw = pw - 20;
        int rh = 84;
        int ry = py + 52;
        int pitch = CARD_W + CARD_GAP;
        double t = progress();
        double off = mode == Mode.RESULT ? stripTarget : stripTarget * ease(t);
        int marker = rx + rw / 2;
        if (mode == Mode.SPIN) tickSound((int) Math.floor((off + pitch / 2.0) / pitch), t);
        g.fill(rx, ry, rx + rw, ry + rh, 0xFF0B0E13);
        g.enableScissor(rx, ry, rx + rw, ry + rh);
        long now = System.currentTimeMillis();
        for (int i = 0; i < strip.size(); i++) {
            double cx = marker + i * pitch - off;
            int x = (int) Math.round(cx - CARD_W / 2.0);
            if (x + CARD_W < rx || x > rx + rw) continue;
            JsonObject r = strip.get(i);
            int rc = (int) Ui.lng(Ui.obj(r, "rarity"), "color", 0xB0B8C4);
            boolean win = mode == Mode.RESULT && i == STRIP_WIN;
            int y = ry + 6;
            int h = rh - 12;
            card(g, x, y, CARD_W, h, rc, win);
            if (win) {
                float pulse = (float) (0.5 + 0.5 * Math.sin((now - resultAt) / 140.0));
                for (int k = 1; k <= 3; k++) Ui.frame(g, x - k, y - k, CARD_W + 2 * k, h + 2 * k, Ui.argb((int) (0xC0 * pulse / k), Ui.lighten(rc, 0.4f)));
            }
            ItemStack icon = win && result != null ? Ui.item(Ui.str(result, "icon", "")) : Ui.item(Ui.str(r, "icon", ""));
            Ui.icon(g, icon, x + CARD_W / 2, y + 28, 2f, 1.5f, true);
        }
        // Fondus sur les bords
        Ui.hgradient(g, rx, ry, 60, rh, 0xF00B0E13, 0x000B0E13);
        Ui.hgradient(g, rx + rw - 60, ry, 60, rh, 0x000B0E13, 0xF00B0E13);
        g.disableScissor();
        Ui.frame(g, rx - 1, ry - 1, rw + 2, rh + 2, Ui.BORDER);
        // Repere central
        int mc = Ui.GOLD;
        g.fill(marker - 1, ry - 4, marker + 1, ry + rh + 4, mc);
        for (int i = 0; i < 5; i++) {
            g.fill(marker - 5 + i, ry - 8 + i, marker + 5 - i, ry - 7 + i, mc);
            g.fill(marker - 5 + i, ry + rh + 7 - i, marker + 5 - i, ry + rh + 8 - i, mc);
        }
        if (mode == Mode.SPIN) {
            Ui.centered(g, "Ouverture…", px + pw / 2, ry + rh + 16, Ui.MUTED);
        } else {
            resultPanel(g, ry + rh + 12);
        }
    }

    private void resultPanel(GuiGraphics g, int y) {
        if (result == null) return;
        int rc = (int) Ui.lng(Ui.obj(result, "rarity"), "color", 0xB0B8C4);
        Component name = Ui.comp(Ui.str(result, "name", ""));
        String rarity = Ui.str(Ui.obj(result, "rarity"), "name", "");
        int w = Math.max(font.width(name), font.width("Vous avez obtenu")) + 60;
        int x = px + (pw - w) / 2;
        int h = 46;
        g.fill(x, y, x + w, y + h, Ui.argb(0xD0, Ui.darken(rc, 0.8f)));
        Ui.frame(g, x, y, w, h, Ui.argb(0xFF, rc));
        float pulse = (float) (0.5 + 0.5 * Math.sin((System.currentTimeMillis() - resultAt) / 200.0));
        for (int k = 0; k < 6; k++) g.fill(x + 8 - k, y + 7 - k, x + 40 + k, y + 39 + k, Ui.argb((int) (0x22 * pulse), rc));
        Ui.icon(g, Ui.item(Ui.str(result, "icon", "")), x + 24, y + 23, 2f, 1.4f, true);
        Ui.text(g, "Vous avez obtenu", x + 46, y + 8, Ui.MUTED);
        g.drawString(font, name, x + 46, y + 20, 0xFFFFFFFF, true);
        Ui.scaled(g, rarity, x + 46, y + 33, Ui.argb(0xFF, rc), 0.75f);
    }

    private void instantResult(GuiGraphics g) {
        resultPanel(g, py + ph / 2 - 30);
    }

    // -------- Roue

    private void wheel(GuiGraphics g) {
        int n = wheel.size();
        if (n == 0) return;
        double t = progress();
        double phi = mode == Mode.RESULT ? wheelTarget : wheelTarget * ease(t);
        double theta = Math.PI * 2 / n;
        int top = py + 40;
        int bottom = py + ph - 86;
        int r = Math.max(30, Math.min((bottom - top) / 2 - 6, 96));
        int cx = px + pw / 2;
        int cy = top + (bottom - top) / 2;
        if (mode == Mode.SPIN) {
            double a = ((-phi) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
            tickSound((int) Math.floor(a / theta) + (int) Math.floor(phi / (Math.PI * 2)) * n, t);
        }
        g.flush();
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        // Anneau exterieur
        ring(m, cx, cy, r + 1, r + 5, Ui.argb(0xFF, accent));
        for (int k = 0; k < n; k++) {
            JsonObject seg = wheel.get(k);
            int rc = (int) Ui.lng(Ui.obj(seg, "rarity"), "color", 0xB0B8C4);
            boolean win = mode == Mode.RESULT && k == wheelWinner;
            int col = win ? Ui.argb(0xFF, Ui.mix(rc, 0x1A202A, 0.25f)) : Ui.argb(0xFF, Ui.mix(rc, 0x161B24, k % 2 == 0 ? 0.62f : 0.72f));
            wedge(m, cx, cy, r, k * theta + phi, (k + 1) * theta + phi, col);
            wedge(m, cx, cy, r, (k + 1) * theta + phi - 0.004, (k + 1) * theta + phi + 0.004, 0xFF0B0E13);
        }
        ring(m, cx, cy, 0, 18, 0xFF161B24);
        ring(m, cx, cy, 17, 19, Ui.argb(0xFF, accent));
        // Pointeur
        triangle(m, cx - 8, cy - r - 9, cx + 8, cy - r - 9, cx, cy - r + 7, Ui.GOLD);
        RenderSystem.enableCull();
        // Icones des segments
        for (int k = 0; k < n; k++) {
            double a = (k + 0.5) * theta + phi;
            int ix = (int) Math.round(cx + Math.sin(a) * r * 0.68);
            int iy = (int) Math.round(cy - Math.cos(a) * r * 0.68);
            JsonObject seg = wheel.get(k);
            boolean win = mode == Mode.RESULT && k == wheelWinner;
            ItemStack icon = win && result != null ? Ui.item(Ui.str(result, "icon", "")) : Ui.item(Ui.str(seg, "icon", ""));
            float sc = win ? 1.6f + 0.15f * (float) Math.sin((System.currentTimeMillis() - resultAt) / 150.0) : 1.25f;
            Ui.icon(g, icon, ix, iy, sc, 1.4f, false);
        }
        Ui.item(g, Ui.item(Ui.str(Ui.obj(data, "key"), "icon", "")), cx - 8, cy - 8, 1f, false);
        if (mode == Mode.RESULT) resultPanel(g, bottom + 8);
    }

    private static void wedge(Matrix4f m, float cx, float cy, float r, double a0, double a1, int argb) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(m, cx, cy, 0).setColor(argb);
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / 0.08));
        for (int i = steps; i >= 0; i--) {
            double a = a0 + (a1 - a0) * i / steps;
            b.addVertex(m, (float) (cx + Math.sin(a) * r), (float) (cy - Math.cos(a) * r), 0).setColor(argb);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void ring(Matrix4f m, float cx, float cy, float r0, float r1, int argb) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int steps = 72;
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI * 2 * i / steps;
            b.addVertex(m, (float) (cx + Math.sin(a) * r1), (float) (cy - Math.cos(a) * r1), 0).setColor(argb);
            b.addVertex(m, (float) (cx + Math.sin(a) * r0), (float) (cy - Math.cos(a) * r0), 0).setColor(argb);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void triangle(Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(m, x0, y0, 0).setColor(argb);
        b.addVertex(m, x2, y2, 0).setColor(argb);
        b.addVertex(m, x1, y1, 0).setColor(argb);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    static MutableComponent lit(String s) {
        return Component.literal(s);
    }
}
