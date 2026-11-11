package com.qateralong.nimbus;

import com.qateralong.nimbus.api.ActionOption;
import com.qateralong.nimbus.api.CycleOption;
import com.qateralong.nimbus.api.ModOptions;
import com.qateralong.nimbus.api.NimbusOptionsApi;
import com.qateralong.nimbus.api.Option;
import com.qateralong.nimbus.api.OptionFlag;
import com.qateralong.nimbus.api.OptionGroup;
import com.qateralong.nimbus.api.OptionImpact;
import com.qateralong.nimbus.api.OptionPage;
import com.qateralong.nimbus.api.SliderOption;
import com.qateralong.nimbus.api.ToggleOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class NimbusScreen extends Screen {
    private static final int ACCENT = 0xFFE8891A;
    private static final int TEXT = 0xFFE8EAED;
    private static final int TEXT_DIM = 0xFF8C929B;
    private static final int TEXT_OFF = 0xFF5C6168;
    private static final int SIDEBAR_BG = 0xA0000000;
    private static final int STRIP = 0x90000000;
    private static final int HOVER = 0x20FFFFFF;
    private static final int LINE = 0x30FFFFFF;
    private static final int TRACK = 0xFF50555C;
    private static final int ROW = 20;
    private static final int GROUP_GAP = 10;
    private static final int TITLE = 14;
    private static final int BAR = 32;
    private static final Map<OptionGroup, Boolean> COLLAPSED = new IdentityHashMap<>();
    private static String lastMod = "nimbus";
    private static String lastPage = "general";

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private List<ModOptions> mods = List.of();
    private OptionPage page;
    private EditBox search;
    private String query = "";
    private double scroll;
    private double scrollTarget;
    private int contentHeight;
    private SliderOption dragging;
    private int dragX;
    private int dragWidth;
    private Option<?, ?> hovered;
    private Option<?, ?> focused;
    private Component notice;
    private final Map<Object, Float> hoverFade = new IdentityHashMap<>();
    private final Map<Object, Float> tickFade = new IdentityHashMap<>();
    private long lastFrame;
    private Option<?, ?> tooltipFor;
    private long tooltipSince;
    private float step = 1.0F;
    private float opening;
    private float switching = 1.0F;
    private float barY = Float.NaN;
    private int origin;
    private int panelRight;
    private int sidebar;
    private int describe;
    private int left;
    private int right;
    private int edge;

    private enum Kind {
        PAGE, SHADERS, GROUP, OPTION, RESET, RESET_PAGE, CONTROL_LEFT, CONTROL_RIGHT, SLIDER, UNDO, APPLY, DONE
    }

    private record Hit(Kind kind, int x0, int y0, int x1, int y1, Object target) {
        boolean contains(double x, double y) {
            return x >= this.x0 && x < this.x1 && y >= this.y0 && y < this.y1;
        }
    }

    public NimbusScreen(Screen parent) {
        super(Component.translatable("nimbus.title"));
        this.parent = parent;
    }

    public static void openTab(Minecraft client, String name) {
        lastMod = "nimbus";
        lastPage = name;
        client.setScreen(new NimbusScreen(null));
    }

    @Override
    protected void init() {
        this.mods = NimbusOptionsApi.mods();
        if (this.page == null) {
            this.page = this.findPage(lastMod, lastPage);
        }
        int total = Math.min(this.width - 12, 540);
        this.origin = (this.width - total) / 2;
        this.panelRight = this.origin + total;
        this.sidebar = Mth.clamp(total * 27 / 100, 100, 150);
        int available = total - this.sidebar - 20;
        this.describe = available >= 440 ? Mth.clamp(available * 32 / 100, 130, 180) : 0;
        this.left = this.origin + this.sidebar + 10;
        this.right = this.left + (this.describe > 0 ? available - this.describe - 14 : available);
        this.edge = this.panelRight - 10;
        this.search = new EditBox(this.font, this.origin + 12, 30, this.sidebar - 24, 14, Component.translatable("nimbus.screen.search"));
        this.search.setBordered(false);
        this.search.setHint(Component.translatable("nimbus.screen.search").withColor(TEXT_OFF));
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.query = text;
            this.scrollTarget = 0;
            this.scroll = 0;
        });
        this.addRenderableWidget(this.search);
    }

    private OptionPage findPage(String mod, String id) {
        for (ModOptions options : this.mods) {
            for (OptionPage candidate : options.pages()) {
                if (options.id().equals(mod) && candidate.id().equals(id)) {
                    return candidate;
                }
            }
        }
        for (ModOptions options : this.mods) {
            if (!options.pages().isEmpty()) {
                return options.pages().get(0);
            }
        }
        return null;
    }

    private boolean pageChanged() {
        for (OptionGroup group : this.page.groups()) {
            for (Option<?, ?> option : group.options()) {
                if (option.resettable() && !option.isDefault() && !(option instanceof ActionOption)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean pendingOn(OptionPage page) {
        for (OptionGroup group : page.groups()) {
            for (Option<?, ?> option : group.options()) {
                if (option.changed()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean searching() {
        return !this.query.isBlank();
    }

    private List<Option<?, ?>> all() {
        List<Option<?, ?>> list = new ArrayList<>();
        for (ModOptions mod : this.mods) {
            for (OptionPage candidate : mod.pages()) {
                for (OptionGroup group : candidate.groups()) {
                    list.addAll(group.options());
                }
            }
        }
        return list;
    }

    private int pending() {
        int count = 0;
        for (Option<?, ?> option : this.all()) {
            if (option.changed()) {
                count++;
            }
        }
        return count;
    }

    private boolean matches(Option<?, ?> option) {
        String needle = this.query.toLowerCase(Locale.ROOT).trim();
        if (option.name().getString().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return option.tooltip() != null && option.tooltip().getString().toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean collapsed(OptionGroup group) {
        return !this.searching() && COLLAPSED.computeIfAbsent(group, OptionGroup::collapsedByDefault);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, 0x50000000);
        graphics.fill(this.origin, 0, this.origin + this.sidebar, this.height, SIDEBAR_BG);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float seconds = this.lastFrame == 0 ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1.0E9F);
        this.lastFrame = now;
        boolean animated = animated();
        this.step = animated ? 1.0F - (float) Math.exp(-seconds * 16.0F) : 1.0F;
        this.opening = animated ? Math.min(1.0F, this.opening + seconds / 0.22F) : 1.0F;
        this.switching = animated ? Math.min(1.0F, this.switching + seconds / 0.18F) : 1.0F;
        this.scroll += (this.scrollTarget - this.scroll) * (animated ? 1.0F - (float) Math.exp(-seconds * 20.0F) : 1.0F);
        if (Math.abs(this.scrollTarget - this.scroll) < 0.5) {
            this.scroll = this.scrollTarget;
        }
        this.hits.clear();
        this.hovered = null;
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        float open = ease(this.opening);
        float page = ease(this.switching);
        graphics.pose().pushPose();
        graphics.pose().translate(-(1.0F - open) * 12.0F, 0.0F, 0.0F);
        this.renderSidebar(graphics, mouseX, mouseY);
        graphics.pose().popPose();
        graphics.pose().pushPose();
        graphics.pose().translate((1.0F - page) * 10.0F, (1.0F - open) * 10.0F, 0.0F);
        this.renderContent(graphics, mouseX, mouseY);
        if (this.describe > 0) {
            this.renderDescription(graphics);
        }
        graphics.pose().popPose();
        this.renderBar(graphics, mouseX, mouseY);
        this.search.render(graphics, mouseX, mouseY, partialTick);
        if (this.hovered != this.tooltipFor) {
            this.tooltipFor = this.hovered;
            this.tooltipSince = now;
        }
        if (this.describe == 0 && this.hovered != null && this.hovered.tooltip() != null && this.dragging == null && now - this.tooltipSince > 400_000_000L) {
            graphics.renderTooltip(this.font, this.font.split(this.hovered.tooltip(), 200), mouseX, mouseY);
        }
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = this.origin;
        int x1 = this.origin + this.sidebar;
        graphics.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nimbus", "icon.png"), x0 + 10, 9, 12, 12, 0, 0, 256, 256, 256, 256);
        graphics.drawString(this.font, Component.translatable("nimbus.screen.title"), x0 + 27, 11, TEXT, false);
        graphics.fill(x0 + 8, 26, x1 - 8, 44, STRIP);
        graphics.fill(x0 + 8, 43, x1 - 8, 44, this.search.isFocused() ? ACCENT : LINE);
        int y = 54;
        long shown = this.mods.stream().filter(mod -> !mod.pages().isEmpty()).count();
        for (ModOptions mod : this.mods) {
            if (mod.pages().isEmpty()) {
                continue;
            }
            if (shown > 1) {
                int x = x0 + 10;
                if (mod.icon() != null) {
                    graphics.blit(mod.icon(), x, y, 10, 10, 0, 0, 256, 256, 256, 256);
                    x += 14;
                }
                graphics.drawString(this.font, ellipsize(mod.name(), x1 - x - 8), x, y + 1, TEXT_OFF, false);
                y += 14;
            }
            for (OptionPage candidate : mod.pages()) {
                boolean selected = !this.searching() && candidate == this.page;
                boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + 16;
                float fade = this.fade(this.hoverFade, candidate, hover || selected);
                if (fade > 0.0F) {
                    graphics.fill(x0, y, x1, y + 16, alpha(selected ? 0x30FFFFFF : HOVER, fade));
                }
                if (selected) {
                    this.barY = Float.isNaN(this.barY) ? y : this.barY + (y - this.barY) * this.step;
                    int bar = Math.round(this.barY);
                    graphics.fill(x0, bar, x0 + 2, bar + 16, ACCENT);
                }
                graphics.drawString(this.font, ellipsize(candidate.name(), this.sidebar - 32), x0 + 14, y + 4, selected || hover ? TEXT : TEXT_DIM, false);
                if (pendingOn(candidate)) {
                    graphics.fill(x1 - 12, y + 7, x1 - 9, y + 10, ACCENT);
                }
                this.hits.add(new Hit(Kind.PAGE, x0, y, x1, y + 16, candidate));
                y += 17;
            }
            y += 8;
        }
        // shader packs live on their own screen, like Iris
        if (com.qateralong.nimbus.pack.Pipeline.supported()) {
            graphics.fill(x0 + 8, y - 4, x1 - 8, y - 3, LINE);
            boolean hover = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + 16;
            if (hover) {
                graphics.fill(x0, y, x1, y + 16, HOVER);
            }
            Component label = Component.translatable("nimbus.shaders.title");
            graphics.drawString(this.font, label, x0 + 14, y + 4, hover ? TEXT : TEXT_DIM, false);
            graphics.drawString(this.font, "›", x1 - 12, y + 4, hover ? ACCENT : TEXT_OFF, false);
            this.hits.add(new Hit(Kind.SHADERS, x0, y, x1, y + 16, null));
        }
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = 34;
        int bottom = this.height - BAR;
        Component heading = this.searching() ? Component.translatable("nimbus.screen.results") : this.page == null ? Component.empty() : this.page.name();
        graphics.drawString(this.font, heading, this.left + 2, 13, TEXT, false);
        graphics.enableScissor(this.left - 2, top, this.right + 4, bottom);
        int y = top - (int) Math.round(this.scroll);
        int start = y;
        if (this.searching()) {
            boolean any = false;
            for (ModOptions mod : this.mods) {
                for (OptionPage candidate : mod.pages()) {
                    for (OptionGroup group : candidate.groups()) {
                        List<Option<?, ?>> found = new ArrayList<>();
                        for (Option<?, ?> option : group.options()) {
                            if (option.isVisible() && this.matches(option)) {
                                found.add(option);
                            }
                        }
                        if (!found.isEmpty()) {
                            any = true;
                            Component title = Component.empty().append(candidate.name()).append(" › ").append(group.name() == null ? mod.name() : group.name());
                            y = this.renderGroup(graphics, mouseX, mouseY, null, title, found, y, top, bottom);
                        }
                    }
                }
            }
            if (!any) {
                graphics.drawString(this.font, Component.translatable("nimbus.screen.nothing"), this.left, y + 4, TEXT_DIM, false);
                y += 20;
            }
        } else if (this.page != null) {
            for (OptionGroup group : this.page.groups()) {
                List<Option<?, ?>> visible = new ArrayList<>();
                for (Option<?, ?> option : group.options()) {
                    if (option.isVisible()) {
                        visible.add(option);
                    }
                }
                if (!visible.isEmpty()) {
                    y = this.renderGroup(graphics, mouseX, mouseY, group, group.name(), visible, y, top, bottom);
                }
            }
        }
        graphics.disableScissor();
        this.contentHeight = y - start;
        int view = bottom - top;
        double max = Math.max(0, this.contentHeight - view + 6);
        if (this.scrollTarget > max) {
            this.scrollTarget = max;
        }
        if (max > 0) {
            int bar = Math.max(16, view * view / Math.max(1, this.contentHeight));
            int barY = top + (int) ((view - bar) * (this.scroll / max));
            graphics.fill(this.right + 3, barY, this.right + 5, barY + bar, 0x50FFFFFF);
        }
    }

    private int renderGroup(GuiGraphics graphics, int mouseX, int mouseY, OptionGroup group, Component title, List<Option<?, ?>> options, int y, int top, int bottom) {
        boolean collapsed = group != null && this.collapsed(group);
        if (title != null) {
            boolean hover = group != null && inside(mouseX, mouseY, this.left, Math.max(y, top), this.right, Math.min(y + TITLE, bottom)) && y + TITLE > top;
            String chevron = group == null ? "" : collapsed ? "▸ " : hover ? "▾ " : "";
            Component caption = Component.literal(chevron).append(title.copy());
            graphics.drawString(this.font, caption, this.left + 2, y + 2, hover ? TEXT : TEXT_DIM, false);
            int rule = this.left + 2 + this.font.width(caption) + 6;
            if (rule < this.right - 24) {
                graphics.fill(rule, y + 6, collapsed ? this.right - 18 : this.right, y + 7, LINE);
            }
            if (collapsed) {
                String count = String.valueOf(options.size());
                graphics.drawString(this.font, count, this.right - 4 - this.font.width(count), y + 2, TEXT_OFF, false);
            }
            if (group != null && y + TITLE > top && y < bottom) {
                this.hits.add(new Hit(Kind.GROUP, this.left, Math.max(y, top), this.right, Math.min(y + TITLE, bottom), group));
            }
            y += TITLE;
        }
        if (collapsed) {
            return y + GROUP_GAP;
        }
        int height = options.size() * ROW;
        for (int i = 0; i < options.size(); i++) {
            int rowY = y + i * ROW;
            if (rowY + ROW <= top || rowY >= bottom) {
                continue;
            }
            this.renderRow(graphics, mouseX, mouseY, options.get(i), rowY, top, bottom);
        }
        return y + height + GROUP_GAP;
    }

    @SuppressWarnings("unchecked")
    private void renderRow(GuiGraphics graphics, int mouseX, int mouseY, Option<?, ?> option, int y, int top, int bottom) {
        boolean enabled = option.isEnabled();
        boolean hover = mouseY >= top && mouseY < bottom && inside(mouseX, mouseY, this.left, y, this.right, y + ROW);
        graphics.fill(this.left, y, this.right, y + ROW - 1, STRIP);
        if (hover) {
            this.hovered = option;
        }
        float fade = this.fade(this.hoverFade, option, hover);
        if (fade > 0.0F) {
            graphics.fill(this.left, y, this.right, y + ROW - 1, alpha(HOVER, fade));
        }
        int clipTop = Math.max(y, top);
        int clipBottom = Math.min(y + ROW, bottom);
        this.hits.add(new Hit(Kind.OPTION, this.left, clipTop, this.right, clipBottom, option));
        if (option.changed()) {
            graphics.fill(this.left, y, this.left + 2, y + ROW - 1, ACCENT);
        }
        int width = this.right - this.left;
        int control = Mth.clamp(width * 44 / 100, 70, 150);
        int controlLeft = this.right - 8 - control;
        int labelColor = !enabled ? TEXT_OFF : option.changed() ? ACCENT : TEXT;
        int labelEnd = this.label(graphics, option.name(), this.left + 8, y + 6, controlLeft - this.left - 18, labelColor);
        // a visible reset instead of the hidden right click
        Component reset = Component.translatable("nimbus.screen.reset");
        int resetWidth = this.font.width(reset);
        if (hover && enabled && option.resettable() && !option.isDefault() && !(option instanceof ActionOption) && labelEnd + 10 + resetWidth < controlLeft) {
            boolean over = inside(mouseX, mouseY, labelEnd + 4, y, labelEnd + 12 + resetWidth, y + ROW - 1);
            graphics.drawString(this.font, reset, labelEnd + 8, y + 6, over ? ACCENT : TEXT_OFF, false);
            this.hits.add(new Hit(Kind.RESET, labelEnd + 4, clipTop, labelEnd + 12 + resetWidth, clipBottom, option));
        }
        int color = enabled ? TEXT : TEXT_OFF;
        if (option instanceof ToggleOption toggle) {
            boolean on = Boolean.TRUE.equals(toggle.value());
            tickBox(graphics, this.right - 17, y, this.fade(this.tickFade, option, on), enabled ? (hover ? TEXT : TEXT_DIM) : TEXT_OFF, enabled ? ACCENT : TEXT_OFF);
            this.hits.add(new Hit(Kind.CONTROL_RIGHT, controlLeft, clipTop, this.right, clipBottom, option));
        } else if (option instanceof SliderOption slider) {
            int value = slider.value() == null ? slider.min() : slider.value();
            int trackRight = this.right - 8;
            Component text = slider.format(value);
            int trackLeft = trackRight - Mth.clamp(control - this.font.width(text) - 8, Math.min(40, control / 2), control / 2);
            double fraction = slider.fraction(value);
            int fill = trackLeft + (int) Math.round((trackRight - trackLeft) * fraction);
            graphics.fill(trackLeft, y + 9, trackRight, y + 10, TRACK);
            graphics.fill(trackLeft, y + 9, fill, y + 10, enabled ? ACCENT : TEXT_OFF);
            graphics.fill(fill - 1, y + 5, fill + 2, y + 14, enabled ? (hover ? 0xFFFFFFFF : TEXT) : TEXT_OFF);
            graphics.drawString(this.font, ellipsize(text, trackLeft - controlLeft - 8), trackLeft - 8 - Math.min(this.font.width(text), trackLeft - controlLeft - 8), y + 6, color, false);
            this.hits.add(new Hit(Kind.SLIDER, trackLeft - 4, clipTop, trackRight + 4, clipBottom, slider));
        } else if (option instanceof CycleOption<?> cycle) {
            Component text = ((CycleOption<Object>) cycle).format(cycle.value());
            int middle = (controlLeft + this.right - 8) / 2;
            boolean leftHover = hover && mouseX < middle && mouseX >= controlLeft;
            boolean rightHover = hover && mouseX >= middle;
            FormattedCharSequence shown = ellipsize(text, control - 20);
            int arrow = this.right - 8 - this.font.width("›");
            int textX = arrow - 6 - this.font.width(shown);
            graphics.drawString(this.font, shown, textX, y + 6, color, false);
            if (hover && enabled) {
                graphics.drawString(this.font, "‹", textX - 6 - this.font.width("‹"), y + 6, leftHover ? ACCENT : TEXT_DIM, false);
                graphics.drawString(this.font, "›", arrow, y + 6, rightHover ? ACCENT : TEXT_DIM, false);
            }
            this.hits.add(new Hit(Kind.CONTROL_LEFT, controlLeft - 4, clipTop, middle, clipBottom, option));
            this.hits.add(new Hit(Kind.CONTROL_RIGHT, middle, clipTop, this.right, clipBottom, option));
        } else if (option instanceof ActionOption action) {
            Component text = action.button() != null ? action.button() : Component.translatable("nimbus.screen.open");
            graphics.drawString(this.font, text, this.right - 8 - this.font.width(text), y + 6, hover && enabled ? ACCENT : TEXT_DIM, false);
            this.hits.add(new Hit(Kind.CONTROL_RIGHT, controlLeft, clipTop, this.right, clipBottom, option));
        }
    }

    private void renderDescription(GuiGraphics graphics) {
        Option<?, ?> option = this.hovered != null ? this.hovered : this.focused;
        if (option == null) {
            return;
        }
        int x0 = this.edge - this.describe;
        int x1 = this.edge;
        int top = 34;
        int x = x0 + 8;
        int wrap = x1 - x - 8;
        // measured with the same code that draws it: the box fits its text
        int bottom = this.describe(null, option, x, top + 8, wrap);
        graphics.fill(x0, top, x1, bottom + 6, STRIP);
        this.describe(graphics, option, x, top + 8, wrap);
    }

    // graphics == null only measures; returns the y below the last line
    private int describe(GuiGraphics graphics, Option<?, ?> option, int x, int y, int wrap) {
        for (FormattedCharSequence line : this.font.split(option.name(), wrap)) {
            if (graphics != null) {
                graphics.drawString(this.font, line, x, y, TEXT, false);
            }
            y += 10;
        }
        y += 4;
        if (option.tooltip() != null) {
            for (FormattedCharSequence line : this.font.split(option.tooltip(), wrap)) {
                if (y > this.height - BAR - 50) {
                    break;
                }
                if (graphics != null) {
                    graphics.drawString(this.font, line, x, y, 0xFFC8CCD2, false);
                }
                y += 10;
            }
            y += 6;
        }
        OptionImpact impact = option.impact();
        if (impact != null) {
            int color = switch (impact) {
                case LOW -> 0xFF7BC67B;
                case MEDIUM -> 0xFFE8C15A;
                case HIGH -> 0xFFE87A5A;
                case VARIES -> TEXT_DIM;
            };
            Component value = Component.translatable("nimbus.impact." + impact.name().toLowerCase(Locale.ROOT)).withColor(color);
            y = this.meta(graphics, Component.translatable("nimbus.screen.impact", value), x, y, wrap);
        }
        for (OptionFlag flag : option.flags()) {
            y = this.meta(graphics, Component.translatable("nimbus.flag." + flag.name().toLowerCase(Locale.ROOT)), x, y, wrap);
        }
        if (option.resettable() && !(option instanceof ActionOption)) {
            y = this.meta(graphics, Component.translatable("nimbus.screen.default", this.formatDefault(option)), x, y, wrap);
            if (!option.isDefault()) {
                y = this.meta(graphics, Component.translatable("nimbus.screen.reset_hint"), x, y, wrap);
            }
        }
        return y;
    }

    @SuppressWarnings("unchecked")
    private Component formatDefault(Option<?, ?> option) {
        return ((Option<Object, ?>) option).format(option.defaultValue());
    }

    private int meta(GuiGraphics graphics, Component text, int x, int y, int wrap) {
        for (FormattedCharSequence line : this.font.split(text, wrap)) {
            if (graphics != null) {
                graphics.drawString(this.font, line, x, y, TEXT_DIM, false);
            }
            y += 10;
        }
        return y + 2;
    }

    private void renderBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = this.height - BAR + 7;
        int pending = this.pending();
        int x1 = this.edge;
        x1 = this.button(graphics, mouseX, mouseY, x1, y, CommonComponents.GUI_DONE, Kind.DONE, true, true);
        // left side: back to defaults for everything on this page (pending until Apply like any change)
        int textX = this.left + 2;
        if (!this.searching() && this.page != null && pending == 0 && this.pageChanged()) {
            Component text = Component.translatable("nimbus.screen.reset_page");
            int width = Math.max(56, this.font.width(text) + 16);
            this.button(graphics, mouseX, mouseY, this.left + width, y, text, Kind.RESET_PAGE, true, false);
            textX = this.left + width + 8;
        }
        if (pending > 0) {
            x1 = this.button(graphics, mouseX, mouseY, x1 - 6, y, Component.translatable("nimbus.screen.apply"), Kind.APPLY, true, false);
            this.button(graphics, mouseX, mouseY, x1 - 6, y, Component.translatable("nimbus.screen.undo"), Kind.UNDO, true, false);
            graphics.drawString(this.font, Component.translatable("nimbus.screen.pending", pending), this.left + 2, y + 6, ACCENT, false);
        } else if (this.notice != null) {
            graphics.drawString(this.font, this.notice, textX, y + 6, TEXT_DIM, false);
        }
    }

    private int button(GuiGraphics graphics, int mouseX, int mouseY, int x1, int y, Component text, Kind kind, boolean active, boolean primary) {
        int width = Math.max(56, this.font.width(text) + 16);
        int x0 = x1 - width;
        boolean hover = active && inside(mouseX, mouseY, x0, y, x1, y + 18);
        int background = !active ? 0x40000000 : primary ? (hover ? 0xFFF09A35 : ACCENT) : (hover ? 0xC0303030 : 0xC0000000);
        graphics.fill(x0, y, x1, y + 18, background);
        int color = !active ? TEXT_OFF : primary ? 0xFF1A1206 : TEXT;
        graphics.drawString(this.font, text, x0 + (width - this.font.width(text)) / 2, y + 5, color, false);
        if (active) {
            this.hits.add(new Hit(kind, x0, y, x1, y + 18, null));
        }
        return x0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        this.search.setFocused(false);
        for (int i = this.hits.size() - 1; i >= 0; i--) {
            Hit hit = this.hits.get(i);
            if (!hit.contains(mouseX, mouseY)) {
                continue;
            }
            if (this.click(hit, mouseX, button)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean click(Hit hit, double mouseX, int button) {
        switch (hit.kind()) {
            case PAGE -> {
                OptionPage target = (OptionPage) hit.target();
                if (this.page != target) {
                    this.switching = 0.0F;
                }
                this.page = target;
                lastMod = target.mod().id();
                lastPage = target.id();
                this.search.setValue("");
                this.scroll = 0;
                this.scrollTarget = 0;
                this.focused = null;
                return true;
            }
            case SHADERS -> {
                this.minecraft.setScreen(new com.qateralong.nimbus.pack.ShaderScreen(this));
                return true;
            }
            case GROUP -> {
                OptionGroup group = (OptionGroup) hit.target();
                COLLAPSED.put(group, !this.collapsed(group));
                return true;
            }
            case RESET -> {
                ((Option<?, ?>) hit.target()).reset();
                return true;
            }
            case RESET_PAGE -> {
                for (OptionGroup group : this.page.groups()) {
                    for (Option<?, ?> option : group.options()) {
                        if (option.resettable() && !option.isDefault() && !(option instanceof ActionOption)) {
                            option.reset();
                        }
                    }
                }
                return true;
            }
            case UNDO -> {
                for (Option<?, ?> option : this.all()) {
                    option.discard();
                }
                return true;
            }
            case APPLY -> {
                this.apply();
                return true;
            }
            case DONE -> {
                this.onClose();
                return true;
            }
            default -> {
            }
        }
        Option<?, ?> option = (Option<?, ?>) hit.target();
        if (option == null) {
            return false;
        }
        this.focused = option;
        if (button == 1) {
            if (option.resettable() && option.isEnabled()) {
                option.reset();
                return true;
            }
            return false;
        }
        if (button != 0 || !option.isEnabled()) {
            return hit.kind() == Kind.OPTION;
        }
        if (option instanceof ToggleOption toggle) {
            toggle.set(!Boolean.TRUE.equals(toggle.value()));
            return true;
        }
        if (option instanceof CycleOption<?> cycle) {
            CycleOption<Object> typed = (CycleOption<Object>) cycle;
            int direction = hit.kind() == Kind.CONTROL_LEFT || hasShiftDown() ? -1 : 1;
            typed.set(typed.step(direction));
            return true;
        }
        if (option instanceof SliderOption slider && hit.kind() == Kind.SLIDER) {
            this.dragging = slider;
            this.dragX = hit.x0() + 4;
            this.dragWidth = hit.x1() - hit.x0() - 8;
            this.drag(mouseX);
            return true;
        }
        if (option instanceof ActionOption action) {
            action.run(this);
            return true;
        }
        return hit.kind() == Kind.OPTION;
    }

    private void drag(double mouseX) {
        if (this.dragging != null && this.dragWidth > 0) {
            this.dragging.set(this.dragging.snap((mouseX - this.dragX) / this.dragWidth));
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging != null) {
            this.drag(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!hasShiftDown() && scrollY != 0) {
            for (Hit hit : this.hits) {
                if (hit.kind() == Kind.SLIDER && hit.contains(mouseX, mouseY) && hit.target() instanceof SliderOption slider && slider.isEnabled()) {
                    int value = slider.value() == null ? slider.min() : slider.value();
                    slider.set(Mth.clamp(value + (scrollY > 0 ? slider.step() : -slider.step()), slider.min(), slider.max()));
                    return true;
                }
            }
        }
        if (mouseX >= this.left && mouseX < this.right + 6) {
            double max = Math.max(0, this.contentHeight - (this.height - BAR - 34) + 6);
            this.scrollTarget = Mth.clamp(this.scrollTarget - scrollY * ROW * 2, 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown()) {
            this.setFocused(this.search);
            this.search.setFocused(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.search.isFocused() && !this.query.isEmpty()) {
            this.search.setValue("");
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void apply() {
        EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
        Set<ModOptions> touched = new HashSet<>();
        boolean vanilla = false;
        for (ModOptions mod : this.mods) {
            for (OptionPage candidate : mod.pages()) {
                for (OptionGroup group : candidate.groups()) {
                    for (Option<?, ?> option : group.options()) {
                        if (option.apply()) {
                            flags.addAll(option.flags());
                            touched.add(mod);
                            vanilla |= option.isVanilla();
                        }
                    }
                }
            }
        }
        for (ModOptions mod : touched) {
            mod.save();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (vanilla) {
            minecraft.options.save();
        }
        if (flags.contains(OptionFlag.RELOAD_CHUNKS) && minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
        if (flags.contains(OptionFlag.RELOAD_RESOURCES)) {
            minecraft.reloadResourcePacks();
        }
        this.notice = flags.contains(OptionFlag.RESTART) ? Component.translatable("nimbus.screen.restart") : null;
    }

    @Override
    public void onClose() {
        this.apply();
        NimbusConfig.get().save();
        this.minecraft.setScreen(this.parent);
    }

    // draws the name, the search match in accent; returns where the text ends
    private int label(GuiGraphics graphics, Component name, int x, int y, int width, int color) {
        String plain = name.getString();
        String needle = this.query.trim();
        int at = needle.isEmpty() ? -1 : plain.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
        if (at < 0 || this.font.width(plain) > width) {
            FormattedCharSequence shown = ellipsize(name, width);
            graphics.drawString(this.font, shown, x, y, color, false);
            return x + this.font.width(shown);
        }
        String before = plain.substring(0, at);
        String match = plain.substring(at, at + needle.length());
        String after = plain.substring(at + needle.length());
        graphics.drawString(this.font, before, x, y, color, false);
        int mx = x + this.font.width(before);
        graphics.drawString(this.font, match, mx, y, ACCENT, false);
        graphics.fill(mx, y + 9, mx + this.font.width(match), y + 10, ACCENT);
        int ax = mx + this.font.width(match);
        graphics.drawString(this.font, after, ax, y, color, false);
        return ax + this.font.width(after);
    }

    private FormattedCharSequence ellipsize(Component text, int width) {
        if (this.font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        String ellipsis = "…";
        String cut = this.font.plainSubstrByWidth(text.getString(), Math.max(0, width - this.font.width(ellipsis)));
        return Component.literal(cut + ellipsis).withStyle(text.getStyle()).getVisualOrderText();
    }

    // 9x9 box from (x, y + 5), a 5x5 square in the middle that grows with fill
    public static void tickBox(GuiGraphics graphics, int x, int y, float fill, int frame, int color) {
        int top = y + 5;
        graphics.fill(x, top, x + 9, top + 1, frame);
        graphics.fill(x, top + 8, x + 9, top + 9, frame);
        graphics.fill(x, top + 1, x + 1, top + 8, frame);
        graphics.fill(x + 8, top + 1, x + 9, top + 8, frame);
        int size = Math.round(5.0F * fill);
        if (size > 0) {
            int offset = (5 - size) / 2;
            graphics.fill(x + 2 + offset, top + 2 + offset, x + 2 + offset + size, top + 2 + offset + size, color);
        }
    }

    private static boolean animated() {
        return NimbusConfig.get().enabled && NimbusConfig.get().menuAnimations;
    }

    private static float ease(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    private static int alpha(int color, float fade) {
        return (int) ((color >>> 24) * fade) << 24 | color & 0xFFFFFF;
    }

    // eases a per-object value towards 0 or 1 at the frame's step
    private float fade(Map<Object, Float> values, Object key, boolean on) {
        float target = on ? 1.0F : 0.0F;
        float value = values.getOrDefault(key, target);
        value += (target - value) * this.step;
        if (Math.abs(value - target) < 0.02F) {
            value = target;
        }
        values.put(key, value);
        return value;
    }

    private static boolean inside(double x, double y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }

}
