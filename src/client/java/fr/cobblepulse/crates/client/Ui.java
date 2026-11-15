package fr.cobblepulse.crates.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import fr.cobblepulse.crates.core.ItemSerial;
import fr.cobblepulse.crates.core.Json;
import fr.cobblepulse.crates.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/** Couleurs, dessin et petites aides partagees par les ecrans du passe et de l'editeur. */
public final class Ui {
    public static final int BG = 0xE00C0F14;
    public static final int PANEL = 0xF0161B24;
    public static final int PANEL2 = 0xF01E2530;
    public static final int CARD = 0xFF1A202A;
    public static final int CARD_ON = 0xFF223044;
    public static final int BORDER = 0xFF2E3A4C;
    public static final int ACCENT = 0xFF5FC8FF;
    public static final int GOLD = 0xFFFFD24A;
    public static final int GOLD_DARK = 0xFF7A5A12;
    public static final int TEXT = 0xFFE8ECF2;
    public static final int MUTED = 0xFF8E9AAB;
    public static final int OK = 0xFF6BE07A;
    public static final int ERROR = 0xFFFF6B6B;
    public static final int PURPLE = 0xFFC48CFF;

    private static final Map<String, ItemStack> ITEM_CACHE = new HashMap<>();
    private static final Map<String, Component> COMP_CACHE = new HashMap<>();

