package com.qateralong.boost;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class BoostScreen extends Screen {
    private static final int ROW = 22;
    private final Screen parent;
    private int scroll;
    private int contentHeight;

    public BoostScreen(Screen parent) {
        super(Component.translatable("boost.title"));
        this.parent = parent;
    }

    private record Option(String key, int max, int step, IntSupplier get, IntConsumer set, boolean zeroOff) {
    }

    @Override
    protected void init() {
        BoostConfig c = BoostConfig.get();
        int width = Math.min(310, this.width / 2 - 14);
        int left = this.width / 2 - width - 4;
        int right = this.width / 2 + 4;
        int top = 34 - this.scroll;
        List<Object> items = new ArrayList<>();
        items.add(toggle("enabled", () -> c.enabled, v -> c.enabled = v));
        items.add(toggle("section_culling", () -> c.sectionCulling, v -> c.sectionCulling = v));
        items.add(toggle("occlusion_culling", () -> c.occlusionCulling, v -> c.occlusionCulling = v));
        items.add(new Option("item", 128, 4, () -> c.itemDistance, v -> c.itemDistance = v, true));
        items.add(new Option("experience", 128, 4, () -> c.experienceDistance, v -> c.experienceDistance = v, true));
        items.add(new Option("frame", 160, 4, () -> c.frameDistance, v -> c.frameDistance = v, true));
        items.add(new Option("armor_stand", 160, 4, () -> c.armorStandDistance, v -> c.armorStandDistance = v, true));
        items.add(new Option("painting", 192, 8, () -> c.paintingDistance, v -> c.paintingDistance = v, true));
        items.add(new Option("projectile", 160, 4, () -> c.projectileDistance, v -> c.projectileDistance = v, true));
        items.add(new Option("vehicle", 192, 8, () -> c.vehicleDistance, v -> c.vehicleDistance = v, true));
        items.add(new Option("mob", 192, 8, () -> c.mobDistance, v -> c.mobDistance = v, true));
        items.add(new Option("other", 192, 8, () -> c.otherDistance, v -> c.otherDistance = v, true));
        items.add(new Option("detail", 128, 4, () -> c.detailDistance, v -> c.detailDistance = v, true));
        items.add(new Option("shadow", 64, 2, () -> c.shadowDistance, v -> c.shadowDistance = v, true));
        items.add(new Option("name_tag", 64, 2, () -> c.nameTagDistance, v -> c.nameTagDistance = v, true));
        items.add(new Option("block_entity", 128, 4, () -> c.blockEntityDistance, v -> c.blockEntityDistance = v, true));
        items.add(new Option("sign_text", 64, 2, () -> c.signTextDistance, v -> c.signTextDistance = v, true));
        items.add(new Option("frame_content", 128, 4, () -> c.frameContentDistance, v -> c.frameContentDistance = v, true));
        items.add(toggle("particle_culling", () -> c.particleCulling, v -> c.particleCulling = v));
        items.add(new Option("particle", 128, 4, () -> c.particleDistance, v -> c.particleDistance = v, true));
        items.add(new Option("max_particles", 16000, 500, () -> c.maxParticles, v -> c.maxParticles = v, true));
        items.add(new Option("block_entity_tick", 160, 4, () -> c.blockEntityTickDistance, v -> c.blockEntityTickDistance = v, true));
        items.add(new Option("entity_tick", 160, 4, () -> c.entityTickDistance, v -> c.entityTickDistance = v, true));
        items.add(new Option("target_fps", 240, 10, () -> c.targetFps, v -> c.targetFps = v, true));
        items.add(toggle("debug_line", () -> c.debugLine, v -> c.debugLine = v));
        for (int i = 0; i < items.size(); i++) {
            int x = i % 2 == 0 ? left : right;
            int y = top + i / 2 * ROW;
            Object item = items.get(i);
            if (y < 28 || y > this.height - 52) {
                continue;
            }
            if (item instanceof Option option) {
                this.addRenderableWidget(slider(option, x, y, width));
            } else if (item instanceof Toggle toggle) {
                CycleButton<Boolean> button = CycleButton.onOffBuilder(toggle.get().getAsInt() == 1)
                        .withTooltip(value -> Tooltip.create(Component.translatable("boost.option." + toggle.key() + ".tip")))
                        .create(x, y, width, 20, Component.translatable("boost.option." + toggle.key()), (b, value) -> {
                            toggle.set().accept(value ? 1 : 0);
                            BoostConfig.get().save();
                        });
                this.addRenderableWidget(button);
            }
        }
        this.contentHeight = (items.size() + 1) / 2 * ROW;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private record Toggle(String key, IntSupplier get, IntConsumer set) {
    }

    private static Toggle toggle(String key, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        return new Toggle(key, () -> get.getAsBoolean() ? 1 : 0, value -> set.accept(value == 1));
    }

    private AbstractSliderButton slider(Option option, int x, int y, int width) {
        AbstractSliderButton slider = new AbstractSliderButton(x, y, width, 20, Component.empty(), option.get().getAsInt() / (double) option.max()) {
            {
                updateMessage();
            }

            private int current() {
                int raw = (int) Math.round(this.value * option.max() / option.step()) * option.step();
                return Math.max(0, Math.min(option.max(), raw));
            }

            @Override
            protected void updateMessage() {
                int value = current();
                Component shown = value == 0 && option.zeroOff() ? Component.translatable(option.key().equals("target_fps") ? "options.off" : "boost.unlimited")
                        : Component.literal(option.key().equals("max_particles") ? String.valueOf(value) : option.key().equals("target_fps") ? value + " FPS" : value + " " + Component.translatable("boost.blocks").getString());
                this.setMessage(Component.translatable("boost.option." + option.key()).append(": ").append(shown));
            }

            @Override
            protected void applyValue() {
                option.set().accept(current());
            }

            @Override
            public void onRelease(double mouseX, double mouseY) {
                super.onRelease(mouseX, mouseY);
                BoostConfig.get().save();
            }
        };
        slider.setTooltip(Tooltip.create(Component.translatable("boost.option." + option.key() + ".tip")));
        return slider;
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        BoostConfig.get().save();
        this.minecraft.setScreen(this.parent);
    }
}
