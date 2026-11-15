package fr.cobblepulse.crates.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Cle fantasy : consommable sans effet, sert uniquement a ouvrir la caisse associee. */
public class CrateKeyItem extends Item {
    private final KeyType key;

    public CrateKeyItem(KeyType key, Properties props) {
        super(props);
        this.key = key;
    }

    public KeyType key() {
        return key;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId()).withStyle(s -> s.withColor(key.color));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.cobblepulse_crates.key.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
