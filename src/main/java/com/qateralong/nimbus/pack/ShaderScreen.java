package com.qateralong.nimbus.pack;

import com.qateralong.nimbus.NimbusConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

// Shader packs: one on/off switch, the packs as a list to pick from, changes applied with Apply or Done.
public class ShaderScreen extends Screen {
    private static final int ACCENT = 0xFFE8891A;
    private static final int TEXT = 0xFFE8EAED;
    private static final int TEXT_DIM = 0xFF8C929B;
    private static final int TEXT_OFF = 0xFF5C6168;
    private static final int STRIP = 0x90000000;
    private static final int HOVER = 0x20FFFFFF;
    private static final int LINE = 0x30FFFFFF;
    private static final int ROW = 20;
    private static final int BAR = 32;

    private final Screen parent;
    private final List<Button> buttons = new ArrayList<>();
    private List<String> packs = List.of();
    private boolean enabled;
    private String selected;
    private double scroll;
    private String lastPick;
    private long lastPickAt;
    private int left;
    private int right;
    private int listTop;

    private record Button(int x0, int y0, int x1, int y1, Runnable action) {
        boolean contains(double x, double y) {
            return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
        }
    }

    public ShaderScreen(Screen parent) {
        super(Component.translatable("nimbus.shaders.title"));
        this.parent = parent;
        NimbusConfig config = NimbusConfig.get();
        this.enabled = config.shaders;
        this.selected = config.shaderPack;
    }

    @Override
    protected void init() {
        this.packs = Pipeline.packNames();
        if ((this.selected == null || !this.packs.contains(this.selected)) && !this.packs.isEmpty()) {
            this.selected = this.packs.get(0);
        }
        int width = Math.min(this.width - 12, 420);
        this.left = (this.width - width) / 2;
        this.right = this.left + width;
        this.listTop = 64;
    }

    private boolean changed() {
        NimbusConfig config = NimbusConfig.get();
        // picking another pack while shaders stay off is not worth an Apply
        return this.enabled != config.shaders || this.enabled && this.selected != null && !this.selected.equals(config.shaderPack);
    }

    private void apply() {
        if (!this.changed()) {
            return;
        }
        NimbusConfig config = NimbusConfig.get();
        config.shaders = this.enabled;
        if (this.selected != null) {
            config.shaderPack = this.selected;
        }
        config.save();
        Pipeline.select(config.shaders ? config.shaderPack : "");
    }

    // a second click on the same pack within 300 ms turns shaders on with it at once
    private void pick(String pack) {
        long now = System.currentTimeMillis();
        boolean twice = pack.equals(this.lastPick) && now - this.lastPickAt < 300L;
        this.selected = pack;
        this.lastPick = pack;
        this.lastPickAt = now;
        if (twice) {
            this.enabled = true;
            this.apply();
        }
    }

