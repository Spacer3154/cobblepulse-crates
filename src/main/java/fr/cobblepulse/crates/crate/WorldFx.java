package fr.cobblepulse.crates.crate;

import com.mojang.math.Transformation;
import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.config.CrateDef;
import fr.cobblepulse.crates.config.GeneralConfig;
import fr.cobblepulse.crates.config.Locations;
import fr.cobblepulse.crates.core.Scheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Effets dans le monde : couvercle du conteneur, hologramme temporaire, particules et sons. */
public final class WorldFx {
    public static final String HOLO_TAG = "cobblepulse_crates_holo";
    /** Hologrammes vivants (les autres, restes d'un arret brutal, sont supprimes au chargement). */
    private static final Set<UUID> LIVE_HOLOS = new HashSet<>();
    /** Nombre d'ouvertures en cours par conteneur (le couvercle reste ouvert tant qu'il y en a). */
    private static final Map<String, Integer> OPEN_LIDS = new HashMap<>();
    private static final Map<String, ParticleOptions> PARTICLE_CACHE = new HashMap<>();

    private WorldFx() {}

    // ------------------------------------------------------------------ Particules et sons

    /** "minecraft:flame", "flame", "dust #FF8800 1.5" ; null si vide ou inconnu. */
    public static ParticleOptions particle(String spec) {
        if (spec == null || spec.isBlank()) return null;
        String s = spec.trim();
        if (PARTICLE_CACHE.containsKey(s)) return PARTICLE_CACHE.get(s);
        ParticleOptions out = null;
        try {
            String[] parts = s.split("\\s+");
            String id = parts[0].toLowerCase(Locale.ROOT);
            if ((id.equals("dust") || id.equals("minecraft:dust")) && parts.length >= 2) {
                int rgb = Integer.parseInt(parts[1].replace("#", ""), 16);
                float size = parts.length >= 3 ? Float.parseFloat(parts[2]) : 1.0f;
                out = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f),
                        Math.max(0.1f, Math.min(4f, size)));
            } else {
                ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
                ParticleType<?> t = rl == null ? null : BuiltInRegistries.PARTICLE_TYPE.get(rl);
                if (t instanceof SimpleParticleType simple) out = simple;
            }
        } catch (Exception ignored) {
        }
        if (out == null) Crates.LOG.warn("[Crates] Particule inconnue ou non prise en charge : {}", s);
        PARTICLE_CACHE.put(s, out);
        return out;
    }

    public static void clearCaches() {
        PARTICLE_CACHE.clear();
    }

    public static SoundEvent sound(String id) {
        if (id == null || id.isBlank()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id.trim().contains(":") ? id.trim() : "minecraft:" + id.trim());
        if (rl == null) return null;
        SoundEvent e = BuiltInRegistries.SOUND_EVENT.get(rl);
        // Son de pack de ressources (non enregistre) : joue tel quel
        return e != null ? e : SoundEvent.createVariableRangeEvent(rl);
    }

    public static void playSound(ServerLevel level, BlockPos pos, String id, float volume, float pitch) {
        SoundEvent s = sound(id);
        if (s != null && level != null) level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s, SoundSource.BLOCKS, volume, pitch);
    }

    public static void burst(ServerLevel level, BlockPos pos, ParticleOptions p, int count, double spread, double speed) {
        if (level == null || p == null || count <= 0) return;
        level.sendParticles(p, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, count, spread, spread * 0.6, spread, speed);
    }

    /** Spirale de particules pendant l'animation. */
    public static void spiral(ServerLevel level, BlockPos pos, ParticleOptions p, long tick) {
        if (level == null || p == null) return;
        for (int i = 0; i < 2; i++) {
            double a = (tick * 0.45) + i * Math.PI;
            double r = 0.75;
            double y = pos.getY() + 0.3 + ((tick % 20) / 20.0) * 1.2;
            level.sendParticles(p, pos.getX() + 0.5 + Math.cos(a) * r, y, pos.getZ() + 0.5 + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** Particules d'ambiance autour des caisses proches des joueurs. */
    public static void idle(MinecraftServer server, long tick) {
        GeneralConfig.Particules cfg = Cfg.general.particules;
        if (!cfg.ambiance || tick % cfg.intervalleTicks != 0) return;
        double r2 = cfg.rayonJoueurs * cfg.rayonJoueurs;
        for (Locations.Loc l : Links.all()) {
            CrateDef c = Cfg.CRATES.get(l.caisse);
            if (c == null || c.particuleAttente.isBlank()) continue;
            ResourceLocation dim = ResourceLocation.tryParse(l.monde);
            if (dim == null) continue;
            ServerLevel level = null;
            for (ServerLevel lv : server.getAllLevels()) {
                if (lv.dimension().location().equals(dim)) {
                    level = lv;
                    break;
                }
            }
            if (level == null || !level.isLoaded(new BlockPos(l.x, l.y, l.z))) continue;
            boolean near = false;
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(l.x + 0.5, l.y + 0.5, l.z + 0.5) <= r2) {
                    near = true;
                    break;
                }
            }
            if (!near) continue;
            ParticleOptions p = particle(c.particuleAttente);
            if (p == null) continue;
            double a = (tick / (double) cfg.intervalleTicks) * 0.9;
            level.sendParticles(p, l.x + 0.5 + Math.cos(a) * 0.6, l.y + 1.05, l.z + 0.5 + Math.sin(a) * 0.6, 1, 0.05, 0.1, 0.05, 0.0);
        }
    }

    // ------------------------------------------------------------------ Couvercle

    private static String lidKey(ServerLevel level, BlockPos pos) {
        return Links.dim(level) + "|" + pos.asLong();
    }

    public static void openLid(ServerLevel level, BlockPos pos, CrateDef crate) {
        if (level == null || pos == null) return;
        String k = lidKey(level, pos);
        int n = OPEN_LIDS.getOrDefault(k, 0);
        OPEN_LIDS.put(k, n + 1);
        BlockState st = level.getBlockState(pos);
        if (n == 0) setLid(level, pos, st, true);
        if (!crate.sonOuverture.isBlank()) playSound(level, pos, crate.sonOuverture, 0.9f, 1.0f);
        else {
            SoundEvent s = containerSound(st.getBlock(), true);
            if (s != null) level.playSound(null, pos, s, SoundSource.BLOCKS, 0.6f, 0.95f);
        }
    }

    public static void closeLidLater(ServerLevel level, BlockPos pos, int delayTicks) {
        if (level == null || pos == null) return;
        String k = lidKey(level, pos);
        Scheduler.later(Math.max(1, delayTicks), () -> {
            int n = OPEN_LIDS.getOrDefault(k, 1) - 1;
            if (n > 0) {
                OPEN_LIDS.put(k, n);
                return;
            }
            OPEN_LIDS.remove(k);
            if (!level.isLoaded(pos)) return;
            BlockState st = level.getBlockState(pos);
            setLid(level, pos, st, false);
            SoundEvent s = containerSound(st.getBlock(), false);
            if (s != null) level.playSound(null, pos, s, SoundSource.BLOCKS, 0.5f, 1.0f);
        });
    }

    private static void setLid(ServerLevel level, BlockPos pos, BlockState st, boolean open) {
        Block b = st.getBlock();
        try {
            if (st.hasProperty(BlockStateProperties.OPEN)) {
                level.setBlock(pos, st.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
            } else if (b instanceof ChestBlock || b instanceof EnderChestBlock || b instanceof ShulkerBoxBlock) {
                level.blockEvent(pos, b, 1, open ? 1 : 0);
            }
        } catch (Throwable t) {
            Crates.LOG.debug("[Crates] Couvercle : {}", t.toString());
        }
    }

    private static SoundEvent containerSound(Block b, boolean open) {
        if (b instanceof BarrelBlock) return open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE;
        if (b instanceof EnderChestBlock) return open ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.ENDER_CHEST_CLOSE;
        if (b instanceof ShulkerBoxBlock) return open ? SoundEvents.SHULKER_BOX_OPEN : SoundEvents.SHULKER_BOX_CLOSE;
        if (b instanceof ChestBlock) return open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE;
        return null;
    }

    // ------------------------------------------------------------------ Hologramme

    /** Objet tournant + nom de la recompense au-dessus de la caisse, retires apres la duree configuree. */
    public static void hologram(ServerLevel level, BlockPos pos, ItemStack icon, Component name, int durationTicks) {
        if (level == null || pos == null) return;
        GeneralConfig.Hologramme h = Cfg.general.hologramme;
        double y = pos.getY() + h.hauteur;
        try {
            Display.ItemDisplay item = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
            item.setPos(pos.getX() + 0.5, y + 0.25, pos.getZ() + 0.5);
            item.setItemStack(icon.copyWithCount(1));
            item.setItemTransform(ItemDisplayContext.GROUND);
            item.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
            float s = (float) h.echelleObjet * 1.6f;
            item.setTransformation(new Transformation(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            item.setBrightnessOverride(new Brightness(15, 15));
            item.setViewRange(0.6f);
            item.addTag(HOLO_TAG);
            Display.TextDisplay text = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
            text.setPos(pos.getX() + 0.5, y - 0.05, pos.getZ() + 0.5);
            text.setText(name);
            text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
            text.setBackgroundColor(0x80101018);
            text.setLineWidth(220);
            text.setFlags(Display.TextDisplay.FLAG_SHADOW);
            text.setViewRange(0.6f);
            text.addTag(HOLO_TAG);
            LIVE_HOLOS.add(item.getUUID());
            LIVE_HOLOS.add(text.getUUID());
            level.addFreshEntity(item);
            level.addFreshEntity(text);
            // Petite montee + rotation douce (interpolee cote client)
            Scheduler.later(2, () -> {
                if (item.isRemoved()) return;
                item.setTransformationInterpolationDelay(0);
                item.setTransformationInterpolationDuration(Math.max(10, durationTicks - 4));
                item.setTransformation(new Transformation(new Vector3f(0, 0.25f, 0), new Quaternionf().rotateY((float) Math.PI * 2f - 0.01f),
                        new Vector3f(s, s, s), new Quaternionf()));
            });
            Scheduler.later(Math.max(10, durationTicks), () -> {
                remove(item);
                remove(text);
            });
        } catch (Throwable t) {
            Crates.LOG.warn("[Crates] Hologramme impossible : {}", t.toString());
        }
    }

    private static void remove(Entity e) {
        LIVE_HOLOS.remove(e.getUUID());
        if (!e.isRemoved()) e.discard();
    }

    /** Entite chargee : un hologramme orphelin (arret du serveur pendant l'affichage) est supprime. */
    public static void onEntityLoad(Entity e) {
        if (e instanceof Display && e.getTags().contains(HOLO_TAG) && !LIVE_HOLOS.contains(e.getUUID())) e.discard();
    }

    public static int liveHolograms() {
        return LIVE_HOLOS.size();
    }

    public static void clear() {
        LIVE_HOLOS.clear();
        OPEN_LIDS.clear();
        PARTICLE_CACHE.clear();
    }

    /** Recompense obtenue : particules et son de la rarete. */
    public static void reveal(ServerLevel level, BlockPos pos, GeneralConfig.Rarete rarity) {
        if (level == null || pos == null) return;
        burst(level, pos, particle(rarity.particule), rarity.quantite, 0.45, 0.08);
        playSound(level, pos, rarity.son, (float) rarity.volume, (float) rarity.tonalite);
    }

    public static List<String> knownParticleExamples() {
        return List.of("minecraft:flame", "minecraft:end_rod", "minecraft:totem_of_undying", "dust #FFD24A 1.2");
    }
}
