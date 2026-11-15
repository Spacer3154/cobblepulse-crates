package fr.cobblepulse.crates.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.cobblepulse.crates.Crates;
import fr.cobblepulse.crates.config.BuilderConfig;
import fr.cobblepulse.crates.config.CrateDef;
import fr.cobblepulse.crates.config.Defaults;
import fr.cobblepulse.crates.config.GeneralConfig;
import fr.cobblepulse.crates.config.RewardDef;
import fr.cobblepulse.crates.core.ItemSerial;
import fr.cobblepulse.crates.core.Json;
import fr.cobblepulse.crates.core.TextUtil;
import fr.cobblepulse.crates.item.KeyType;
import fr.cobblepulse.crates.item.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Auto-test visuel (CI) : ouvre les ecrans avec des donnees de demonstration sur l'ecran titre et les capture. */
final class ClientSelfTest {
    private static int ticks;
    private static int start = -1;
    private static CrateScreen crate;
    private static AdminScreen admin;
    private static BuilderScreen builder;

    private ClientSelfTest() {}

    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::tick);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> Crates.LOG.info("[Crates][clienttest] {}", c.getString()));
    }

    private static String comp(Component c) {
        return Component.Serializer.toJson(c, Ui.regs());
    }

    private static String item(ItemStack s) {
        return ItemSerial.encode(s, Ui.regs());
    }

    private static JsonObject rarity(String id) {
        GeneralConfig.Rarete r = new GeneralConfig.Rarete();
        for (GeneralConfig.Rarete x : GeneralConfig.defaultRarities()) if (x.id.equals(id)) r = x;
        JsonObject o = new JsonObject();
        o.addProperty("id", r.id);
        o.addProperty("name", r.nom);
        o.addProperty("color", r.color());
        return o;
    }

    private static JsonObject crateView(CrateDef c, int have, boolean maintenance, boolean remote) {
        JsonObject o = new JsonObject();
        o.addProperty("id", c.id);
        o.addProperty("name", comp(TextUtil.legacy(c.nom)));
        o.addProperty("desc", comp(TextUtil.legacy(c.description)));
        o.addProperty("color", c.color());
        JsonObject k = new JsonObject();
        ItemStack key = Ui.itemById(c.cle);
        k.addProperty("icon", item(key));
        k.addProperty("name", comp(key.getHoverName()));
        k.addProperty("have", have);
        k.addProperty("need", c.clesRequises);
        o.add("key", k);
        o.addProperty("maintenance", maintenance);
        o.addProperty("globalMaintenance", false);
        o.addProperty("bypass", false);
        o.addProperty("perm", true);
        o.addProperty("anim", c.animation);
        o.addProperty("duration", 4.0);
        o.addProperty("skip", true);
        o.addProperty("tick", "minecraft:block.note_block.hat");
        o.addProperty("admin", true);
        o.addProperty("remote", remote);
        if (!remote) {
            JsonArray p = new JsonArray();
            p.add(10);
            p.add(64);
            p.add(-4);
            o.add("pos", p);
        }
        double total = c.totalWeight();
        List<RewardDef> list = new ArrayList<>(c.recompenses);
        list.sort(Comparator.comparingDouble(r -> -r.poids));
        JsonArray rewards = new JsonArray();
        for (RewardDef r : list) {
            JsonObject cfg = Json.CFG.toJsonTree(r).getAsJsonObject();
            JsonObject x = new JsonObject();
            x.addProperty("id", r.id);
            x.addProperty("type", r.type);
            ItemStack icon = ClientData.rewardIcon(cfg);
            if ("pokemon".equals(r.type) && icon.is(net.minecraft.world.item.Items.BARRIER)) icon = Ui.itemById("cobblemon:poke_ball");
            x.addProperty("icon", item(icon));
            GeneralConfig.Rarete rr = null;
            for (GeneralConfig.Rarete y : GeneralConfig.defaultRarities()) if (y.id.equals(r.rarete)) rr = y;
            int col = rr == null ? 0xB0B8C4 : rr.color();
            String label = r.nom.isBlank() ? ClientData.rewardLabel(cfg) : null;
            Component name = label == null ? TextUtil.legacy(r.nom) : Component.literal(label).withStyle(s -> s.withColor(col));
            x.addProperty("name", comp(name));
            JsonArray lore = new JsonArray();
            if ("pokemon_aleatoire".equals(r.type)) {
                lore.add(comp(Component.literal("Pool : " + r.aleatoire.pool).withStyle(net.minecraft.ChatFormatting.GRAY)));
                lore.add(comp(Component.literal("Niveau " + r.aleatoire.niveauMin + " à " + r.aleatoire.niveauMax).withStyle(net.minecraft.ChatFormatting.GRAY)));
                if (r.aleatoire.ivsParfaits > 0) lore.add(comp(Component.literal(r.aleatoire.ivsParfaits + " IV parfaits garantis").withStyle(net.minecraft.ChatFormatting.AQUA)));
            }
            x.add("lore", lore);
            double ch = r.poids / total;
            x.addProperty("chance", ch);
            double pct = ch * 100;
            String ps = (pct >= 10 ? String.format(Locale.FRANCE, "%.1f", pct) : String.format(Locale.FRANCE, "%.2f", pct)).replaceAll("0+$", "").replaceAll(",$", "") + " %";
            x.addProperty("pct", ps);
            x.add("rarity", rarity(r.rarete));
            x.addProperty("annonce", r.annonce);
            rewards.add(x);
        }
        o.add("rewards", rewards);
        return o;
    }

    private static JsonObject spin(JsonObject view, int winnerIdx, double duration) {
        JsonObject o = view.deepCopy();
        JsonObject w = o.getAsJsonArray("rewards").get(winnerIdx).getAsJsonObject();
        o.addProperty("winner", Ui.str(w, "id", ""));
        o.addProperty("seed", 42L);
        o.addProperty("instant", false);
        o.addProperty("duration", duration);
        JsonObject res = new JsonObject();
        res.addProperty("icon", Ui.str(w, "icon", ""));
        res.addProperty("name", Ui.str(w, "name", ""));
        res.add("rarity", Ui.obj(w, "rarity"));
        o.add("result", res);
        return o;
    }

    static JsonObject demoAdmin() {
        JsonObject o = new JsonObject();
        JsonArray crates = new JsonArray();
        int i = 0;
        for (CrateDef c : Defaults.crates()) {
            JsonObject x = Json.CFG.toJsonTree(c).getAsJsonObject();
            x.addProperty("_id", c.id);
            JsonArray links = new JsonArray();
            if (i < 4) {
                JsonObject l = new JsonObject();
                l.addProperty("monde", "minecraft:overworld");
                l.addProperty("x", 120 + i * 3);
                l.addProperty("y", 64);
                l.addProperty("z", -40);
                l.addProperty("caisse", c.id);
                links.add(l);
            }
            if (i == 2) x.addProperty("maintenance", true);
            x.add("_links", links);
            crates.add(x);
            i++;
        }
        o.add("crates", crates);
        JsonArray pools = new JsonArray();
        String[][] pd = {{"all", "Tous les Pokémon", "1025"}, {"legendary", "Légendaires et fabuleux", "87"}, {"ultra_beasts", "Ultra-Chimères", "11"},
                {"fakemons", "Fakemons", "0"}};
        for (String[] p : pd) {
            JsonObject x = new JsonObject();
            x.addProperty("id", p[0]);
            x.addProperty("name", p[1]);
            x.addProperty("count", Integer.parseInt(p[2]));
            pools.add(x);
        }
        o.add("pools", pools);
        GeneralConfig g = new GeneralConfig();
        g.normalize();
        o.add("general", Json.CFG.toJsonTree(g));
        BuilderConfig b = new BuilderConfig();
        b.normalize();
        o.add("builder", Json.CFG.toJsonTree(b));
        JsonArray keys = new JsonArray();
        for (KeyType k : KeyType.values()) keys.add(k.itemId());
        o.add("keys", keys);
        JsonObject perms = new JsonObject();
        perms.addProperty("editor", true);
        perms.addProperty("toggle", true);
        perms.addProperty("unset", true);
        o.add("perms", perms);
        o.addProperty("dim", "minecraft:overworld");
        return o;
    }

    private static JsonObject demoMon(String name, String species, int level, boolean shiny, String nature, int[] ivs, int[] evs) {
        JsonObject m = new JsonObject();
        m.addProperty("uuid", java.util.UUID.nameUUIDFromBytes(name.getBytes()).toString());
        m.addProperty("where", "party");
        m.addProperty("icon", item(Ui.itemById("cobblemon:poke_ball")));
        m.addProperty("name", comp(Component.literal(name)));
        m.addProperty("species", comp(Component.literal(species)));
        m.addProperty("level", level);
        m.addProperty("shiny", shiny);
        JsonObject n = new JsonObject();
        n.addProperty("id", nature);
        n.addProperty("name", comp(Component.literal(nature.substring(0, 1).toUpperCase() + nature.substring(1))));
        m.add("nature", n);
        JsonObject ab = new JsonObject();
        ab.addProperty("id", "flashfire");
        ab.addProperty("name", comp(Component.literal("Torche")));
        ab.addProperty("hidden", false);
        m.add("ability", ab);
        JsonObject ha = new JsonObject();
        ha.addProperty("id", "drought");
        ha.addProperty("name", comp(Component.literal("Sécheresse")));
        m.add("hiddenAbility", ha);
        JsonArray abs = new JsonArray();
        abs.add(ab);
        m.add("abilities", abs);
        m.addProperty("form", "Normale");
        JsonArray forms = new JsonArray();
        for (String[] f : new String[][]{{"Normal", "Normale", "true"}, {"Alola", "Alola", "false"}}) {
            JsonObject fo = new JsonObject();
            fo.addProperty("name", f[0]);
            fo.addProperty("label", f[1]);
            fo.addProperty("current", Boolean.parseBoolean(f[2]));
            forms.add(fo);
        }
        m.add("forms", forms);
        JsonObject iv = new JsonObject();
        JsonObject ev = new JsonObject();
        for (int i = 0; i < 6; i++) {
            iv.addProperty(ClientData.STATS.get(i), ivs[i]);
            ev.addProperty(ClientData.STATS.get(i), evs[i]);
        }
        m.add("ivs", iv);
        m.add("evs", ev);
        m.addProperty("locked", false);
        return m;
    }

    static JsonObject demoBuilder() {
        JsonObject o = new JsonObject();
        o.addProperty("wishes", 12);
        o.addProperty("bypass", false);
        o.addProperty("canUse", true);
        o.addProperty("shinyReversible", true);
        JsonObject p = new JsonObject();
        p.addProperty("shiny", 5);
        p.addProperty("hidden", 3);
        p.addProperty("form", 2);
        p.addProperty("ability", 1);
        p.addProperty("nature", 1);
        p.addProperty("iv", 1);
        p.addProperty("evs", 1);
        o.add("prices", p);
        o.addProperty("currency", item(new ItemStack(ModItems.VOEU_ADN)));
        JsonArray party = new JsonArray();
        party.add(demoMon("Goupix", "Goupix", 24, false, "hardy", new int[]{12, 31, 8, 22, 17, 30}, new int[]{4, 252, 0, 0, 0, 252}));
        party.add(demoMon("Évoli", "Évoli", 18, true, "timid", new int[]{31, 31, 31, 5, 9, 31}, new int[]{0, 0, 0, 0, 0, 0}));
        party.add(demoMon("Dracolosse", "Dracolosse", 61, false, "adamant", new int[]{20, 25, 31, 2, 14, 27}, new int[]{252, 252, 4, 0, 0, 0}));
        party.add(new JsonObject());
        party.add(new JsonObject());
        party.add(new JsonObject());
        o.add("party", party);
        o.addProperty("pc", true);
        o.addProperty("boxes", 30);
        JsonArray natures = new JsonArray();
        String[][] ns = {{"hardy", "Hardi", "", ""}, {"lonely", "Solo", "atk", "def"}, {"brave", "Brave", "atk", "spe"}, {"adamant", "Rigide", "atk", "spa"},
                {"naughty", "Mauvais", "atk", "spd"}, {"bold", "Assuré", "def", "atk"}, {"docile", "Docile", "", ""}, {"relaxed", "Relax", "def", "spe"},
                {"impish", "Malin", "def", "spa"}, {"lax", "Lâche", "def", "spd"}, {"timid", "Timide", "spe", "atk"}, {"hasty", "Pressé", "spe", "def"},
                {"serious", "Sérieux", "", ""}, {"jolly", "Jovial", "spe", "spa"}, {"naive", "Naïf", "spe", "spd"}, {"modest", "Modeste", "spa", "atk"},
                {"mild", "Doux", "spa", "def"}, {"quiet", "Discret", "spa", "spe"}, {"bashful", "Pudique", "", ""}, {"rash", "Foufou", "spa", "spd"},
                {"calm", "Calme", "spd", "atk"}, {"gentle", "Gentil", "spd", "def"}, {"sassy", "Malpoli", "spd", "spe"}, {"careful", "Prudent", "spd", "spa"},
                {"quirky", "Bizarre", "", ""}};
        for (String[] n : ns) {
            JsonObject x = new JsonObject();
            x.addProperty("id", n[0]);
            x.addProperty("name", comp(Component.literal(n[1])));
            x.addProperty("plus", n[2]);
            x.addProperty("minus", n[3]);
            natures.add(x);
        }
        o.add("natures", natures);
        return o;
    }

    /** Planche des 11 objets (textures, noms, infobulles). */
    static final class ItemsScreen extends Screen {
        ItemsScreen() {
            super(Component.literal("Objets"));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            List<ItemStack> items = new ArrayList<>();
            for (KeyType k : KeyType.values()) items.add(new ItemStack(ModItems.KEYS.get(k)));
            items.add(new ItemStack(ModItems.VOEU_ADN, 16));
            int colW = Math.min(290, (width - 30) / 2);
            int x0 = (width - colW * 2 - 10) / 2;
            int y0 = 8;
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                int col = i % 2;
                int row = i / 2;
                int x = x0 + col * (colW + 10);
                int y = y0 + row * 38;
                Ui.panel(g, x, y, colW, 36, Ui.PANEL, Ui.BORDER);
                Ui.item(g, s, x + 4, y + 2, 2f, true);
                g.renderItem(s, x + 44, y + 10);
                g.drawString(font, s.getHoverName(), x + 66, y + 6, 0xFFFFFFFF, true);
                List<Component> tip = s.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY, null, net.minecraft.world.item.TooltipFlag.NORMAL);
                if (tip.size() > 1) g.drawString(font, Ui.cut(tip.get(1).getString(), colW - 72), x + 66, y + 20, 0xFF8E9AAB, false);
            }
        }
    }

    // ------------------------------------------------------------------ Test de bout en bout (connecte au serveur)

    private static int liveStart = -1;

    static void installLive() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::liveTick);
    }

    private static net.minecraft.core.BlockPos crateAt(int index) {
        List<net.minecraft.core.BlockPos> l = new ArrayList<>();
        for (Long v : fr.cobblepulse.crates.crate.Links.CLIENT_POSITIONS) l.add(net.minecraft.core.BlockPos.of(v));
        l.sort(Comparator.comparingInt(net.minecraft.core.BlockPos::getX));
        return l.isEmpty() ? null : l.get(Math.min(index, l.size() - 1));
    }

    private static void look(Minecraft mc, net.minecraft.core.BlockPos pos) {
        if (pos == null || mc.player == null) return;
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        double dx = c.x - eye.x;
        double dy = c.y - eye.y;
        double dz = c.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.setYHeadRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    private static void click(Minecraft mc, net.minecraft.core.BlockPos pos) {
        if (pos == null || mc.gameMode == null || mc.player == null) {
            Crates.LOG.error("[Crates][livetest] aucune caisse connue du client");
            return;
        }
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.NORTH, pos, false);
        var r = mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        Crates.LOG.info("[Crates][livetest] clic sur {} : {}", pos.toShortString(), r);
    }

    private static void liveTick(Minecraft mc) {
        ticks++;
        if (liveStart < 0) {
            if (mc.player != null && mc.level != null && fr.cobblepulse.crates.crate.Links.CLIENT_POSITIONS.size() >= 2) {
                liveStart = ticks + 160;
                Crates.LOG.info("[Crates][livetest] connecte, {} caisses connues", fr.cobblepulse.crates.crate.Links.CLIENT_POSITIONS.size());
            } else if (ticks > 14000) {
                Crates.LOG.error("[Crates][livetest] connexion jamais etablie");
                mc.stop();
            }
            return;
        }
        int t = ticks - liveStart;
        if (t < 0) return;
        try {
            switch (t) {
                case 0 -> {
                    if (mc.screen != null) mc.setScreen(null);
                    look(mc, crateAt(0));
                }
                case 20 -> shot(mc, "live_monde");
                case 22 -> click(mc, crateAt(0));
                case 60 -> shot(mc, "live_apercu");
                case 62 -> {
                    if (mc.screen instanceof CrateScreen s) s.testOpen();
                    else Crates.LOG.error("[Crates][livetest] apercu non ouvert ({})", mc.screen);
                }
                case 100 -> shot(mc, "live_roulette");
                case 200 -> shot(mc, "live_roulette_resultat");
                case 202 -> mc.setScreen(null);
                case 214 -> shot(mc, "live_hologramme");
                case 250 -> look(mc, crateAt(1));
                case 252 -> click(mc, crateAt(1));
                case 290 -> {
                    if (mc.screen instanceof CrateScreen s) s.testOpen();
                    else Crates.LOG.error("[Crates][livetest] apercu (roue) non ouvert ({})", mc.screen);
                }
                case 330 -> shot(mc, "live_roue");
                case 430 -> shot(mc, "live_roue_resultat");
                case 432 -> mc.setScreen(null);
                case 444 -> shot(mc, "live_hologramme_roue");
                case 450 -> Ui.send("builder_open");
                case 490 -> shot(mc, "live_voeu");
                case 492 -> {
                    if (mc.screen instanceof BuilderScreen b) b.testOp("form", "Alola");
                }
                case 510 -> shot(mc, "live_voeu_forme");
                case 512 -> {
                    if (mc.screen instanceof BuilderScreen b) b.testConfirm();
                }
                case 550 -> shot(mc, "live_voeu_apres");
                case 552 -> Ui.send("admin_open");
                case 590 -> shot(mc, "live_editeur");
                case 620 -> {
                    Crates.LOG.info("[Crates][livetest] fin");
                    mc.stop();
                }
                default -> {
                }
            }
        } catch (Throwable e) {
            Crates.LOG.error("[Crates][livetest] erreur a t={}", t, e);
        }
    }

    private static void tick(Minecraft mc) {
        ticks++;
        if (start < 0) {
            if (mc.getOverlay() == null && ((mc.screen instanceof TitleScreen && ticks > 60) || (mc.screen != null && ticks > 400))) {
                start = ticks;
                Crates.LOG.info("[Crates][clienttest] debut sur {}", mc.screen.getClass().getSimpleName());
            } else if (ticks > 6000) {
                Crates.LOG.error("[Crates][clienttest] ecran titre jamais atteint");
                mc.stop();
            }
            return;
        }
        int t = ticks - start;
        try {
            List<CrateDef> defs = Defaults.crates();
            switch (t) {
                case 20 -> mc.setScreen(new ItemsScreen());
                case 40 -> shot(mc, "cpc_objets");
                case 42 -> {
                    crate = new CrateScreen(crateView(defs.get(0), 3, false, false));
                    mc.setScreen(crate);
                }
                case 70 -> shot(mc, "cpc_apercu");
                case 72 -> crate.debugHover = 7;
                case 90 -> shot(mc, "cpc_apercu_survol");
                case 92 -> {
                    crate.debugHover = -1;
                    crate.startSpin(spin(crateView(defs.get(0), 2, false, false), 7, 5.0));
                }
                case 128 -> shot(mc, "cpc_roulette");
                case 210 -> shot(mc, "cpc_roulette_resultat");
                case 212 -> {
                    crate = new CrateScreen(crateView(defs.get(1), 1, false, false));
                    mc.setScreen(crate);
                    crate.startSpin(spin(crateView(defs.get(1), 0, false, false), 6, 5.0));
                }
                case 246 -> shot(mc, "cpc_roue");
                case 330 -> shot(mc, "cpc_roue_resultat");
                case 332 -> {
                    crate = new CrateScreen(crateView(defs.get(9), 0, true, true));
                    mc.setScreen(crate);
                }
                case 352 -> shot(mc, "cpc_maintenance");
                case 354 -> {
                    admin = new AdminScreen(demoAdmin());
                    mc.setScreen(admin);
                }
                case 380 -> shot(mc, "cpc_admin_recompenses");
                case 382 -> admin.testTab(0, 0, "obsidienne");
                case 400 -> shot(mc, "cpc_admin_caisse");
                case 402 -> admin.testTab(0, 2, "obsidienne");
                case 420 -> shot(mc, "cpc_admin_conteneurs");
                case 422 -> admin.testTab(1, 0, null);
                case 440 -> shot(mc, "cpc_admin_reglages");
                case 442 -> admin.testTab(2, 0, null);
                case 460 -> shot(mc, "cpc_admin_voeu");
                case 462 -> {
                    admin.testTab(0, 1, "temps");
                    JsonObject r = null;
                    for (com.google.gson.JsonElement e : Ui.arr(Ui.arr(admin.adminData(), "crates").get(7).getAsJsonObject(), "recompenses")) {
                        if ("pokemon_aleatoire".equals(Ui.str(e.getAsJsonObject(), "type", ""))) r = e.getAsJsonObject();
                    }
                    if (r != null) mc.setScreen(new RewardEditScreen(admin, r.deepCopy(), admin.adminData(), 100, x -> {}));
                }
                case 485 -> shot(mc, "cpc_admin_recompense");
                case 487 -> {
                    builder = new BuilderScreen(demoBuilder());
                    mc.setScreen(builder);
                }
                case 510 -> shot(mc, "cpc_voeu");
                case 512 -> builder.testOp("nature", "adamant");
                case 530 -> shot(mc, "cpc_voeu_nature");
                case 532 -> builder.testOp("evs", null);
                case 550 -> shot(mc, "cpc_voeu_ev");
                case 552 -> builder.testOp("ivs", null);
                case 570 -> shot(mc, "cpc_voeu_iv");
                case 600 -> {
                    Crates.LOG.info("[Crates][clienttest] fin");
                    mc.stop();
                }
                default -> {
                }
            }
        } catch (Throwable e) {
            Crates.LOG.error("[Crates][clienttest] erreur a t={}", t, e);
        }
    }
}
