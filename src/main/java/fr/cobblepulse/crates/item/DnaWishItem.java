package fr.cobblepulse.crates.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Voeu ADN : monnaie evenementielle depensee dans le menu de remodelage (/cpbuilder). */
public class DnaWishItem extends Item {
    private static final int[] IRIDESCENT = {0xFFB3E6, 0xC9B8FF, 0xA6E4FF, 0xA8FFD8, 0xFFF2A8, 0xFFC4A8};

    public DnaWishItem(Properties props) {
        super(props);
    }

    @Override
    public Component getName(ItemStack stack) {
        String name = Component.translatable(getDescriptionId()).getString();
        MutableComponent out = Component.empty();
        int n = Math.max(1, name.length());
        for (int i = 0; i < name.length(); i++) {
            float t = (float) i / n * (IRIDESCENT.length - 1);
            int a = IRIDESCENT[(int) Math.floor(t)];
            int b = IRIDESCENT[Math.min(IRIDESCENT.length - 1, (int) Math.floor(t) + 1)];
            float f = t - (float) Math.floor(t);
            int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
            int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
            int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
            int rgb = (r << 16) | (g << 8) | bl;
            out.append(Component.literal(String.valueOf(name.charAt(i))).withStyle(s -> s.withColor(rgb)));
        }
        return out;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("item.cobblepulse_crates.voeu_adn.usage").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
