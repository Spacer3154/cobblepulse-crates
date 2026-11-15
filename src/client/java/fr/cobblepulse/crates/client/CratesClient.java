package fr.cobblepulse.crates.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.crate.Links;
import fr.cobblepulse.crates.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Cote client : ecrans des caisses, editeur, menu Voeu ADN (touche N) et positions des caisses. */
public final class CratesClient implements ClientModInitializer {
    private static KeyMapping builderKey;

    @Override
    public void onInitializeClient() {
        builderKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblepulse_crates.builder", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N, "key.categories.cobblepulse_crates"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (builderKey.consumeClick()) {
                if (mc.player != null && mc.screen == null) Ui.send("builder_open");
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Data.TYPE, (payload, ctx) -> ctx.client().execute(() -> receive(payload.kind(), payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> Links.CLIENT_POSITIONS.clear());
        if ("true".equals(System.getProperty("cobblepulse_crates.clienttest"))) ClientSelfTest.install();
        if ("true".equals(System.getProperty("cobblepulse_crates.livetest"))) ClientSelfTest.installLive();
        Crates.LOG.info("[Crates] Client initialise");
    }

    static void receive(String kind, String json) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            Crates.LOG.warn("[Crates] Donnees illisibles ({}) : {}", kind, e.toString());
            return;
        }
        switch (kind) {
            case "positions" -> {
                Links.CLIENT_POSITIONS.clear();
                for (JsonElement e : Ui.arr(o, "pos")) Links.CLIENT_POSITIONS.add(e.getAsLong());
            }
            case "crate_preview" -> {
                if (mc.screen instanceof CrateScreen s && s.crateId().equals(Ui.str(o, "id", ""))) s.showPreview(o);
                else {
                    Ui.clearCaches();
                    mc.setScreen(new CrateScreen(o));
                }
            }
            case "crate_spin" -> {
                if (mc.screen instanceof CrateScreen s) s.startSpin(o);
                else {
                    CrateScreen s = new CrateScreen(o);
                    mc.setScreen(s);
                    s.startSpin(o);
                }
            }
            case "crate_done" -> {
                if (mc.screen instanceof CrateScreen s) s.onDone(o);
            }
            case "toast" -> {
                boolean ok = Ui.bool(o, "ok", false);
                String msg = Ui.str(o, "message", "");
                if (mc.screen instanceof Toaster t) t.toast(ok, msg);
                else if (mc.screen instanceof EditScreen e && e.parent instanceof Toaster t) t.toast(ok, msg);
                else if (mc.player != null) mc.player.displayClientMessage(Component.literal(msg), true);
            }
            case "admin_open" -> mc.setScreen(new AdminScreen(o));
            case "admin_data" -> {
                if (mc.screen instanceof AdminScreen s) s.refreshData(o);
                else if (mc.screen instanceof EditScreen e && e.parent instanceof AdminScreen s) s.refreshData(o);
            }
            case "admin_result" -> {
                if (mc.screen instanceof AdminScreen s) s.onResult(o);
            }
            case "pokemon_check" -> {
                if (mc.screen instanceof RewardEditScreen s) s.onCheck(o);
            }
            case "builder_open" -> mc.setScreen(new BuilderScreen(o));
            case "builder_data" -> {
                if (mc.screen instanceof BuilderScreen s) s.refresh(o);
            }
            case "builder_box" -> {
                if (mc.screen instanceof BuilderScreen s) s.onBox(o);
            }
            case "builder_result" -> {
                if (mc.screen instanceof BuilderScreen s) s.onResult(o);
            }
            default -> {
            }
        }
    }

    /** Ecran capable d'afficher une notification. */
    public interface Toaster {
        void toast(boolean ok, String message);
    }
}
