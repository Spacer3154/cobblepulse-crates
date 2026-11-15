package fr.cobblepulse.crates.item;

import fr.cobblepulse.crates.Crates;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.EnumMap;
import java.util.Map;

/** Enregistrement des 10 cles, du Voeu ADN et de l'onglet creatif. */
public final class ModItems {
    public static final Map<KeyType, CrateKeyItem> KEYS = new EnumMap<>(KeyType.class);
    public static DnaWishItem VOEU_ADN;

    private ModItems() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Crates.MOD_ID, path);
    }

    public static void register() {
        for (KeyType k : KeyType.values()) {
            Rarity r = switch (k) {
                case COEUR_DRAGON, TEMPS, CELESTE -> Rarity.EPIC;
                case SOLAIRE, OMBRE, PROFONDEURS -> Rarity.RARE;
                default -> Rarity.UNCOMMON;
            };
            CrateKeyItem item = Registry.register(BuiltInRegistries.ITEM, id(k.itemPath()), new CrateKeyItem(k, new Item.Properties().stacksTo(64).rarity(r)));
            KEYS.put(k, item);
        }
        VOEU_ADN = Registry.register(BuiltInRegistries.ITEM, id("voeu_adn"), new DnaWishItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC)));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("onglet"), FabricItemGroup.builder()
                .icon(() -> new ItemStack(KEYS.get(KeyType.COEUR_DRAGON)))
                .title(Component.translatable("itemGroup.cobblepulse_crates"))
                .displayItems((params, out) -> {
                    for (KeyType k : KeyType.values()) out.accept(KEYS.get(k));
                    out.accept(VOEU_ADN);
                })
                .build());
    }

    public static boolean isKey(Item item) {
        return item instanceof CrateKeyItem;
    }
}
