package com.qateralong.nimbus.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// One screen of a pack's option tree (shaders.properties screen / screen.NAME): options cycle through their values,
// [NAME] opens a sub-screen, <profile> applies the pack's presets. Done saves and reloads the pack if anything changed.
public final class PackOptionsScreen extends Screen {
    private static final int ROW = 24;
    private final Screen parent;
    private final PackOptions options;
    private final String screen;
    private final boolean root;
    private int scroll;
    private int rows;
    private static boolean changed;

    public PackOptionsScreen(Screen parent, PackOptions options, String screen) {
        super(Component.literal(screen.isEmpty() ? "Shader options" : options.label("screen." + screen, screen)));
        this.parent = parent;
        this.options = options;
        this.screen = screen;
        this.root = screen.isEmpty();
        if (this.root) {
            changed = false;
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();
        List<String> entries = this.options.screens.getOrDefault(this.screen, new ArrayList<>(this.options.options.keySet()));
        int width = 150;
        int left = this.width / 2 - width - 5;
        int top = 32;
        int visible = Math.max(1, (this.height - top - 40) / ROW);
        this.rows = (entries.size() + 1) / 2;
        this.scroll = Math.max(0, Math.min(this.scroll, this.rows - visible));
        for (int i = 0; i < entries.size(); i++) {
            int row = i / 2 - this.scroll;
            if (row < 0 || row >= visible) {
                continue;
            }
            int x = left + (i % 2) * (width + 10);
            int y = top + row * ROW;
            String entry = entries.get(i);
            Button button = this.button(entry, x, y, width);
            if (button != null) {
                this.addRenderableWidget(button);
            }
        }
        this.addRenderableWidget(Button.builder(Component.translatable("controls.reset"), b -> {
            this.options.chosen.clear();
            changed = true;
            this.init();
        }).bounds(this.width / 2 - 155, this.height - 28, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(this.width / 2 + 5, this.height - 28, 150, 20).build());
    }

    private Button button(String entry, int x, int y, int width) {
        if (entry.equals("<empty>") || entry.equals("*")) {
            return null;
        }
        if (entry.equals("<profile>")) {
            List<String> names = new ArrayList<>(this.options.profiles.keySet());
            if (names.isEmpty()) {
                return null;
            }
            String current = this.currentProfile();
            return Button.builder(Component.literal(this.options.label("profile", "Profile") + ": "
                    + (current == null ? this.options.label("profile.custom", "Custom") : this.options.label("profile." + current, current))), b -> {
                int next = current == null ? 0 : (names.indexOf(current) + 1) % names.size();
                for (Map.Entry<String, String> e : this.options.profiles.get(names.get(next)).entrySet()) {
                    if (this.options.options.containsKey(e.getKey())) {
                        this.options.set(e.getKey(), e.getValue());
                    }
                }
                changed = true;
                this.init();
            }).bounds(x, y, width, 20).build();
        }
        if (entry.startsWith("[") && entry.endsWith("]")) {
            String sub = entry.substring(1, entry.length() - 1);
            return Button.builder(Component.literal(this.options.label("screen." + sub, sub) + "..."),
                    b -> this.minecraft.setScreen(new PackOptionsScreen(this, this.options, sub))).bounds(x, y, width, 20).build();
        }
        PackOptions.Option option = this.options.options.get(entry);
        String label = this.options.label("option." + entry, entry);
        if (option == null || option.values.isEmpty()) {
            Button info = Button.builder(Component.literal(label), b -> {
            }).bounds(x, y, width, 20).build();
            info.active = false;
            return info;
        }
        String value = this.options.value(entry);
        String shown = option.toggle ? (value.equals("true") ? "ON" : "OFF") : this.options.label("value." + entry + "." + value, value);
        Button button = Button.builder(Component.literal(label + ": " + shown), b -> {
            int at = option.values.indexOf(value);
            int step = hasShiftDown() ? -1 : 1;
            String next = option.values.get(Math.floorMod(at + step, option.values.size()));
            this.options.set(entry, next);
            changed = true;
            this.init();
        }).bounds(x, y, width, 20).build();
        String tip = this.options.lang.get("option." + entry + ".comment");
        if (tip != null) {
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tip)));
        }
        return button;
    }

    private String currentProfile() {
        for (var e : this.options.profiles.entrySet()) {
            boolean all = true;
            for (var s : e.getValue().entrySet()) {
                if (this.options.options.containsKey(s.getKey()) && !this.options.value(s.getKey()).equals(s.getValue())) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return e.getKey();
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll -= (int) Math.signum(scrollY);
        this.init();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.root && changed) {
            try {
                this.options.save();
            } catch (java.io.IOException e) {
                PackProgram.warn("saving options", e);
            }
            Pipeline.reload();
        }
        this.minecraft.setScreen(this.parent);
    }
}
