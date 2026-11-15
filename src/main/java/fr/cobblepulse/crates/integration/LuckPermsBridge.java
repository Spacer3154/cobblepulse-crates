package fr.cobblepulse.crates.integration;

import fr.cobblepulse.crates.Crates;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/** Facade sans dependance dure vers LuckPerms (les classes LP ne sont touchees que si le mod est present). */
public final class LuckPermsBridge {
    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("luckperms");
    private static boolean warned;

    private LuckPermsBridge() {}

    public static boolean present() {
        return PRESENT;
    }

    /** @return true/false si le noeud est defini (y compris via joker), null sinon ou si LuckPerms est absent. */
    public static Boolean check(ServerPlayer p, String node) {
        if (!PRESENT || p == null) return null;
        try {
            return LuckPermsHook.check(p, node);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Crates.LOG.warn("[Crates] API LuckPerms indisponible : {}", t.toString());
            }
            return null;
        }
    }

    /** Auto-test : definit un noeud pour un joueur (null = retire). */
    public static void setForTest(java.util.UUID uuid, String node, Boolean value) {
        if (!PRESENT) return;
        try {
            LuckPermsHook.set(uuid, node, value);
        } catch (Throwable t) {
            Crates.LOG.warn("[Crates] LuckPerms (test) : {}", t.toString());
        }
    }
}
