package fr.cobblepulse.crates.crate;

import fr.cobblepulse.crates.core.Msg;
import fr.cobblepulse.crates.core.TextUtil;
import fr.cobblepulse.crates.config.CrateDef;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** Clic gauche / clic droit sur un conteneur lie = meme ecran d'apercu ; conteneur protege contre la casse. */
public final class Interaction {
    private Interaction() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            if (world.isClientSide) return Links.CLIENT_POSITIONS.contains(pos.asLong()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            String crate = Links.crateAt(world, pos);
            if (crate == null) return InteractionResult.PASS;
            if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && world instanceof ServerLevel sl) {
                CrateService.click(sp, sl, pos, crate);
            }
            return InteractionResult.SUCCESS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (world.isClientSide) return Links.CLIENT_POSITIONS.contains(pos.asLong()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            String crate = Links.crateAt(world, pos);
            if (crate == null) return InteractionResult.PASS;
            if (player instanceof ServerPlayer sp && world instanceof ServerLevel sl) CrateService.click(sp, sl, pos, crate);
            return InteractionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            String crate = Links.crateAt(world, pos);
            if (crate == null) return true;
            if (player instanceof ServerPlayer sp) {
                CrateDef c = CrateService.crate(crate);
                Msg.send(sp, "caisse.protected", "crate", crate, "name", c == null ? crate : TextUtil.strip(c.nom));
            }
            return false;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> WorldFx.onEntityLoad(entity));
    }
}
