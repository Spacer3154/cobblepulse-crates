package fr.cobblepulse.crates.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.builder.BuilderService;
import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.config.CrateDef;
import fr.cobblepulse.crates.config.Locations;
import fr.cobblepulse.crates.core.Msg;
import fr.cobblepulse.crates.core.Perms;
import fr.cobblepulse.crates.core.Reg;
import fr.cobblepulse.crates.core.TextUtil;
import fr.cobblepulse.crates.crate.CrateService;
import fr.cobblepulse.crates.crate.Links;
import fr.cobblepulse.crates.crate.Rewards;
import fr.cobblepulse.crates.item.KeyType;
import fr.cobblepulse.crates.item.ModItems;
import fr.cobblepulse.crates.net.Net;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /lootbox (caisses) et /cpbuilder (menu Voeu ADN). */
public final class Commands {
    private Commands() {}

    private static final SuggestionProvider<CommandSourceStack> CRATES = (ctx, b) -> SharedSuggestionProvider.suggest(Cfg.CRATES.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> KEYS = (ctx, b) -> {
        List<String> s = new ArrayList<>();
        for (KeyType k : KeyType.values()) s.add(k.shortName);
        for (String c : Cfg.CRATES.keySet()) if (!s.contains(c)) s.add(c);
        return SharedSuggestionProvider.suggest(s, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("lootbox")
                .then(literal("preview").requires(Perms.playerReq(Perms.PREVIEW))
                        .then(argument("nom", StringArgumentType.word()).suggests(CRATES).executes(Commands::preview)))
                .then(literal("set").requires(Perms.adminReq(Perms.ADMIN_SET))
                        .then(argument("nom", StringArgumentType.word()).suggests(CRATES).executes(Commands::set)))
                .then(literal("unset").requires(Perms.adminReq(Perms.ADMIN_UNSET))
                        .executes(ctx -> unset(ctx, null))
                        .then(argument("nom", StringArgumentType.word()).suggests(CRATES).executes(ctx -> unset(ctx, StringArgumentType.getString(ctx, "nom")))))
                .then(literal("editor").requires(Perms.adminReq(Perms.ADMIN_EDITOR)).executes(Commands::editor))
                .then(literal("toggle").requires(Perms.adminReq(Perms.ADMIN_TOGGLE))
                        .then(argument("nom", StringArgumentType.word()).suggests(CRATES).executes(Commands::toggle)))
                .then(literal("toggleall").requires(Perms.adminReq(Perms.ADMIN_TOGGLE)).executes(Commands::toggleAll))
                .then(literal("givekey").requires(Perms.adminReq(Perms.ADMIN_GIVEKEY))
                        .then(argument("joueur", EntityArgument.players())
                                .then(argument("cle", StringArgumentType.word()).suggests(KEYS)
                                        .executes(ctx -> giveKey(ctx, 1))
                                        .then(argument("quantite", IntegerArgumentType.integer(1, 6400))
                                                .executes(ctx -> giveKey(ctx, IntegerArgumentType.getInteger(ctx, "quantite")))))))
                .then(literal("givewish").requires(Perms.adminReq(Perms.ADMIN_GIVEWISH))
                        .then(argument("joueur", EntityArgument.players())
                                .executes(ctx -> giveWish(ctx, 1))
                                .then(argument("quantite", IntegerArgumentType.integer(1, 6400))
                                        .executes(ctx -> giveWish(ctx, IntegerArgumentType.getInteger(ctx, "quantite"))))))
                .then(literal("reload").requires(Perms.adminReq(Perms.ADMIN_RELOAD)).executes(Commands::reload))
                .then(literal("list").requires(Perms.adminReq(Perms.ADMIN_EDITOR)).executes(Commands::list))
                .then(literal("selftest").requires(src -> SelfTest.enabled() && src.hasPermission(2))
                        .executes(ctx -> SelfTest.run(ctx.getSource()))
                        .then(literal("persisted").executes(ctx -> SelfTest.persisted(ctx.getSource())))
                        .then(literal("report").executes(ctx -> SelfTest.report(ctx.getSource())))));
        d.register(literal("cpbuilder").requires(Perms.playerReq(Perms.BUILDER_OPEN)).executes(ctx -> {
            ServerPlayer p = player(ctx);
            if (p != null) BuilderService.open(p);
            return 1;
        }));
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) Msg.send(ctx.getSource(), "general.players_only");
        return p;
    }

