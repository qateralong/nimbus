package com.qateralong.nimbus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
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

public class NimbusScreen extends Screen {
    private static final int ROW = 22;
    private static final String[] TABS = {"general", "quality", "particles", "entities", "blocks", "performance"};
    private static String tab = "general";
    private final Screen parent;
    private int scroll;
    private int contentHeight;

    public NimbusScreen(Screen parent) {
        super(Component.translatable("nimbus.title"));
        this.parent = parent;
    }

    private interface Entry {
    }

    private record Slider(String key, int min, int max, int step, IntSupplier get, IntConsumer set, String unit) implements Entry {
    }

    private record Toggle(String key, BooleanSupplier get, Consumer<Boolean> set) implements Entry {
    }

    private record Vanilla(AbstractWidgetFactory factory) implements Entry {
    }

    private interface AbstractWidgetFactory {
        AbstractWidget create(Options options, int x, int y, int width);
    }

    private static Vanilla vanilla(net.minecraft.client.OptionInstance<?> option) {
        return new Vanilla(option::createButton);
    }

    private List<Entry> entries() {
        NimbusConfig c = NimbusConfig.get();
        Options o = Minecraft.getInstance().options;
        List<Entry> list = new ArrayList<>();
        switch (tab) {
            case "general" -> {
                list.add(new Toggle("enabled", () -> c.enabled, v -> c.enabled = v));
                list.add(vanilla(o.renderDistance()));
                list.add(vanilla(o.simulationDistance()));
                list.add(vanilla(o.framerateLimit()));
                list.add(vanilla(o.enableVsync()));
                list.add(vanilla(o.fullscreen()));
                list.add(new Toggle("borderless", () -> c.borderlessFullscreen, v -> c.borderlessFullscreen = v));
                list.add(vanilla(o.guiScale()));
                list.add(vanilla(o.gamma()));
                list.add(vanilla(o.bobView()));
                list.add(vanilla(o.fov()));
                list.add(new Toggle("debug_line", () -> c.debugLine, v -> c.debugLine = v));
                list.add(new Toggle("steady_debug", () -> c.steadyDebug, v -> c.steadyDebug = v));
                list.add(new Toggle("toasts", () -> c.toasts, v -> c.toasts = v));
                list.add(new Slider("fps_overlay", 0, 2, 1, () -> c.fpsOverlay, v -> c.fpsOverlay = v, "corner"));
            }
            case "quality" -> {
                list.add(vanilla(o.graphicsMode()));
                list.add(vanilla(o.ambientOcclusion()));
                list.add(vanilla(o.biomeBlendRadius()));
                list.add(vanilla(o.mipmapLevels()));
                list.add(vanilla(o.cloudStatus()));
                list.add(vanilla(o.entityShadows()));
                list.add(new Slider("render_scale", 25, 100, 5, () -> c.renderScale, v -> c.renderScale = v, "%"));
                list.add(new Slider("sharpness", 0, 100, 5, () -> c.sharpness, v -> c.sharpness = v, "%"));
                list.add(new Toggle("sharp_upscale", () -> c.sharpUpscale, v -> c.sharpUpscale = v));
                list.add(new Toggle("sky", () -> c.sky, v -> c.sky = v));
                list.add(new Toggle("fog", () -> c.fog, v -> c.fog = v));
                list.add(new Toggle("weather", () -> c.weather, v -> c.weather = v));
                list.add(new Toggle("vignette", () -> c.vignette, v -> c.vignette = v));
                list.add(new Toggle("beacon_beams", () -> c.beaconBeams, v -> c.beaconBeams = v));
                list.add(new Toggle("animated_textures", () -> c.animatedTextures, v -> c.animatedTextures = v));
                for (String kind : Animations.KINDS) {
                    list.add(new Toggle("anim." + kind, () -> !c.disabledAnimations.contains(kind), v -> {
                        c.disabledAnimations.remove(kind);
                        if (!v) {
                            c.disabledAnimations.add(kind);
                        }
                    }));
                }
                list.add(new Toggle("sun_moon", () -> c.sunMoon, v -> c.sunMoon = v));
                list.add(new Toggle("stars", () -> c.stars, v -> c.stars = v));
                list.add(new Slider("fog_distance", 10, 300, 10, () -> c.fogDistance, v -> c.fogDistance = v, "%"));
                list.add(new Slider("cloud_height", 0, 320, 8, () -> c.cloudHeight, v -> c.cloudHeight = v, "blocks"));
                list.add(vanilla(o.glintStrength()));
                list.add(vanilla(o.screenEffectScale()));
                list.add(vanilla(o.fovEffectScale()));
            }
            case "particles" -> {
                list.add(vanilla(o.particles()));
                list.add(new Toggle("particle_culling", () -> c.particleCulling, v -> c.particleCulling = v));
                list.add(new Slider("particle", 0, 128, 4, () -> c.particleDistance, v -> c.particleDistance = v, "blocks"));
                list.add(new Slider("max_particles", 0, 16000, 500, () -> c.maxParticles, v -> c.maxParticles = v, ""));
                list.add(new Toggle("block_break_particles", () -> c.blockBreakParticles, v -> c.blockBreakParticles = v));
                List<String> types = new ArrayList<>();
                net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.keySet().forEach(key -> types.add(key.toString()));
                types.sort(null);
                for (String type : types) {
                    list.add(new Toggle("particle_type:" + type, () -> !c.disabledParticles.contains(type), v -> {
                        c.disabledParticles.remove(type);
                        if (!v) {
                            c.disabledParticles.add(type);
                        }
                    }));
                }
            }
            case "entities" -> {
                list.add(vanilla(o.entityDistanceScaling()));
                list.add(new Toggle("render_frames", () -> c.renderFrames, v -> c.renderFrames = v));
                list.add(new Toggle("render_armor_stands", () -> c.renderArmorStands, v -> c.renderArmorStands = v));
                list.add(new Toggle("render_paintings", () -> c.renderPaintings, v -> c.renderPaintings = v));
                list.add(new Toggle("section_culling", () -> c.sectionCulling, v -> c.sectionCulling = v));
                list.add(new Toggle("occlusion_culling", () -> c.occlusionCulling, v -> c.occlusionCulling = v));
                list.add(new Slider("item", 0, 128, 4, () -> c.itemDistance, v -> c.itemDistance = v, "blocks"));
                list.add(new Slider("experience", 0, 128, 4, () -> c.experienceDistance, v -> c.experienceDistance = v, "blocks"));
                list.add(new Slider("frame", 0, 160, 4, () -> c.frameDistance, v -> c.frameDistance = v, "blocks"));
                list.add(new Slider("armor_stand", 0, 160, 4, () -> c.armorStandDistance, v -> c.armorStandDistance = v, "blocks"));
                list.add(new Slider("painting", 0, 192, 8, () -> c.paintingDistance, v -> c.paintingDistance = v, "blocks"));
                list.add(new Slider("projectile", 0, 160, 4, () -> c.projectileDistance, v -> c.projectileDistance = v, "blocks"));
                list.add(new Slider("vehicle", 0, 192, 8, () -> c.vehicleDistance, v -> c.vehicleDistance = v, "blocks"));
                list.add(new Slider("mob", 0, 192, 8, () -> c.mobDistance, v -> c.mobDistance = v, "blocks"));
                list.add(new Slider("other", 0, 192, 8, () -> c.otherDistance, v -> c.otherDistance = v, "blocks"));
                list.add(new Slider("detail", 0, 128, 4, () -> c.detailDistance, v -> c.detailDistance = v, "blocks"));
                list.add(new Slider("shadow", 0, 64, 2, () -> c.shadowDistance, v -> c.shadowDistance = v, "blocks"));
                list.add(new Slider("name_tag", 0, 64, 2, () -> c.nameTagDistance, v -> c.nameTagDistance = v, "blocks"));
            }
            case "blocks" -> {
                list.add(new Slider("block_entity", 0, 128, 4, () -> c.blockEntityDistance, v -> c.blockEntityDistance = v, "blocks"));
                list.add(new Slider("sign_text", 0, 64, 2, () -> c.signTextDistance, v -> c.signTextDistance = v, "blocks"));
                list.add(new Slider("frame_content", 0, 128, 4, () -> c.frameContentDistance, v -> c.frameContentDistance = v, "blocks"));
                list.add(new Slider("block_entity_tick", 0, 160, 4, () -> c.blockEntityTickDistance, v -> c.blockEntityTickDistance = v, "blocks"));
            }
            default -> {
                list.add(new Slider("render_ahead", 0, 4, 1, () -> c.renderAhead, v -> c.renderAhead = v, "frames"));
                list.add(vanilla(o.prioritizeChunkUpdates()));
                list.add(new Slider("target_fps", 0, 240, 10, () -> c.targetFps, v -> c.targetFps = v, "fps"));
                list.add(new Toggle("adaptive_resolution", () -> c.adaptiveResolution, v -> c.adaptiveResolution = v));
                list.add(new Toggle("hidden_tick", () -> c.hiddenTickLod, v -> c.hiddenTickLod = v));
                list.add(new Slider("entity_tick", 0, 160, 4, () -> c.entityTickDistance, v -> c.entityTickDistance = v, "blocks"));
            }
        }
        return list;
    }

