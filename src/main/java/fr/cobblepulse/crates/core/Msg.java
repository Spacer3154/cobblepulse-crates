package fr.cobblepulse.crates.core;

import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.config.Cfg;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Messages configurables (messages.toml) avec variables %cle%. */
public final class Msg {
    private Msg() {}

    public static String raw(String key) {
        if (Cfg.messages == null) return key;
        String v = Cfg.messages.str(key, null);
        if (v == null) {
            Crates.LOG.warn("[Crates] Message manquant dans messages.toml : {}", key);
            return key;
        }
        return v;
    }

    public static String format(String key, Object... kv) {
        String s = raw(key);
        if (s.contains("%prefix_adn%")) s = s.replace("%prefix_adn%", raw("builder.prefix"));
        if (s.contains("%prefix%")) s = s.replace("%prefix%", raw("general.prefix"));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("%" + kv[i] + "%", String.valueOf(kv[i + 1]));
        }
        return s;
    }

    public static MutableComponent get(String key, Object... kv) {
        return TextUtil.legacy(format(key, kv));
    }

    /** Message dont une variable est un composant (nom d'objet traduit, Pokemon...). */
    public static MutableComponent withComponent(String key, String var, Component value, Object... kv) {
        String s = format(key, kv);
        String token = "%" + var + "%";
        int i = s.indexOf(token);
        if (i < 0) return TextUtil.legacy(s);
        MutableComponent out = Component.empty();
        out.append(TextUtil.legacy(s.substring(0, i)));
        out.append(value);
        out.append(TextUtil.legacy(s.substring(i + token.length())));
        return out;
    }

    public static boolean reachable(ServerPlayer p) {
        return p != null && p.connection != null && !Players.isTest(p.getUUID());
    }

    public static void send(ServerPlayer p, String key, Object... kv) {
        if (reachable(p)) p.sendSystemMessage(get(key, kv));
    }

    public static void send(CommandSourceStack src, String key, Object... kv) {
        src.sendSystemMessage(get(key, kv));
    }

    public static void broadcast(Component c) {
        MinecraftServer s = Crates.server();
        if (s != null) s.getPlayerList().broadcastSystemMessage(c, false);
    }

    public static void actionBar(ServerPlayer p, String key, Object... kv) {
        if (reachable(p)) p.displayClientMessage(get(key, kv), true);
    }
}
