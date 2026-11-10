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
    private static final int PANEL = 0xC0121418;
    private static final int SIDEBAR_BG = 0x600A0B0D;
    private static final int CARD = 0x80202329;
    private static final int HOVER = 0x16FFFFFF;
    private static final int LINE = 0x14FFFFFF;
    private static final int TRACK = 0xFF3A3F47;
    private static final int ROW = 20;
    private static final int GROUP_GAP = 8;
    private static final int TITLE = 14;
    private static final int BAR = 32;
    private static final Map<OptionGroup, Boolean> COLLAPSED = new IdentityHashMap<>();
    private static String lastMod = "nimbus";
    private static String lastPage = "general";

    private final Screen parent;
    private final Map<Option<?, ?>, Float> knobs = new IdentityHashMap<>();
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
    private int origin;
    private int panelRight;
    private int sidebar;
    private int describe;
    private int left;
    private int right;
    private int edge;

    private enum Kind {
        PAGE, GROUP, OPTION, CONTROL_LEFT, CONTROL_RIGHT, SLIDER, UNDO, APPLY, DONE
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
        this.sidebar = Mth.clamp(total * 23 / 100, 96, 140);
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
        graphics.fill(0, 0, this.width, this.height, 0x60000000);
        graphics.fill(this.origin, 0, this.panelRight, this.height, PANEL);
        graphics.fill(this.origin, 0, this.origin + this.sidebar, this.height, SIDEBAR_BG);
        graphics.fill(this.origin + this.sidebar - 1, 0, this.origin + this.sidebar, this.height, LINE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.scroll += (this.scrollTarget - this.scroll) * Math.min(1.0, partialTick * 0.6 + 0.35);
        if (Math.abs(this.scrollTarget - this.scroll) < 0.5) {
            this.scroll = this.scrollTarget;
        }
        this.hits.clear();
        this.hovered = null;
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.renderSidebar(graphics, mouseX, mouseY);
        this.renderContent(graphics, mouseX, mouseY);
        if (this.describe > 0) {
            this.renderDescription(graphics);
        }
        this.renderBar(graphics, mouseX, mouseY);
        this.search.render(graphics, mouseX, mouseY, partialTick);
        if (this.describe == 0 && this.hovered != null && this.hovered.tooltip() != null && this.dragging == null) {
            graphics.renderTooltip(this.font, this.font.split(this.hovered.tooltip(), 200), mouseX, mouseY);
        }
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = this.origin;
        int x1 = this.origin + this.sidebar;
        graphics.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nimbus", "icon.png"), x0 + 10, 9, 12, 12, 0, 0, 256, 256, 256, 256);
        graphics.drawString(this.font, Component.translatable("nimbus.screen.title"), x0 + 27, 11, TEXT, false);
        card(graphics, x0 + 8, 26, x1 - 8, 44, this.search.isFocused() ? 0xFF262A31 : 0x80000000);
        if (this.search.isFocused()) {
            graphics.fill(x0 + 8, 43, x1 - 8, 44, ACCENT);
        }
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
                boolean hover = mouseX >= x0 + 6 && mouseX < x1 - 6 && mouseY >= y && mouseY < y + 16;
                if (selected) {
                    card(graphics, x0 + 6, y, x1 - 6, y + 16, 0x1CFFFFFF);
                    graphics.fill(x0 + 6, y + 4, x0 + 8, y + 12, ACCENT);
                } else if (hover) {
                    card(graphics, x0 + 6, y, x1 - 6, y + 16, HOVER);
                }
                graphics.drawString(this.font, ellipsize(candidate.name(), this.sidebar - 32), x0 + 14, y + 4, selected || hover ? TEXT : TEXT_DIM, false);
                this.hits.add(new Hit(Kind.PAGE, x0 + 6, y, x1 - 6, y + 16, candidate));
                y += 17;
            }
            y += 8;
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
            graphics.drawString(this.font, Component.literal(chevron).append(title.copy()), this.left + 2, y + 2, hover ? TEXT : TEXT_OFF, false);
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
        if (y + height > top && y < bottom) {
            card(graphics, this.left, y, this.right, y + height, CARD);
        }
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
        if (hover) {
            this.hovered = option;
            card(graphics, this.left, y, this.right, y + ROW, HOVER);
        }
        int clipTop = Math.max(y, top);
        int clipBottom = Math.min(y + ROW, bottom);
        this.hits.add(new Hit(Kind.OPTION, this.left, clipTop, this.right, clipBottom, option));
        if (option.changed()) {
            graphics.fill(this.left, y + 5, this.left + 2, y + ROW - 5, ACCENT);
        }
        int width = this.right - this.left;
        int control = Mth.clamp(width * 44 / 100, 70, 150);
        int controlLeft = this.right - 8 - control;
        graphics.drawString(this.font, ellipsize(option.name(), controlLeft - this.left - 18), this.left + 11, y + 6, enabled ? TEXT : TEXT_OFF, false);
        int color = enabled ? TEXT : TEXT_OFF;
        if (option instanceof ToggleOption toggle) {
            boolean on = Boolean.TRUE.equals(toggle.value());
            float knob = this.knobs.getOrDefault(option, on ? 1.0F : 0.0F);
            knob += ((on ? 1.0F : 0.0F) - knob) * 0.35F;
            if (Math.abs(knob - (on ? 1 : 0)) < 0.02F) {
                knob = on ? 1.0F : 0.0F;
            }
            this.knobs.put(option, knob);
            int x1 = this.right - 8;
            int x0 = x1 - 20;
            int pill = lerpColor(knob, 0xFF4A505A, enabled ? ACCENT : 0xFF6B4A22);
            card(graphics, x0, y + 5, x1, y + 15, pill);
            int kx = x0 + 1 + Math.round(knob * 10);
            card(graphics, kx, y + 6, kx + 8, y + 14, enabled ? 0xFFF5F5F5 : 0xFF9A9A9A);
            this.hits.add(new Hit(Kind.CONTROL_RIGHT, controlLeft, clipTop, this.right, clipBottom, option));
        } else if (option instanceof SliderOption slider) {
            int value = slider.value() == null ? slider.min() : slider.value();
            int trackRight = this.right - 8;
            Component text = slider.format(value);
            int trackLeft = trackRight - Mth.clamp(control - this.font.width(text) - 8, Math.min(40, control / 2), control / 2);
            double fraction = slider.fraction(value);
            int fill = trackLeft + (int) Math.round((trackRight - trackLeft) * fraction);
            graphics.fill(trackLeft, y + 9, trackRight, y + 11, TRACK);
            graphics.fill(trackLeft, y + 9, fill, y + 11, enabled ? ACCENT : 0xFF6B4A22);
            card(graphics, fill - 3, y + 6, fill + 3, y + 14, enabled ? 0xFFF5F5F5 : 0xFF9A9A9A);
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
        } else if (option instanceof ActionOption) {
            Component text = Component.translatable("nimbus.screen.open");
            graphics.drawString(this.font, text, this.right - 8 - this.font.width(text), y + 6, hover && enabled ? ACCENT : TEXT_DIM, false);
            this.hits.add(new Hit(Kind.CONTROL_RIGHT, controlLeft, clipTop, this.right, clipBottom, option));
        }
    }

    private void renderDescription(GuiGraphics graphics) {
        int x0 = this.edge - this.describe;
        int x1 = this.edge;
        int top = 34;
        Option<?, ?> option = this.hovered != null ? this.hovered : this.focused;
        graphics.fill(x0, top, x0 + 1, this.height - BAR, LINE);
        int x = x0 + 10;
        int wrap = x1 - x - 8;
        int y = top + 2;
        if (option == null) {
            for (FormattedCharSequence line : this.font.split(Component.translatable("nimbus.screen.hint"), wrap)) {
                graphics.drawString(this.font, line, x, y, TEXT_OFF, false);
                y += 10;
            }
            return;
        }
        for (FormattedCharSequence line : this.font.split(option.name(), wrap)) {
            graphics.drawString(this.font, line, x, y, ACCENT, false);
            y += 10;
        }
        y += 4;
        if (option.tooltip() != null) {
            for (FormattedCharSequence line : this.font.split(option.tooltip(), wrap)) {
                if (y > this.height - BAR - 50) {
                    break;
                }
                graphics.drawString(this.font, line, x, y, 0xFFC8CCD2, false);
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
                this.meta(graphics, Component.translatable("nimbus.screen.reset_hint"), x, y, wrap);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Component formatDefault(Option<?, ?> option) {
        return ((Option<Object, ?>) option).format(option.defaultValue());
    }

    private int meta(GuiGraphics graphics, Component text, int x, int y, int wrap) {
        for (FormattedCharSequence line : this.font.split(text, wrap)) {
            graphics.drawString(this.font, line, x, y, TEXT_DIM, false);
            y += 10;
        }
        return y + 2;
    }

    private void renderBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = this.height - BAR + 7;
        int pending = this.pending();
        int x1 = this.edge;
        x1 = this.button(graphics, mouseX, mouseY, x1, y, CommonComponents.GUI_DONE, Kind.DONE, true, true);
        if (pending > 0) {
            x1 = this.button(graphics, mouseX, mouseY, x1 - 6, y, Component.translatable("nimbus.screen.apply"), Kind.APPLY, true, false);
            this.button(graphics, mouseX, mouseY, x1 - 6, y, Component.translatable("nimbus.screen.undo"), Kind.UNDO, true, false);
            graphics.drawString(this.font, Component.translatable("nimbus.screen.pending", pending), this.left + 2, y + 6, ACCENT, false);
        } else if (this.notice != null) {
            graphics.drawString(this.font, this.notice, this.left + 2, y + 6, TEXT_DIM, false);
        }
    }

    private int button(GuiGraphics graphics, int mouseX, int mouseY, int x1, int y, Component text, Kind kind, boolean active, boolean primary) {
        int width = Math.max(56, this.font.width(text) + 16);
        int x0 = x1 - width;
        boolean hover = active && inside(mouseX, mouseY, x0, y, x1, y + 18);
        int background = !active ? 0x40202328 : primary ? (hover ? 0xFFF09A35 : ACCENT) : (hover ? 0xFF353A42 : 0xFF262A30);
        card(graphics, x0, y, x1, y + 18, background);
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
                this.page = target;
                lastMod = target.mod().id();
                lastPage = target.id();
                this.search.setValue("");
                this.scroll = 0;
                this.scrollTarget = 0;
                this.focused = null;
                return true;
            }
            case GROUP -> {
                OptionGroup group = (OptionGroup) hit.target();
                COLLAPSED.put(group, !this.collapsed(group));
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
        if (option instanceof ActionOption action && hit.kind() != Kind.OPTION) {
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

    private FormattedCharSequence ellipsize(Component text, int width) {
        if (this.font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        String ellipsis = "…";
        String cut = this.font.plainSubstrByWidth(text.getString(), Math.max(0, width - this.font.width(ellipsis)));
        return Component.literal(cut + ellipsis).withStyle(text.getStyle()).getVisualOrderText();
    }

    private static boolean inside(double x, double y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    private static void card(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < 3 || y1 - y0 < 3) {
            graphics.fill(x0, y0, x1, y1, color);
            return;
        }
        graphics.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
        graphics.fill(x0, y0 + 1, x1, y1 - 1, color);
        graphics.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);
    }

    private static int lerpColor(float t, int from, int to) {
        int a = (int) Mth.lerp(t, from >>> 24, to >>> 24);
        int r = (int) Mth.lerp(t, from >> 16 & 255, to >> 16 & 255);
        int g = (int) Mth.lerp(t, from >> 8 & 255, to >> 8 & 255);
        int b = (int) Mth.lerp(t, from & 255, to & 255);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
