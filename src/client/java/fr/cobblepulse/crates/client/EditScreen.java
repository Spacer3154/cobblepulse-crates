package fr.cobblepulse.crates.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Base des sous-editeurs (recompense, quete, Pokemon de chasse) : formulaire defilant + Valider / Annuler. */
public abstract class EditScreen extends Screen {
    protected final Screen parent;
    protected final JsonObject obj;
    private final Consumer<JsonObject> onSave;
    protected Form form;
    protected int px;
    protected int py;
    protected int pw;
    protected int ph;
    protected double keepScroll;
    protected String message = "";
    protected int messageColor = Ui.MUTED;

    protected EditScreen(String title, Screen parent, JsonObject obj, Consumer<JsonObject> onSave) {
        super(Component.literal(title));
        this.parent = parent;
        this.obj = obj;
        this.onSave = onSave;
    }

    /** Ajoute les champs du formulaire. */
    protected abstract void fields(Form f);

    /** Zone de droite reservee (apercu) en pixels. */
    protected int previewWidth() {
        return 0;
    }

    private int savedGuiScale = -1;

    /** Reduit l'echelle d'interface si l'ecran est trop petit (restauree a la fermeture). */
    private void fitGuiScale() {
        com.mojang.blaze3d.platform.Window win = minecraft.getWindow();
        int cur = (int) win.getGuiScale();
        int s = cur;
        while (s > 1 && (win.getWidth() / s < 480 || win.getHeight() / s < 280)) s--;
        if (s != cur) {
            if (savedGuiScale < 0) savedGuiScale = cur;
            win.setGuiScale(s);
            this.width = win.getGuiScaledWidth();
            this.height = win.getGuiScaledHeight();
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (savedGuiScale > 0) {
            minecraft.getWindow().setGuiScale(savedGuiScale);
            savedGuiScale = -1;
        }
    }

    @Override
    protected void init() {
        fitGuiScale();
        pw = Math.min(width - 16, 620);
        ph = height - 16;
        px = (width - pw) / 2;
        py = 8;
        rebuild();
    }

    protected void rebuild() {
        if (form != null) keepScroll = form.scroll();
        clearWidgets();
        form = new Form(this::addRenderableWidget, px + 6, py + 22, pw - 12 - previewWidth(), ph - 50);
        fields(form);
        form.setScroll(keepScroll);
        form.layout();
        addRenderableWidget(Button.builder(Component.literal("Valider"), b -> {
            onSave.accept(obj);
            minecraft.setScreen(parent);
        }).bounds(px + pw - 196, py + ph - 24, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Annuler"), b -> minecraft.setScreen(parent)).bounds(px + pw - 100, py + ph - 24, 92, 18).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph, Ui.PANEL, Ui.BORDER);
        g.fill(px + 1, py + 18, px + pw - 1, py + 19, Ui.BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Ui.text(g, title.getString(), px + 8, py + 6, Ui.GOLD);
        if (!message.isEmpty()) Ui.text(g, Ui.cut(message, pw - 220), px + 8, py + ph - 19, messageColor);
        extra(g, mouseX, mouseY);
        form.render(g, mouseX, mouseY);
    }

    /** Dessin additionnel (apercu). */
    protected void extra(GuiGraphics g, int mouseX, int mouseY) {}

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (form.mouseClicked(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (form.mouseScrolled(mx, my, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }
}