    static void openTab(Minecraft client, String name) {
        tab = name;
        client.setScreen(new NimbusScreen(null));
    }

    @Override
    protected void init() {
        int tabWidth = Math.min(110, (this.width - 20) / TABS.length - 4);
        int tx = this.width / 2 - (TABS.length * (tabWidth + 4) - 4) / 2;
        for (String name : TABS) {
            Button button = Button.builder(Component.translatable("nimbus.tab." + name), b -> {
                tab = name;
                this.scroll = 0;
                this.rebuildWidgets();
            }).bounds(tx, 26, tabWidth, 20).build();
            button.active = !tab.equals(name);
            this.addRenderableWidget(button);
            tx += tabWidth + 4;
        }
        int presetWidth = Math.min(120, (this.width - 100) / Presets.NAMES.length - 4);
        int px = this.width / 2 - (Presets.NAMES.length * (presetWidth + 4) - 4) / 2 + 30;
        for (String name : Presets.NAMES) {
            Button button = Button.builder(Component.translatable("nimbus.preset." + name), b -> {
                Presets.apply(name);
                this.rebuildWidgets();
            }).bounds(px, 50, presetWidth, 20).tooltip(Tooltip.create(Component.translatable("nimbus.preset." + name + ".tip"))).build();
            button.active = !name.equals(NimbusConfig.get().preset);
            this.addRenderableWidget(button);
            px += presetWidth + 4;
        }
        int width = Math.min(310, this.width / 2 - 14);
        int left = this.width / 2 - width - 4;
        int right = this.width / 2 + 4;
        int top = 80 - this.scroll;
        List<Entry> entries = entries();
        Options options = Minecraft.getInstance().options;
        for (int i = 0; i < entries.size(); i++) {
            int x = i % 2 == 0 ? left : right;
            int y = top + i / 2 * ROW;
            if (y < 76 || y > this.height - 52) {
                continue;
            }
            Entry entry = entries.get(i);
            if (entry instanceof Slider slider) {
                this.addRenderableWidget(slider(slider, x, y, width));
            } else if (entry instanceof Toggle toggle) {
                this.addRenderableWidget(CycleButton.onOffBuilder(toggle.get().getAsBoolean())
                        .withTooltip(value -> Tooltip.create(tipOf(toggle.key())))
                        .create(x, y, width, 20, labelOf(toggle.key()), (b, value) -> {
                            toggle.set().accept(value);
                            changed();
                        }));
            } else if (entry instanceof Vanilla vanilla) {
                this.addRenderableWidget(vanilla.factory().create(options, x, y, width));
            }
        }
        this.contentHeight = (entries.size() + 1) / 2 * ROW;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private static Component labelOf(String key) {
        if (key.startsWith("particle_type:")) {
            String id = key.substring("particle_type:".length());
            return Component.literal(id.startsWith("minecraft:") ? id.substring(10) : id);
        }
        return Component.translatable("nimbus.option." + key);
    }

    private static Component tipOf(String key) {
        if (key.startsWith("particle_type:")) {
            return Component.translatable("nimbus.option.particle_type.tip");
        }
        return Component.translatable("nimbus.option." + key + ".tip");
    }

    private static void changed() {
        NimbusConfig config = NimbusConfig.get();
        config.preset = "custom";
        config.save();
    }

    private AbstractSliderButton slider(Slider option, int x, int y, int width) {
        double range = option.max() - option.min();
        AbstractSliderButton slider = new AbstractSliderButton(x, y, width, 20, Component.empty(), (option.get().getAsInt() - option.min()) / range) {
            {
                updateMessage();
            }

            private int current() {
                int raw = option.min() + (int) Math.round(this.value * range / option.step()) * option.step();
                return Math.max(option.min(), Math.min(option.max(), raw));
            }

            @Override
            protected void updateMessage() {
                int value = current();
                Component shown;
                if (option.unit().equals("corner")) {
                    shown = Component.translatable("nimbus.corner." + value);
                } else if (value == 0 && option.key().equals("cloud_height")) {
                    shown = Component.translatable("nimbus.default");
                } else if (value == 0 && option.min() == 0 && !option.key().equals("sharpness")) {
                    shown = Component.translatable(option.key().equals("target_fps") || option.key().equals("render_ahead") ? "options.off" : "nimbus.unlimited");
                } else {
                    shown = Component.literal(value + switch (option.unit()) {
                        case "blocks" -> " " + Component.translatable("nimbus.blocks").getString();
                        case "frames" -> " " + Component.translatable("nimbus.frames").getString();
                        case "fps" -> " FPS";
                        default -> option.unit();
                    });
                }
                this.setMessage(Component.translatable("nimbus.option." + option.key()).append(": ").append(shown));
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
        slider.setTooltip(Tooltip.create(Component.translatable("nimbus.option." + option.key() + ".tip")));
        return slider;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, this.contentHeight - (this.height - 130));
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        int presetWidth = Math.min(120, (this.width - 100) / Presets.NAMES.length - 4);
        int px = this.width / 2 - (Presets.NAMES.length * (presetWidth + 4) - 4) / 2 + 30;
        Component label = Component.translatable("nimbus.presets");
        graphics.drawString(this.font, label, px - 8 - this.font.width(label), 56, 0xFFD27A);
    }

    @Override
    public void onClose() {
        NimbusConfig.get().save();
        Minecraft.getInstance().options.save();
        this.minecraft.setScreen(this.parent);
    }
}