    private boolean running(String pack) {
        NimbusConfig config = NimbusConfig.get();
        return Pipeline.get() != null && config.shaders && pack != null && pack.equals(config.shaderPack);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, 0x50000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.buttons.clear();
        graphics.drawString(this.font, this.title, this.left + 2, 13, TEXT, false);

        // the switch
        int y = 30;
        boolean hover = inside(mouseX, mouseY, this.left, y, this.right, y + ROW - 1);
        graphics.fill(this.left, y, this.right, y + ROW - 1, STRIP);
        if (hover) {
            graphics.fill(this.left, y, this.right, y + ROW - 1, HOVER);
        }
        boolean pending = this.enabled != NimbusConfig.get().shaders;
        if (pending) {
            graphics.fill(this.left, y, this.left + 2, y + ROW - 1, ACCENT);
        }
        graphics.drawString(this.font, Component.translatable("nimbus.shaders.enable"), this.left + 8, y + 6, pending ? ACCENT : TEXT, false);
        this.tick(graphics, this.right - 18, y, this.enabled, hover);
        this.buttons.add(new Button(this.left, y, this.right, y + ROW - 1, () -> this.enabled = !this.enabled));

        // caption
        Component caption = Component.translatable("nimbus.shaders.packs");
        graphics.drawString(this.font, caption, this.left + 2, this.listTop - 12, TEXT_DIM, false);
        graphics.fill(this.left + 8 + this.font.width(caption), this.listTop - 8, this.right, this.listTop - 7, LINE);

        // the list
        int bottom = this.height - BAR - 4;
        if (this.packs.isEmpty()) {
            int lineY = this.listTop + 6;
            for (FormattedCharSequence line : this.font.split(Component.translatable("nimbus.shaders.empty"), this.right - this.left - 16)) {
                graphics.drawString(this.font, line, this.left + 8, lineY, TEXT_DIM, false);
                lineY += this.font.lineHeight + 2;
            }
        } else {
            int max = Math.max(0, this.packs.size() * ROW - (bottom - this.listTop));
            this.scroll = Mth.clamp(this.scroll, 0, max);
            graphics.enableScissor(this.left, this.listTop, this.right, bottom);
            int rowY = this.listTop - (int) this.scroll;
            for (String pack : this.packs) {
                if (rowY + ROW > this.listTop && rowY < bottom) {
                    boolean rowHover = mouseY >= this.listTop && mouseY < bottom && inside(mouseX, mouseY, this.left, rowY, this.right, rowY + ROW - 1);
                    boolean chosen = pack.equals(this.selected);
                    graphics.fill(this.left, rowY, this.right, rowY + ROW - 1, STRIP);
                    if (chosen) {
                        graphics.fill(this.left, rowY, this.right, rowY + ROW - 1, 0x30FFFFFF);
                        graphics.fill(this.left, rowY, this.left + 2, rowY + ROW - 1, ACCENT);
                    } else if (rowHover) {
                        graphics.fill(this.left, rowY, this.right, rowY + ROW - 1, HOVER);
                    }
                    String name = pack.endsWith(".zip") ? pack.substring(0, pack.length() - 4) : pack;
                    int color = !this.enabled ? TEXT_DIM : chosen ? TEXT : 0xFFC8CCD2;
                    graphics.drawString(this.font, this.font.plainSubstrByWidth(name, this.right - this.left - 90), this.left + 8, rowY + 6, color, false);
                    if (this.running(pack)) {
                        Component active = Component.translatable("nimbus.shaders.active");
                        graphics.drawString(this.font, active, this.right - 8 - this.font.width(active), rowY + 6, ACCENT, false);
                    }
                    int top = Math.max(rowY, this.listTop);
                    int end = Math.min(rowY + ROW - 1, bottom);
                    this.buttons.add(new Button(this.left, top, this.right, end, () -> this.pick(pack)));
                }
                rowY += ROW;
            }
            graphics.disableScissor();
            if (max > 0) {
                int view = bottom - this.listTop;
                int bar = Math.max(16, view * view / (this.packs.size() * ROW));
                int barY = this.listTop + (int) ((view - bar) * (this.scroll / max));
                graphics.fill(this.right + 3, barY, this.right + 5, barY + bar, 0x50FFFFFF);
            }
        }

        // buttons: folder and pack options on the left, apply and done on the right
        int by = this.height - BAR + 7;
        int x = this.left;
        x = this.button(graphics, mouseX, mouseY, x, by, Component.translatable("nimbus.shaders.folder"), true, false, true,
                () -> Util.getPlatform().openFile(Pipeline.folder().toFile()));
        boolean options = this.running(this.selected);
        this.button(graphics, mouseX, mouseY, x + 6, by, Component.translatable("nimbus.option.shader_options"), options, false, true, () -> {
            Pipeline pipeline = Pipeline.get();
            if (pipeline != null) {
                this.minecraft.setScreen(new PackOptionsScreen(this, pipeline.options, ""));
            }
        });
        int x1 = this.button(graphics, mouseX, mouseY, this.right, by, CommonComponents.GUI_DONE, true, true, false, this::onClose);
        if (this.changed()) {
            this.button(graphics, mouseX, mouseY, x1 - 6, by, Component.translatable("nimbus.screen.apply"), true, false, false, this::apply);
        }
        if (!options && this.enabled && this.selected != null && inside(mouseX, mouseY, this.left, by, x1 - 6, by + 18) && this.changed()) {
            graphics.renderTooltip(this.font, Component.translatable("nimbus.shaders.apply_first"), mouseX, mouseY);
        }
    }

    private void tick(GuiGraphics graphics, int x0, int y, boolean on, boolean hover) {
        com.qateralong.nimbus.NimbusScreen.tickBox(graphics, x0 + 1, y, on ? 1.0F : 0.0F, hover ? TEXT : TEXT_DIM, ACCENT);
    }

    // fromLeft: x is the left edge; otherwise the right edge. Returns the opposite edge.
    private int button(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, Component text, boolean active, boolean primary, boolean fromLeft,
                       Runnable action) {
        int width = Math.max(56, this.font.width(text) + 16);
        int x0 = fromLeft ? x : x - width;
        int x1 = x0 + width;
        boolean hover = active && inside(mouseX, mouseY, x0, y, x1, y + 18);
        int background = !active ? 0x40000000 : primary ? (hover ? 0xFFF09A35 : ACCENT) : (hover ? 0xC0303030 : 0xC0000000);
        graphics.fill(x0, y, x1, y + 18, background);
        int color = !active ? TEXT_OFF : primary ? 0xFF1A1206 : TEXT;
        graphics.drawString(this.font, text, x0 + (width - this.font.width(text)) / 2, y + 5, color, false);
        if (active) {
            this.buttons.add(new Button(x0, y, x1, y + 18, action));
        }
        return fromLeft ? x1 : x0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = this.buttons.size() - 1; i >= 0; i--) {
                if (this.buttons.get(i).contains(mouseX, mouseY)) {
                    this.buttons.get(i).action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll -= scrollY * ROW * 2;
        return true;
    }

    @Override
    public void onClose() {
        this.apply();
        this.minecraft.setScreen(this.parent);
    }

    private static boolean inside(double x, double y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }
}
