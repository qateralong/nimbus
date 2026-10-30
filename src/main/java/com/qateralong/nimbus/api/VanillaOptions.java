package com.qateralong.nimbus.api;

import java.util.List;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class VanillaOptions {
    private static final int SLIDER_STEPS = 100;

    private VanillaOptions() {
    }

    @SuppressWarnings("unchecked")
    public static Option<?, ?> wrap(String id, OptionInstance<?> instance) {
        return wrapTyped(id, (OptionInstance<Object>) instance);
    }

    @SuppressWarnings("unchecked")
    private static <T> Option<?, ?> wrapTyped(String id, OptionInstance<T> option) {
        Component caption = option.caption;
        Component tip = tooltip(option, option.get());
        OptionInstance.ValueSet<T> values = option.values();
        if (values instanceof OptionInstance.CycleableValueSet<T> cycle) {
            if (option.get() instanceof Boolean) {
                OptionInstance<Boolean> flag = (OptionInstance<Boolean>) option;
                return Option.toggle(id).name(caption).tooltip(tip).vanilla()
                        .binding(flag::get, flag::set)
                        .defaultValue((Boolean) option.initialValue);
            }
            return Option.<T>cycle(id).name(caption).tooltip(tip).vanilla()
                    .values(() -> List.copyOf(cycle.valueListSupplier().getSelectedList()))
                    .binding(option::get, option::set)
                    .formatter(value -> valueText(option, value))
                    .defaultValue(option.initialValue);
        }
        if (values instanceof OptionInstance.IntRangeBase range) {
            OptionInstance<Integer> ints = (OptionInstance<Integer>) option;
            return Option.slider(id).name(caption).tooltip(tip).vanilla()
                    .range(range.minInclusive(), range.maxInclusive(), 1)
                    .binding(ints::get, ints::set)
                    .formatter(value -> valueText(option, (T) value))
                    .defaultValue((Integer) option.initialValue);
        }
        if (values instanceof OptionInstance.SliderableValueSet<T> slider) {
            return Option.slider(id).name(caption).tooltip(tip).vanilla()
                    .range(0, SLIDER_STEPS, 1)
                    .binding(() -> (int) Math.round(slider.toSliderValue(option.get()) * SLIDER_STEPS),
                            position -> option.set(slider.fromSliderValue(position / (double) SLIDER_STEPS)))
                    .formatter(position -> valueText(option, slider.fromSliderValue(position / (double) SLIDER_STEPS)))
                    .defaultValue((int) Math.round(slider.toSliderValue(option.initialValue) * SLIDER_STEPS));
        }
        return null;
    }

    private static <T> Component tooltip(OptionInstance<T> option, T value) {
        try {
            Tooltip tooltip = option.tooltip.apply(value);
            return tooltip == null ? null : tooltip.message;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static <T> Component valueText(OptionInstance<T> option, T value) {
        Component full = option.toString.apply(value);
        String text = full.getString();
        String prefix = option.caption.getString() + ": ";
        if (text.startsWith(prefix)) {
            return Component.literal(text.substring(prefix.length()));
        }
        return full;
    }
}