    private static CrateDef crateArg(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "nom");
        CrateDef c = CrateService.crate(id);
        if (c == null) Msg.send(ctx.getSource(), "caisse.unknown", "crate", id);
        return c;
    }

    /** Conteneur vise (6 blocs). */
    private static BlockPos target(ServerPlayer p) {
        HitResult hit = p.pick(6.0, 1.0f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return null;
        return bh.getBlockPos();
    }

    public static boolean isContainer(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container || level.getBlockState(pos).getBlock() instanceof EnderChestBlock;
    }

    private static int preview(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        CrateDef c = crateArg(ctx);
        if (p == null || c == null) return 0;
        CrateService.preview(p, c.id, p.serverLevel(), null);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        CrateDef c = crateArg(ctx);
        if (p == null || c == null) return 0;
        BlockPos pos = target(p);
        if (pos == null || !isContainer(p.level(), pos)) {
            Msg.send(p, "caisse.not_container");
            return 0;
        }
        String cur = Links.crateAt(p.level(), pos);
        if (c.id.equals(cur)) {
            Msg.send(p, "caisse.already_linked", "crate", c.id);
            return 0;
        }
        Links.link(p.level(), pos, c.id);
        Net.syncAllPositions();
        Msg.send(p, "caisse.linked", "crate", c.id, "x", pos.getX(), "y", pos.getY(), "z", pos.getZ(), "world", Links.dim(p.level()),
                "previous", cur == null ? "" : cur);
        Crates.LOG.info("[Crates] {} lie {} {} a la caisse {}", p.getGameProfile().getName(), Links.dim(p.level()), pos.toShortString(), c.id);
        return 1;
    }

    private static int unset(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer p = ctx.getSource().getPlayer();
        BlockPos pos = p == null ? null : target(p);
        String at = pos == null ? null : Links.crateAt(p.level(), pos);
        if (at != null && (name == null || CrateDef.cleanId(name).equals(at))) {
            Links.unlink(Links.dim(p.level()), pos);
            Net.syncAllPositions();
            Msg.send(ctx.getSource(), "caisse.unlinked", "crate", at);
            return 1;
        }
        if (name == null) {
            Msg.send(ctx.getSource(), "caisse.not_linked_any");
            return 0;
        }
        if (at != null) {
            Msg.send(ctx.getSource(), "caisse.other_crate", "other", at, "crate", name);
            return 0;
        }
        String id = CrateDef.cleanId(name);
        int n = Links.unlinkAll(id);
        Net.syncAllPositions();
        if (n == 0) {
            Msg.send(ctx.getSource(), "caisse.not_linked", "crate", id);
            return 0;
        }
        Msg.send(ctx.getSource(), "caisse.unlinked_all", "crate", id, "count", n);
        return n;
    }

    private static int editor(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = player(ctx);
        if (p == null) return 0;
        if (!Net.openAdmin(p)) Msg.send(p, "general.no_client");
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) {
        CrateDef c = crateArg(ctx);
        if (c == null) return 0;
        c.maintenance = !c.maintenance;
        Cfg.saveCrate(c);
        Msg.send(ctx.getSource(), c.maintenance ? "caisse.toggled_on" : "caisse.toggled_off", "crate", c.id);
        return 1;
    }

    private static int toggleAll(CommandContext<CommandSourceStack> ctx) {
        Cfg.general.maintenanceGlobale = !Cfg.general.maintenanceGlobale;
        Cfg.saveGeneral();
        Msg.send(ctx.getSource(), Cfg.general.maintenanceGlobale ? "caisse.toggled_all_on" : "caisse.toggled_all_off");
        return 1;
    }

    /** Objet cle depuis un nom court (obsidienne...), un nom de caisse ou un identifiant d'objet. */
    public static Item keyItem(String arg) {
        KeyType k = KeyType.byShortName(arg);
        if (k != null) return ModItems.KEYS.get(k);
        CrateDef c = CrateService.crate(arg);
        if (c != null) return CrateService.keyItem(c);
        return null;
    }

    private static int giveKey(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "joueur");
        String arg = StringArgumentType.getString(ctx, "cle");
        Item key = keyItem(arg);
        if (key == null) {
            Msg.send(ctx.getSource(), "caisse.unknown_key", "key", arg);
            return 0;
        }
        String keyName = new ItemStack(key).getHoverName().getString();
        for (ServerPlayer t : targets) {
            Rewards.giveItem(t, new ItemStack(key, amount));
            Msg.send(t, "caisse.givekey_received", "count", amount, "key", keyName);
            Msg.send(ctx.getSource(), "caisse.givekey", "count", amount, "key", keyName, "player", t.getGameProfile().getName());
        }
        return targets.size();
    }

    private static int giveWish(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "joueur");
        Item wish = Reg.item(Cfg.builder.monnaie, ModItems.VOEU_ADN);
        for (ServerPlayer t : targets) {
            Rewards.giveItem(t, new ItemStack(wish, amount));
            Msg.send(t, "builder.givewish_received", "count", amount);
            Msg.send(ctx.getSource(), "builder.givewish", "count", amount, "player", t.getGameProfile().getName());
        }
        return targets.size();
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        Net.reload();
        Msg.send(ctx.getSource(), "general.reloaded", "crates", Cfg.CRATES.size(), "pools", Cfg.POOLS.size(), "links", Links.all().size());
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        Msg.send(ctx.getSource(), "caisse.list_header", "count", Cfg.CRATES.size(), "maint", Cfg.general.maintenanceGlobale ? " &c(maintenance globale)" : "");
        for (CrateDef c : Cfg.CRATES.values()) {
            List<Locations.Loc> locs = Links.forCrate(c.id);
            ctx.getSource().sendSystemMessage(TextUtil.legacy(Msg.format("caisse.list_entry", "crate", c.id, "name", c.nom, "rewards", c.recompenses.size(),
                    "links", locs.size(), "maint", c.maintenance ? " &c[maintenance]" : "")));
        }
        return 1;
    }
}
