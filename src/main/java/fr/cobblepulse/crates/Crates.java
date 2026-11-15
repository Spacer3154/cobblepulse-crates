package fr.cobblepulse.crates;

import fr.cobblepulse.crates.command.Commands;
import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.core.Scheduler;
import fr.cobblepulse.crates.crate.CrateService;
import fr.cobblepulse.crates.crate.Interaction;
import fr.cobblepulse.crates.crate.Links;
import fr.cobblepulse.crates.crate.OpenLog;
import fr.cobblepulse.crates.crate.WorldFx;
import fr.cobblepulse.crates.integration.LuckPermsBridge;
import fr.cobblepulse.crates.item.ModItems;
import fr.cobblepulse.crates.net.Net;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CobblePulse Crates : caisses mysteres ouvertes avec des cles thematiques (roulette / roue, hologrammes, particules)
 * et Voeu ADN pour remodeler ses Pokemon.
 */
public final class Crates implements ModInitializer {
    public static final String MOD_ID = "cobblepulse_crates";
    public static final Logger LOG = LoggerFactory.getLogger("CobblePulse-Crates");
    private static MinecraftServer server;

    public static MinecraftServer server() {
        return server;
    }

    @Override
    public void onInitialize() {
        ModItems.register();
        Cfg.loadAll();
        Net.register();
        Interaction.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> Commands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(s -> {
            server = s;
            Cfg.loadAll();
            Links.rebuild();
            LOG.info("[Crates] {} caisses, {} pools, {} conteneurs lies. LuckPerms : {}", Cfg.CRATES.size(), Cfg.POOLS.size(),
                    Links.all().size(), LuckPermsBridge.present() ? "oui" : "non");
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            CrateService.shutdown();
            OpenLog.flush();
            Scheduler.clear();
            WorldFx.clear();
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            Scheduler.tick(s);
            CrateService.tick(s);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> Scheduler.later(10, () -> {
            if (!handler.player.hasDisconnected()) Net.sendPositions(handler.player);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> CrateService.onDisconnect(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> Net.sendPositions(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> Net.sendPositions(newPlayer));
        LOG.info("[Crates] Mod initialise");
    }
}
