package fr.cobblepulse.crates.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** Objet <-> texte (NBT compresse en Base64), pour l'affichage cote client. Utilisable cote serveur et client. */
public final class ItemSerial {
    private ItemSerial() {}

    public static String encode(ItemStack stack, HolderLookup.Provider regs) {
        if (stack == null || stack.isEmpty() || regs == null) return "";
        try {
            int count = stack.getCount();
            Tag t = stack.copyWithCount(Math.max(1, Math.min(99, count))).save(regs);
            CompoundTag root = new CompoundTag();
            root.put("i", t);
            root.putInt("n", count);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack decode(String data, HolderLookup.Provider regs) {
        if (data == null || data.isEmpty() || regs == null) return ItemStack.EMPTY;
        try {
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(data)), NbtAccounter.unlimitedHeap());
            ItemStack s = ItemStack.parseOptional(regs, root.getCompound("i"));
            if (!s.isEmpty() && root.contains("n")) s.setCount(Math.max(1, root.getInt("n")));
            return s;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
