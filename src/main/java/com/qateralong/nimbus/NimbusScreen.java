package com.qateralong.nimbus;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public class NimbusScreen extends Screen {
    private static final int ROW = 22;
    private static final int SIDEBAR = 128;
    private static final int PANEL_BG = 0xB0101418;
    private static final int SIDEBAR_BG = 0xC0080A0C;
    private static final int ACCENT = 0xFFE8891A;
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("nimbus", "icon.png");
    private static String tab = "general";
    private final Screen parent;
    private final List<Described> described = new ArrayList<>();
    private int scroll;
    private int contentHeight;
    private int contentLeft;
    private int contentWidth;
    private int panelLeft;

    private record Described(AbstractWidget widget, String key) {
    }

    public NimbusScreen(Screen parent) {
        super(Component.translatable("nimbus.title"));
        this.parent = parent;
    }

    static void openTab(Minecraft client, String name) {
        tab = name;
        client.setScreen(new NimbusScreen(null));
    }

    private boolean panel() {
        return this.width - SIDEBAR - this.contentWidth - 36 >= 150;
    }

    @Override
    protected void init() {
        this.described.clear();
        int y = 34;
        for (String name : NimbusOptions.PAGES) {
            Button button = Button.builder(Component.translatable("nimbus.tab." + name), b -> {
                tab = name;
                this.scroll = 0;
                this.rebuildWidgets();
            }).bounds(8, y, SIDEBAR - 16, 20).build();
            button.active = !tab.equals(name);
            this.addRenderableWidget(button);
            y += 22;
        }
        y += 18;
        java.util.List<String> presets = new java.util.ArrayList<>(java.util.List.of(Presets.NAMES));
        presets.add("custom");
        String current = presets.contains(NimbusConfig.get().preset) ? NimbusConfig.get().preset : "custom";
        this.addRenderableWidget(CycleButton.<String>builder(name -> Component.translatable("nimbus.preset." + name))
                .withValues(presets)
                .withInitialValue(current)
                .withTooltip(name -> Tooltip.create(Component.translatable("nimbus.preset." + name + ".tip")))
                .displayOnlyValue()
                .create(8, y, SIDEBAR - 16, 20, Component.translatable("nimbus.presets"), (b, name) -> {
                    if (!name.equals("custom")) {
                        Presets.apply(name);
                        this.rebuildWidgets();
                    }
                }));
        this.contentLeft = SIDEBAR + 12;
        this.contentWidth = Math.max(160, Math.min(300, this.width - SIDEBAR - 24));
        this.panelLeft = this.contentLeft + this.contentWidth + 12;
        boolean panel = this.panel();
        int top = 34 - this.scroll;
        List<NimbusOptions.Entry> entries = NimbusOptions.entries(tab, NimbusConfig.get());
        Options options = Minecraft.getInstance().options;
        for (int i = 0; i < entries.size(); i++) {
            int rowY = top + i * ROW;
            if (rowY < 30 || rowY > this.height - 52) {
                continue;
            }
            NimbusOptions.Entry entry = entries.get(i);
            AbstractWidget widget = null;
            String key = null;
            if (entry instanceof NimbusOptions.Slider slider) {
                widget = slider(slider, this.contentLeft, rowY, this.contentWidth);
                key = slider.key();
            } else if (entry instanceof NimbusOptions.Toggle toggle) {
                widget = CycleButton.onOffBuilder(toggle.get().getAsBoolean())
                        .create(this.contentLeft, rowY, this.contentWidth, 20, NimbusOptions.label(toggle.key()), (b, value) -> {
                            toggle.set().accept(value);
                            changed();
                        });
                key = toggle.key();
            } else if (entry instanceof NimbusOptions.Vanilla vanilla) {
                widget = vanilla.factory().create(options, this.contentLeft, rowY, this.contentWidth);
            }
            if (widget == null) {
                continue;
            }
            if (key != null) {
                if (panel) {
                    this.described.add(new Described(widget, key));
                } else {
                    widget.setTooltip(Tooltip.create(NimbusOptions.tip(key)));
                }
            }
            this.addRenderableWidget(widget);
        }
        this.contentHeight = entries.size() * ROW;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds(8, this.height - 28, SIDEBAR - 16, 20).build());
    }

    private static void changed() {
        NimbusConfig config = NimbusConfig.get();
        config.preset = "custom";
        config.save();
    }

    private AbstractSliderButton slider(NimbusOptions.Slider option, int x, int y, int width) {
        double range = option.max() - option.min();
        return new AbstractSliderButton(x, y, width, 20, Component.empty(), (option.get().getAsInt() - option.min()) / range) {
            {
                updateMessage();
            }

            private int current() {
                int raw = option.min() + (int) Math.round(this.value * range / option.step()) * option.step();
                return Math.max(option.min(), Math.min(option.max(), raw));
            }

            @Override
            protected void updateMessage() {
                this.setMessage(NimbusOptions.label(option.key()).copy().append(": ").append(NimbusOptions.format(option, current())));
            }

            @Override
            protected void applyValue() {
                option.set().accept(current());
            }

            @Override
            public void onRelease(double mouseX, double mouseY) {
                super.onRelease(mouseX, mouseY);
                changed();
            }
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, this.contentHeight - (this.height - 90));
        int next = Math.max(0, Math.min(max, this.scroll - (int) (scrollY * ROW)));
        if (next != this.scroll) {
            this.scroll = next;
            this.rebuildWidgets();
        }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, SIDEBAR, this.height, SIDEBAR_BG);
        graphics.fill(this.contentLeft - 6, 28, this.contentLeft + this.contentWidth + 6, this.height - 46, PANEL_BG);
        if (this.panel()) {
            graphics.fill(this.panelLeft, 28, this.width - 10, this.height - 46, PANEL_BG);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.blit(ICON, 8, 8, 16, 16, 0, 0, 256, 256, 256, 256);
        graphics.drawString(this.font, "Nimbus", 30, 12, 0xFFFFFFFF);
        graphics.fill(8, 27, SIDEBAR - 8, 28, ACCENT);
        int presetsY = 34 + NimbusOptions.PAGES.length * 22 + 6;
        graphics.drawString(this.font, Component.translatable("nimbus.presets"), 10, presetsY, 0xFFFFD27A);
        graphics.drawString(this.font, Component.translatable("nimbus.tab." + tab), this.contentLeft, 16, 0xFFFFFFFF);
        if (!this.panel()) {
            return;
        }
        for (Described entry : this.described) {
            if (entry.widget().isHovered()) {
                int x = this.panelLeft + 8;
                int y = 36;
                int wrap = this.width - 10 - x - 8;
                graphics.drawString(this.font, NimbusOptions.label(entry.key()), x, y, ACCENT);
                y += 14;
                for (FormattedCharSequence line : this.font.split(NimbusOptions.tip(entry.key()), wrap)) {
                    graphics.drawString(this.font, line, x, y, 0xFFD0D0D0);
                    y += 10;
                }
                break;
            }
        }
    }

    @Override
    public void onClose() {
        NimbusConfig.get().save();
        Minecraft.getInstance().options.save();
        this.minecraft.setScreen(this.parent);
    }
}
