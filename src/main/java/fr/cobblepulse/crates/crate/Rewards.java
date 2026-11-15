package fr.cobblepulse.crates.crate;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.config.Cfg;
import fr.cobblepulse.crates.config.GeneralConfig;
import fr.cobblepulse.crates.config.PoolDef;
import fr.cobblepulse.crates.config.RewardDef;
import fr.cobblepulse.crates.core.Json;
import fr.cobblepulse.crates.core.Reg;
import fr.cobblepulse.crates.core.TextUtil;
import fr.cobblepulse.crates.pokemon.FormTools;
import fr.cobblepulse.crates.pokemon.MonSpec;
import fr.cobblepulse.crates.pokemon.Names;
import fr.cobblepulse.crates.pokemon.PokemonFactory;
import fr.cobblepulse.crates.pokemon.SpeciesLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Affichage (icone, nom, details) et distribution des recompenses. */
public final class Rewards {
    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();
    private static int cacheRevision = -1;
    private static final Stats[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};

    private Rewards() {}

    public static HolderLookup.Provider regs() {
        MinecraftServer s = Crates.server();
        return s == null ? null : s.registryAccess();
    }

    // ------------------------------------------------------------------ Objets (JSON de Minecraft)

    /** Objet decrit en JSON ({"id", "count", "components"}) ; la quantite peut depasser 99. */
    public static ItemStack decodeItem(JsonElement json) {
        HolderLookup.Provider regs = regs();
        if (json == null || json.isJsonNull() || regs == null) return ItemStack.EMPTY;
        try {
            int count = 1;
            JsonElement parse = json;
            if (json.isJsonObject()) {
                JsonObject o = json.getAsJsonObject().deepCopy();
                if (o.has("count")) count = Math.max(1, o.get("count").getAsInt());
                o.addProperty("count", 1);
                parse = o;
            } else if (json.isJsonPrimitive()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", json.getAsString());
                o.addProperty("count", 1);
                parse = o;
            }
            ItemStack s = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, regs), parse).result().orElse(ItemStack.EMPTY);
            if (!s.isEmpty()) s.setCount(count);
            return s;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static JsonElement encodeItem(ItemStack stack) {
        HolderLookup.Provider regs = regs();
        if (stack == null || stack.isEmpty() || regs == null) return null;
        int count = stack.getCount();
        JsonElement e = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, regs), stack.copyWithCount(1)).result().orElse(null);
        if (e != null && e.isJsonObject()) e.getAsJsonObject().addProperty("count", count);
        return e;
    }

    /** Donne l'objet (en plusieurs piles si besoin) ; le surplus tombe aux pieds du joueur. */
    public static void giveItem(ServerPlayer p, ItemStack stack) {
        int left = stack.getCount();
        int max = Math.max(1, stack.getMaxStackSize());
        while (left > 0) {
            int n = Math.min(max, left);
            ItemStack part = stack.copyWithCount(n);
            p.getInventory().add(part);
            if (!part.isEmpty()) p.drop(part, false);
            left -= n;
        }
        p.containerMenu.broadcastChanges();
    }

    public static void givePokemon(ServerPlayer p, Pokemon pk) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(p);
        if (!party.add(pk)) Cobblemon.INSTANCE.getStorage().getPC(p).add(pk);
    }

    public static void runCommands(MinecraftServer server, List<String> commands, ServerPlayer p) {
        if (server == null) return;
        String name = p.getGameProfile().getName();
        String uuid = p.getUUID().toString();
        for (String c : commands) {
            String cmd = c.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            cmd = cmd.replace("%player%", name).replace("%joueur%", name).replace("%uuid%", uuid);
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
            } catch (Throwable t) {
                Crates.LOG.warn("[Crates] Commande de recompense en echec '{}' : {}", cmd, t.toString());
            }
        }
    }

    // ------------------------------------------------------------------ Pokemon aleatoire

    /** Tire un Pokemon dans le pool de la recompense (niveau, chromatique, IV parfaits, forme). */
    public static Pokemon rollPokemon(RewardDef.RandomMon a, List<String> errors) {
        a.normalize();
        List<Species> list = Pools.species(a.pool);
        if (list.isEmpty()) {
            PoolDef def = Cfg.POOLS.get(a.pool);
            errors.add(def == null ? "pool inconnu : " + a.pool : "pool vide : " + a.pool);
            return null;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Species sp = list.get(rnd.nextInt(list.size()));
        int level = a.niveauMin + rnd.nextInt(a.niveauMax - a.niveauMin + 1);
        boolean shiny = a.chanceShiny > 0 && rnd.nextDouble() * 100.0 < a.chanceShiny;
        Pokemon pk;
        try {
            PokemonProperties pp = PokemonProperties.Companion.parse("level=" + level + " shiny=" + (shiny ? "yes" : "no"));
            pp.setSpecies(sp.getResourceIdentifier().toString());
            pk = pp.create();
            if (pk.getSpecies() != sp) pk.setSpecies(sp);
        } catch (Throwable t) {
            errors.add("création impossible (" + sp.getName() + ") : " + t.getMessage());
            return null;
        }
        if ("aleatoire".equals(a.formes)) {
            List<FormData> forms = FormTools.allowed(sp);
            if (forms.size() > 1) {
                FormData f = forms.get(rnd.nextInt(forms.size()));
                if (!FormTools.isStandard(sp, f)) FormTools.apply(pk, f);
            }
        }
        List<Stats> order = new ArrayList<>(List.of(STATS));
        Collections.shuffle(order);
        for (int i = 0; i < a.ivsParfaits; i++) pk.setIV(order.get(i), 31);
        pk.setShiny(shiny);
        pk.heal();
        return pk;
    }

    // ------------------------------------------------------------------ Distribution

    /** Recompense prete a etre donnee (preparee a l'ouverture pour connaitre le resultat exact). */
    public static final class Prepared {
        public final ItemStack icon;
        public final MutableComponent name;
        private final java.util.function.Consumer<ServerPlayer> giver;

        Prepared(ItemStack icon, MutableComponent name, java.util.function.Consumer<ServerPlayer> giver) {
            this.icon = icon;
            this.name = name;
            this.giver = giver;
        }

        public void give(ServerPlayer p) {
            giver.accept(p);
        }
    }

    /** Prepare la recompense ; null (et une erreur) si elle ne peut pas etre donnee. */
    public static Prepared prepare(RewardDef r, List<String> errors) {
        r.normalize();
        switch (r.type()) {
            case "objet" -> {
                ItemStack s = decodeItem(r.objet);
                if (s.isEmpty()) {
                    errors.add("objet invalide (" + (r.objet == null ? "vide" : r.objet.toString()) + ")");
                    return null;
                }
                return new Prepared(s.copy(), name(r), p -> giveItem(p, s.copy()));
            }
            case "commande" -> {
                if (r.commandes.isEmpty()) {
                    errors.add("aucune commande");
                    return null;
                }
                List<String> cmds = new ArrayList<>(r.commandes);
                return new Prepared(icon(r), name(r), p -> runCommands(p.getServer(), cmds, p));
            }
            case "pokemon" -> {
                List<String> errs = new ArrayList<>();
                Pokemon pk = PokemonFactory.create(Json.copy(r.pokemon, MonSpec.class), errs);
                if (pk == null) {
                    errors.addAll(errs);
                    return null;
                }
                if (!errs.isEmpty()) Crates.LOG.warn("[Crates] Recompense Pokemon {} : {}", r.pokemon.species, String.join(" | ", errs));
                return new Prepared(PokemonItem.from(pk), name(r), p -> givePokemon(p, pk));
            }
            case "pokemon_aleatoire" -> {
                Pokemon pk = rollPokemon(r.aleatoire, errors);
                if (pk == null) return null;
                return new Prepared(PokemonItem.from(pk), pokemonName(pk), p -> givePokemon(p, pk));
            }
            default -> {
                errors.add("type inconnu " + r.type);
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ Affichage

    public static void clearCaches() {
        ICON_CACHE.clear();
    }

    /** Icone de la recompense dans l'apercu (Poke Ball pour un Pokemon aleatoire). */
    public static ItemStack icon(RewardDef r) {
        if (cacheRevision != Cfg.revision) {
            ICON_CACHE.clear();
            cacheRevision = Cfg.revision;
        }
        r.normalize();
        switch (r.type()) {
            case "objet" -> {
                ItemStack s = decodeItem(r.objet);
                if (!r.icone.isBlank()) {
                    ItemStack i = Reg.stack(r.icone);
                    i.setCount(Math.max(1, Math.min(99, s.getCount())));
                    return i;
                }
                if (s.isEmpty()) return new ItemStack(Items.BARRIER);
                ItemStack d = s.copy();
                d.setCount(Math.max(1, Math.min(99, s.getCount())));
                return d;
            }
            case "pokemon" -> {
                if (!r.icone.isBlank()) return Reg.stack(r.icone);
                String key = Json.COMPACT.toJson(r.pokemon);
                ItemStack cached = ICON_CACHE.get(key);
                if (cached != null) return cached.copy();
                ItemStack out;
                try {
                    Pokemon pk = PokemonFactory.create(Json.copy(r.pokemon, MonSpec.class), new ArrayList<>());
                    out = pk == null ? new ItemStack(Items.BARRIER) : PokemonItem.from(pk);
                } catch (Throwable t) {
                    out = new ItemStack(Items.BARRIER);
                }
                ICON_CACHE.put(key, out.copy());
                return out;
            }
            case "pokemon_aleatoire" -> {
                return Reg.stack(r.icone.isBlank() ? "cobblemon:poke_ball" : r.icone);
            }
            default -> {
                return Reg.stack(r.icone.isBlank() ? "minecraft:paper" : r.icone);
            }
        }
    }

    public static MutableComponent pokemonName(Pokemon pk) {
        MutableComponent c = Component.empty().append(pk.getSpecies().getTranslatedName());
        if (!FormTools.isStandard(pk.getSpecies(), pk.getForm())) c.append(Component.literal(" (" + FormTools.label(pk.getSpecies(), pk.getForm()) + ")"));
        c.append(Component.literal(" Nv. " + pk.getLevel()));
        if (pk.getShiny()) c.append(Component.literal(" ★").withStyle(ChatFormatting.GOLD));
        return c;
    }

    /** Nom affiche (sans couleur de rarete). */
    public static MutableComponent name(RewardDef r) {
        if (!r.nom.isBlank()) return TextUtil.legacy(r.nom);
        switch (r.type()) {
            case "objet" -> {
                ItemStack s = decodeItem(r.objet);
                if (s.isEmpty()) return Component.literal("Objet invalide").withStyle(ChatFormatting.RED);
                MutableComponent c = Component.empty().append(s.getHoverName());
                return s.getCount() > 1 ? c.append(Component.literal(" ×" + s.getCount())) : c;
            }
            case "pokemon" -> {
                MonSpec m = r.pokemon;
                MutableComponent c = Component.empty();
                Species sp = SpeciesLookup.resolve(m.species);
                c.append(sp == null ? Component.literal(m.species) : sp.getTranslatedName());
                c.append(Component.literal(" Nv. " + m.level));
                if (m.shiny) c.append(Component.literal(" ★").withStyle(ChatFormatting.GOLD));
                return c;
            }
            case "pokemon_aleatoire" -> {
                PoolDef p = Cfg.POOLS.get(r.aleatoire.pool);
                return Component.literal("Pokémon aléatoire" + (p == null ? "" : " — " + TextUtil.strip(p.nom)));
            }
            default -> {
                return Component.literal("Récompense spéciale");
            }
        }
    }

    /** Nom colore selon la rarete. */
    public static MutableComponent coloredName(RewardDef r, MutableComponent base) {
        GeneralConfig.Rarete rar = Cfg.general.rarity(r.rarete);
        if (!r.nom.isBlank()) return base;
        return Component.empty().withStyle(s -> s.withColor(rar.color())).append(base);
    }

    public static String percent(double chance) {
        double pct = chance * 100.0;
        String s;
        if (pct >= 10) s = String.format(Locale.FRANCE, "%.1f", pct);
        else if (pct >= 1) s = String.format(Locale.FRANCE, "%.2f", pct);
        else s = String.format(Locale.FRANCE, "%.3f", pct);
        if (s.contains(",")) s = s.replaceAll("0+$", "").replaceAll(",$", "");
        return s + " %";
    }

    /** Details (infobulle). */
    public static List<MutableComponent> lore(RewardDef r) {
        List<MutableComponent> out = new ArrayList<>();
        for (String d : r.description) out.add(TextUtil.legacy(d));
        switch (r.type()) {
            case "pokemon" -> {
                MonSpec m = r.pokemon;
                if (m.shiny) out.add(Component.literal("Chromatique").withStyle(ChatFormatting.GOLD));
                if (!m.form.isBlank()) out.add(Component.literal("Forme : " + m.form).withStyle(ChatFormatting.GRAY));
                if (!m.nature.isBlank()) out.add(Component.literal("Nature : " + Names.nature(m.nature)).withStyle(ChatFormatting.GRAY));
                if (!m.ability.isBlank()) out.add(Component.literal("Talent : " + m.ability).withStyle(ChatFormatting.GRAY));
                StringBuilder iv = new StringBuilder();
                boolean anyIv = false;
                for (String s : MonSpec.STATS) {
                    int v = m.iv(s);
                    if (v >= 0) anyIv = true;
                    iv.append(iv.length() == 0 ? "" : " / ").append(v < 0 ? "?" : String.valueOf(v));
                }
                if (anyIv) out.add(Component.literal("IV : " + iv).withStyle(ChatFormatting.AQUA));
                if (!m.heldItem.isBlank()) out.add(Component.literal("Objet tenu : " + m.heldItem).withStyle(ChatFormatting.GRAY));
            }
            case "pokemon_aleatoire" -> {
                RewardDef.RandomMon a = r.aleatoire;
                PoolDef p = Cfg.POOLS.get(a.pool);
                out.add(Component.literal("Pool : " + (p == null ? a.pool : TextUtil.strip(p.nom)) + " (" + Pools.species(a.pool).size() + " espèces)")
                        .withStyle(ChatFormatting.GRAY));
                out.add(Component.literal("Niveau " + (a.niveauMin == a.niveauMax ? String.valueOf(a.niveauMin) : a.niveauMin + " à " + a.niveauMax))
                        .withStyle(ChatFormatting.GRAY));
                if (a.chanceShiny > 0) out.add(Component.literal("Chromatique : " + percent(a.chanceShiny / 100.0)).withStyle(ChatFormatting.GOLD));
                if (a.ivsParfaits > 0) out.add(Component.literal(a.ivsParfaits + " IV parfait" + (a.ivsParfaits > 1 ? "s" : "") + " garanti"
                        + (a.ivsParfaits > 1 ? "s" : "")).withStyle(ChatFormatting.AQUA));
                if ("aleatoire".equals(a.formes)) out.add(Component.literal("Formes régionales / variantes possibles").withStyle(ChatFormatting.GRAY));
            }
            default -> {
            }
        }
        return out;
    }
}
