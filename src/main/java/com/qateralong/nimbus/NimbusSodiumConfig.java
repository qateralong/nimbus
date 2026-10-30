package com.qateralong.nimbus;

import com.qateralong.nimbus.api.ActionOption;
import com.qateralong.nimbus.api.CycleOption;
import com.qateralong.nimbus.api.ModOptions;
import com.qateralong.nimbus.api.NimbusOptionsApi;
import com.qateralong.nimbus.api.Option;
import com.qateralong.nimbus.api.OptionGroup;
import com.qateralong.nimbus.api.OptionPage;
import com.qateralong.nimbus.api.SliderOption;
import com.qateralong.nimbus.api.ToggleOption;
import java.util.ArrayList;
import java.util.List;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.StatefulOptionBuilder;
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
        ModOptionsBuilder target = builder.registerModOptions("nimbus", "Nimbus", version)
                .setNonTintedIcon(ResourceLocation.fromNamespaceAndPath("nimbus", "icon.png"));
        ModOptions source = null;
        for (ModOptions mod : NimbusOptionsApi.mods()) {
            if (mod.id().equals("nimbus")) {
                source = mod;
            }
        }
        if (source == null) {
            return;
        }
        boolean first = true;
        for (OptionPage page : source.pages()) {
            OptionPageBuilder pageBuilder = builder.createOptionPage().setName(page.name());
            if (first) {
                first = false;
                OptionGroupBuilder top = builder.createOptionGroup();
                top.addOption(builder.createExternalButtonOption(id("full_settings"))
                        .setName(Component.translatable("nimbus.option.full_settings"))
                        .setTooltip(Component.translatable("nimbus.option.full_settings.tip"))
                        .setScreenConsumer(parent -> Minecraft.getInstance().setScreen(new NimbusScreen(parent))));
                top.addOption(builder.createIntegerOption(id("render_engine"))
                        .setName(Component.translatable("nimbus.option.render_engine"))
                        .setTooltip(Component.translatable("nimbus.option.render_engine.tip"))
                        .setRange(0, 1, 1)
                        .setDefaultValue(0)
                        .setValueFormatter(value -> Component.translatable("nimbus.engine.sodium"))
                        .setBinding(value -> {
                        }, () -> 0)
                        .setStorageHandler(source::save)
                        .setEnabled(false));
                pageBuilder.addOptionGroup(top);
            }
            boolean any = false;
            for (OptionGroup group : page.groups()) {
                OptionGroupBuilder groupBuilder = builder.createOptionGroup();
                if (group.name() != null) {
                    groupBuilder.setName(group.name());
                }
                boolean added = false;
                for (Option<?, ?> option : group.options()) {
                    if (option.isVanilla()) {
                        continue;
                    }
                    StatefulOptionBuilder<?> built = this.convert(builder, source, option);
                    if (built != null) {
                        groupBuilder.addOption(built);
                        added = true;
                    }
                }
                if (added) {
                    pageBuilder.addOptionGroup(groupBuilder);
                    any = true;
                }
            }
            if (any || page == source.pages().get(0)) {
                target.addPage(pageBuilder);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private StatefulOptionBuilder<?> convert(ConfigBuilder builder, ModOptions source, Option<?, ?> option) {
        if (option instanceof ToggleOption toggle) {
            return decorate(builder.createBooleanOption(id(option.id()))
                    .setName(option.name())
                    .setDefaultValue(toggle.defaultValue() == null ? Boolean.TRUE.equals(toggle.stored()) : toggle.defaultValue())
                    .setBinding(value -> {
                        toggle.set(value);
                        toggle.apply();
                    }, () -> Boolean.TRUE.equals(toggle.stored()))
                    .setStorageHandler(source::save), option);
        }
        if (option instanceof SliderOption slider) {
            return decorate(builder.createIntegerOption(id(option.id()))
                    .setName(option.name())
                    .setRange(slider.min(), slider.max(), slider.step())
                    .setDefaultValue(slider.defaultValue() == null ? slider.stored() : slider.defaultValue())
                    .setValueFormatter(slider::format)
                    .setBinding(value -> {
                        slider.set(value);
                        slider.apply();
                    }, slider::stored)
                    .setStorageHandler(source::save), option);
        }
        if (option instanceof CycleOption<?> cycle && !option.isInstant()) {
            CycleOption<Object> typed = (CycleOption<Object>) cycle;
            List<Object> values = new ArrayList<>(typed.values());
            if (values.isEmpty()) {
                return null;
            }
            return decorate(builder.createIntegerOption(id(option.id()))
                    .setName(option.name())
                    .setRange(0, values.size() - 1, 1)
                    .setDefaultValue(Math.max(0, values.indexOf(typed.defaultValue())))
                    .setValueFormatter(index -> typed.format(values.get(Math.max(0, Math.min(values.size() - 1, index)))))
                    .setBinding(index -> {
                        typed.set(values.get(index));
                        typed.apply();
                    }, () -> Math.max(0, values.indexOf(typed.stored())))
                    .setStorageHandler(source::save), option);
        }
        if (option instanceof ActionOption) {
            return null;
        }
        return null;
    }

    private static <B extends StatefulOptionBuilder<?>> B decorate(B built, Option<?, ?> option) {
        if (option.tooltip() != null) {
            built.setTooltip(option.tooltip());
        }
        if (option.impact() != null) {
            built.setImpact(switch (option.impact()) {
                case LOW -> OptionImpact.LOW;
                case MEDIUM -> OptionImpact.MEDIUM;
                case HIGH -> OptionImpact.HIGH;
                case VARIES -> OptionImpact.VARIES;
            });
        }
        List<OptionFlag> flags = new ArrayList<>();
        for (com.qateralong.nimbus.api.OptionFlag flag : option.flags()) {
            flags.add(switch (flag) {
                case RELOAD_CHUNKS -> OptionFlag.REQUIRES_RENDERER_RELOAD;
                case RELOAD_RESOURCES -> OptionFlag.REQUIRES_ASSET_RELOAD;
                case RESTART -> OptionFlag.REQUIRES_GAME_RESTART;
            });
        }
        if (!flags.isEmpty()) {
            built.setFlags(flags.toArray(new OptionFlag[0]));
        }
        return built;
    }

    private static ResourceLocation id(String key) {
        String path = key.startsWith("nimbus:") ? key.substring(7) : key;
        return ResourceLocation.fromNamespaceAndPath("nimbus", path.replace(':', '/').replace('.', '_'));
    }
}
