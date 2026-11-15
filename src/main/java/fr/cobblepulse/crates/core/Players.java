package fr.cobblepulse.crates.core;

import fr.cobblepulse.crates.Crates;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Recherche de joueurs connectes (inclut les joueurs factices de l'auto-test). */
public final class Players {
    private static final Map<UUID, ServerPlayer> TEST_PLAYERS = new HashMap<>();

    private Players() {}

    public static ServerPlayer get(UUID uuid) {
        if (uuid == null) return null;
        MinecraftServer s = Crates.server();
        if (s == null) return null;
        ServerPlayer p = s.getPlayerList().getPlayer(uuid);
        if (p != null) return p;
        return TEST_PLAYERS.get(uuid);
    }

    public static ServerPlayer get(String uuid) {
        if (uuid == null) return null;
        try {
            return get(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean online(UUID uuid) {
        return get(uuid) != null;
    }

    public static void registerTest(ServerPlayer p) {
        TEST_PLAYERS.put(p.getUUID(), p);
    }

    public static void unregisterTest(UUID uuid) {
        TEST_PLAYERS.remove(uuid);
    }

    public static boolean isTest(UUID uuid) {
        return TEST_PLAYERS.containsKey(uuid);
    }
}
