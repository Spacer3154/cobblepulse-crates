package fr.cobblepulse.crates.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.util.Tristate;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Appels directs a l'API LuckPerms. Ne jamais referencer cette classe si LuckPerms est absent. */
final class LuckPermsHook {
    private LuckPermsHook() {}

    static Boolean check(ServerPlayer p, String node) {
        LuckPerms lp = LuckPermsProvider.get();
        User user = lp.getUserManager().getUser(p.getUUID());
        if (user == null) return null;
        Tristate t = user.getCachedData().getPermissionData().checkPermission(node);
        return t == Tristate.UNDEFINED ? null : t.asBoolean();
    }

    static void set(UUID uuid, String node, Boolean value) throws Exception {
        LuckPerms lp = LuckPermsProvider.get();
        lp.getUserManager().modifyUser(uuid, user -> {
            user.data().clear(n -> n.getKey().equalsIgnoreCase(node));
            if (value != null) user.data().add(Node.builder(node).value(value).build());
        }).get(10, TimeUnit.SECONDS);
        User u = lp.getUserManager().getUser(uuid);
        if (u != null) u.getCachedData().invalidate();
    }
}