    private Ui() {}

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Registres du client (connexion en cours) ou registres integres (ecran titre, auto-test). */
    public static HolderLookup.Provider regs() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) return mc.getConnection().registryAccess();
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    public static void clearCaches() {
        ITEM_CACHE.clear();
        COMP_CACHE.clear();
    }

    public static ItemStack item(String b64) {
        if (b64 == null || b64.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = ITEM_CACHE.get(b64);
        if (s == null) {
            s = ItemSerial.decode(b64, regs());
            if (ITEM_CACHE.size() > 4000) ITEM_CACHE.clear();
            ITEM_CACHE.put(b64, s);
        }
        return s;
    }

    public static Component comp(String json) {
        if (json == null || json.isEmpty()) return Component.empty();
        Component c = COMP_CACHE.get(json);
        if (c == null) {
            try {
                c = Component.Serializer.fromJson(json, regs());
            } catch (Exception e) {
                c = Component.literal(json);
            }
            if (c == null) c = Component.empty();
            if (COMP_CACHE.size() > 4000) COMP_CACHE.clear();
            COMP_CACHE.put(json, c);
        }
        return c;
    }

    /** Objet decrit en JSON (format des fichiers de recompenses) ; la quantite peut depasser 99. */
    public static ItemStack itemJson(JsonElement json) {
        if (json == null || json.isJsonNull()) return ItemStack.EMPTY;
        try {
            int count = 1;
            JsonElement parse = json;
            if (json.isJsonObject()) {
                JsonObject o = json.getAsJsonObject().deepCopy();
                if (o.has("count")) count = Math.max(1, o.get("count").getAsInt());
                o.addProperty("count", 1);
                parse = o;
            }
            ItemStack s = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, regs()), parse).result().orElse(ItemStack.EMPTY);
            if (!s.isEmpty()) s.setCount(count);
            return s;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static JsonElement encodeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        int count = stack.getCount();
        JsonElement e = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, regs()), stack.copyWithCount(1)).result().orElse(null);
        if (e != null && e.isJsonObject()) e.getAsJsonObject().addProperty("count", count);
        return e;
    }

    public static ItemStack itemById(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        String v = id.trim();
        ResourceLocation rl = ResourceLocation.tryParse(v.contains(":") ? v : "minecraft:" + v);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            rl = ResourceLocation.tryParse("cobblemon:" + (v.contains(":") ? v.substring(v.indexOf(':') + 1) : v));
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return new ItemStack(Items.BARRIER);
        }
        Item it = BuiltInRegistries.ITEM.get(rl);
        return new ItemStack(it);
    }

    public static void send(String action, JsonElement args) {
        if (!ClientPlayNetworking.canSend(Payloads.Action.TYPE)) return;
        ClientPlayNetworking.send(new Payloads.Action(action, args == null ? "{}" : Json.COMPACT.toJson(args)));
    }

    public static void send(String action) {
        send(action, new JsonObject());
    }

    // ------------------------------------------------------------------ Echelle d'interface

    /**
     * Reduit l'echelle d'interface tant que l'ecran fait moins de minW x minH (en pixels d'interface).
     * @return l'echelle d'origine a restaurer a la fermeture, ou -1 si rien n'a change.
     */
    public static int fitScale(net.minecraft.client.gui.screens.Screen screen, int minW, int minH, int saved) {
        com.mojang.blaze3d.platform.Window win = Minecraft.getInstance().getWindow();
        int cur = (int) win.getGuiScale();
        int s = cur;
        while (s > 1 && (win.getWidth() / s < minW || win.getHeight() / s < minH)) s--;
        if (s != cur) {
            if (saved < 0) saved = cur;
            win.setGuiScale(s);
            screen.width = win.getGuiScaledWidth();
            screen.height = win.getGuiScaledHeight();
        }
        return saved;
    }

    public static int restoreScale(int saved) {
        if (saved > 0) Minecraft.getInstance().getWindow().setGuiScale(saved);
        return -1;
    }

    // ------------------------------------------------------------------ Couleurs et sons

    public static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Melange de deux couleurs RGB (t = 0 : a, t = 1 : b). */
    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int darken(int rgb, float f) {
        return mix(rgb, 0x000000, f);
    }

    public static int lighten(int rgb, float f) {
        return mix(rgb, 0xFFFFFF, f);
    }

    public static void playUi(String id, float pitch, float volume) {
        try {
            net.minecraft.resources.ResourceLocation rl = id == null || id.isBlank() ? null
                    : net.minecraft.resources.ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
            if (rl == null) return;
            net.minecraft.sounds.SoundEvent ev = BuiltInRegistries.SOUND_EVENT.get(rl);
            if (ev == null) ev = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(rl);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ev, pitch, volume));
        } catch (Throwable ignored) {
        }
    }

    public static void playUi(net.minecraft.sounds.SoundEvent ev, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ev, pitch, volume));
    }

    // ------------------------------------------------------------------ Dessin

    public static void hgradient(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
        int steps = Math.max(1, Math.min(w, 32));
        for (int i = 0; i < steps; i++) {
            int x0 = x + i * w / steps;
            int x1 = x + (i + 1) * w / steps;
            float t = steps == 1 ? 0 : (float) i / (steps - 1);
            int a = (int) (((left >>> 24) & 255) * (1 - t) + ((right >>> 24) & 255) * t);
            int c = mix(left & 0xFFFFFF, right & 0xFFFFFF, t);
            g.fill(x0, y, x1, y + h, argb(a, c));
        }
    }

    /** Cadre de 1 px. */
    public static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, double pct, int color) {
        g.fill(x, y, x + w, y + h, 0xFF0B0E13);
        g.fill(x, y, x + w, y + 1, BORDER);
        int fw = (int) Math.round((w - 2) * Math.max(0, Math.min(1, pct)));
        if (fw > 0) g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, color);
    }

    public static void item(GuiGraphics g, ItemStack s, int x, int y, float scale, boolean decorations) {
        if (s == null || s.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(s, 0, 0);
        if (decorations) g.renderItemDecorations(font(), s, 0, 0);
        g.pose().popPose();
    }

    /**
     * Objet centre sur (cx, cy) a l'echelle donnee ; les icones de Pokemon (modele 3D, plus petit qu'un objet)
     * sont agrandies de {@code pokemonBoost}.
     */
    public static void icon(GuiGraphics g, ItemStack s, int cx, int cy, float scale, float pokemonBoost, boolean decorations) {
        if (s == null || s.isEmpty()) return;
        float sc = s.getItem() instanceof com.cobblemon.mod.common.item.PokemonItem ? scale * pokemonBoost : scale;
        g.pose().pushPose();
        g.pose().translate(cx - 8 * sc, cy - 8 * sc, 0);
        g.pose().scale(sc, sc, 1f);
        g.renderItem(s, 0, 0);
        if (decorations) g.renderItemDecorations(font(), s, 0, 0);
        g.pose().popPose();
    }

    public static String cut(String s, int width) {
        Font f = font();
        if (f.width(s) <= width) return s;
        return f.plainSubstrByWidth(s, Math.max(0, width - f.width("…"))) + "…";
    }

    public static void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font(), s, x, y, color, false);
    }

    public static void text(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font(), c, x, y, color, false);
    }

    public static void centered(GuiGraphics g, String s, int cx, int y, int color) {
        g.drawString(font(), s, cx - font().width(s) / 2, y, color, false);
    }

    public static void scaled(GuiGraphics g, String s, int x, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font(), s, 0, 0, color, false);
        g.pose().popPose();
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** Duree lisible : 3j 4h, 2h 5min, 45s. */
    public static String duration(long ms) {
        if (ms <= 0) return "0s";
        long s = ms / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (d > 0) return d + "j " + h + "h";
        if (h > 0) return h + "h " + m + "min";
        if (m > 0) return m + "min " + sec + "s";
        return sec + "s";
    }

    public static String num(long n) {
        String s = String.valueOf(Math.abs(n));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + b;
    }

    public static String str(JsonObject o, String k, String def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static long lng(JsonObject o, String k, long def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static double dbl(JsonObject o, String k, double def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean bool(JsonObject o, String k, boolean def) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    public static JsonObject obj(JsonObject o, String k) {
        if (o == null) return new JsonObject();
        if (!o.has(k) || !o.get(k).isJsonObject()) o.add(k, new JsonObject());
        return o.getAsJsonObject(k);
    }

    public static com.google.gson.JsonArray arr(JsonObject o, String k) {
        if (o == null) return new com.google.gson.JsonArray();
        if (!o.has(k) || !o.get(k).isJsonArray()) o.add(k, new com.google.gson.JsonArray());
        return o.getAsJsonArray(k);
    }
}
