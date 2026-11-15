package fr.cobblepulse.crates.core;

import fr.cobblepulse.crates.integration.LuckPermsBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Permissions (LuckPerms). Sans LuckPerms, ou si le noeud n'est pas defini : noeuds joueurs autorises,
 * administration = operateurs niveau 3, contournements (bypass) = accordes explicitement seulement
 * (sauf contournement de maintenance : operateurs niveau 3).
 */
public final class Perms {
    public static final String ROOT = "cobblepulsecrates";
    public static final String PREVIEW = ROOT + ".command.preview";
    public static final String BUILDER_OPEN = ROOT + ".builder.open";
    public static final String BUILDER_USE = ROOT + ".builder.use";
    public static final String BUILDER_BYPASS = ROOT + ".builder.bypass";
    public static final String BYPASS_MAINTENANCE = ROOT + ".bypass.maintenance";
    public static final String CRATE_OPEN = ROOT + ".crate.open.";
    public static final String ADMIN = ROOT + ".admin";
    public static final String ADMIN_SET = ADMIN + ".set";
    public static final String ADMIN_UNSET = ADMIN + ".unset";
    public static final String ADMIN_EDITOR = ADMIN + ".editor";
    public static final String ADMIN_TOGGLE = ADMIN + ".toggle";
    public static final String ADMIN_GIVEKEY = ADMIN + ".givekey";
    public static final String ADMIN_GIVEWISH = ADMIN + ".givewish";
    public static final String ADMIN_RELOAD = ADMIN + ".reload";

    private Perms() {}

    /** Noeud joueur : autorise sauf refus explicite. */
    public static boolean player(ServerPlayer p, String node) {
        Boolean v = LuckPermsBridge.check(p, node);
        return v == null || v;
    }

    /** Noeud d'administration : le noeud precis, sinon le joker admin.*, sinon operateur niveau 3. */
    public static boolean admin(ServerPlayer p, String node) {
        if (p == null) return true;
        Boolean v = LuckPermsBridge.check(p, node);
        if (v != null) return v;
        Boolean all = LuckPermsBridge.check(p, ADMIN + ".*");
        return all != null ? all : p.hasPermissions(3);
    }

    public static boolean canOpen(ServerPlayer p, String crate) {
        return player(p, CRATE_OPEN + crate.toLowerCase(Locale.ROOT));
    }

    public static boolean bypassMaintenance(ServerPlayer p) {
        Boolean v = LuckPermsBridge.check(p, BYPASS_MAINTENANCE);
        return v != null ? v : p.hasPermissions(3);
    }

    /** Remodelage sans consommer de Voeu ADN : uniquement si le noeud est accorde. */
    public static boolean builderBypass(ServerPlayer p) {
        Boolean v = LuckPermsBridge.check(p, BUILDER_BYPASS);
        return v != null && v;
    }

    public static Predicate<CommandSourceStack> playerReq(String node) {
        return src -> !src.isPlayer() || player(src.getPlayer(), node);
    }

    public static Predicate<CommandSourceStack> adminReq(String node) {
        return src -> !src.isPlayer() || admin(src.getPlayer(), node);
    }

    public static boolean anyAdmin(ServerPlayer p) {
        return admin(p, ADMIN_EDITOR) || admin(p, ADMIN_SET) || admin(p, ADMIN_TOGGLE) || admin(p, ADMIN_RELOAD);
    }
}
