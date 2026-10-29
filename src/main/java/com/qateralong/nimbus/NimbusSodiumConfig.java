package com.qateralong.nimbus;

import java.util.List;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class NimbusSodiumConfig implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        try {
            register(builder);
        } catch (RuntimeException | LinkageError e) {
            Nimbus.LOGGER.error("Nimbus page for Sodium settings could not be registered", e);
        }
    }

    private void register(ConfigBuilder builder) {
        String version = FabricLoader.getInstance().getModContainer("nimbus").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
        ModOptionsBuilder mod = builder.registerModOptions("nimbus", "Nimbus", version)
                .setNonTintedIcon(ResourceLocation.fromNamespaceAndPath("nimbus", "icon.png"));
        NimbusConfig defaults = new NimbusConfig();
        NimbusConfig config = NimbusConfig.get();
        for (String page : NimbusOptions.PAGES) {
            OptionPageBuilder options = builder.createOptionPage().setName(Component.translatable("nimbus.tab." + page));
            OptionGroupBuilder main = builder.createOptionGroup();
            OptionGroupBuilder particles = null;
            if (page.equals("general")) {
                main.addOption(builder.createExternalButtonOption(id("full_settings"))
                        .setName(Component.translatable("nimbus.option.full_settings"))
                        .setTooltip(Component.translatable("nimbus.option.full_settings.tip"))
                        .setScreenConsumer(parent -> Minecraft.getInstance().setScreen(new NimbusScreen(parent))));
                main.addOption(builder.createIntegerOption(id("render_engine"))
                        .setName(Component.translatable("nimbus.option.render_engine"))
                        .setTooltip(Component.translatable("nimbus.option.render_engine.tip"))
                        .setRange(0, 1, 1)
                        .setDefaultValue(0)
                        .setValueFormatter(value -> Component.translatable("nimbus.engine.sodium"))
                        .setBinding(value -> {
                        }, () -> 0)
                        .setStorageHandler(config::save)
                        .setEnabled(false));
            }
            List<NimbusOptions.Entry> entries = NimbusOptions.entries(page, config);
            List<NimbusOptions.Entry> fallback = NimbusOptions.entries(page, defaults);
            for (int i = 0; i < entries.size(); i++) {
                NimbusOptions.Entry entry = entries.get(i);
                NimbusOptions.Entry initial = i < fallback.size() ? fallback.get(i) : entry;
                if (entry instanceof NimbusOptions.Toggle toggle) {
                    boolean particleType = toggle.key().startsWith("particle_type:");
                    var option = builder.createBooleanOption(id(toggle.key()))
                            .setName(NimbusOptions.label(toggle.key()))
                            .setTooltip(NimbusOptions.tip(toggle.key()))
                            .setDefaultValue(initial instanceof NimbusOptions.Toggle base ? base.get().getAsBoolean() : toggle.get().getAsBoolean())
                            .setBinding(value -> {
                                toggle.set().accept(value);
                                config.preset = "custom";
                            }, () -> toggle.get().getAsBoolean())
                            .setStorageHandler(config::save);
                    if (particleType) {
                        if (particles == null) {
                            particles = builder.createOptionGroup().setName(Component.translatable("nimbus.group.particle_types"));
                        }
                        particles.addOption(option);
                    } else {
                        main.addOption(option);
                    }
                } else if (entry instanceof NimbusOptions.Slider slider) {
                    main.addOption(builder.createIntegerOption(id(slider.key()))
                            .setName(NimbusOptions.label(slider.key()))
                            .setTooltip(NimbusOptions.tip(slider.key()))
                            .setRange(slider.min(), slider.max(), slider.step())
                            .setDefaultValue(initial instanceof NimbusOptions.Slider base ? base.get().getAsInt() : slider.get().getAsInt())
                            .setValueFormatter(value -> NimbusOptions.format(slider, value))
                            .setBinding(value -> {
                                slider.set().accept(value);
                                config.preset = "custom";
                            }, () -> slider.get().getAsInt())
                            .setStorageHandler(config::save));
                }
            }
            options.addOptionGroup(main);
            if (particles != null) {
                options.addOptionGroup(particles);
            }
            mod.addPage(options);
        }
    }

    private static ResourceLocation id(String key) {
        return ResourceLocation.fromNamespaceAndPath("nimbus", key.replace(':', '/').replace('.', '_'));
    }
}
