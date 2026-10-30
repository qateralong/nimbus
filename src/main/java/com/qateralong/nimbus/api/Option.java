package com.qateralong.nimbus.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public abstract class Option<T, S extends Option<T, S>> {
    private final String id;
    private Component name = Component.empty();
    private Component tooltip;
    private Supplier<T> getter = () -> null;
    private Consumer<T> setter = value -> {
    };
    private T defaultValue;
    private Function<T, Component> formatter = value -> Component.literal(String.valueOf(value));
    private final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);
    private OptionImpact impact;
    private BooleanSupplier enabled = () -> true;
    private BooleanSupplier visible = () -> true;
    private Runnable afterApply = () -> {
    };
    private boolean instant;
    private boolean vanilla;
    private boolean hasPending;
    private T pending;
    private OptionGroup group;

    protected Option(String id) {
        this.id = id;
    }

    public static ToggleOption toggle(String id) {
        return new ToggleOption(id);
    }

    public static SliderOption slider(String id) {
        return new SliderOption(id);
    }

    public static <T> CycleOption<T> cycle(String id) {
        return new CycleOption<>(id);
    }

    public static ActionOption action(String id) {
        return new ActionOption(id);
    }

    @SuppressWarnings("unchecked")
    protected final S self() {
        return (S) this;
    }

    void attach(OptionGroup group) {
        this.group = group;
    }

    public S name(Component name) {
        this.name = name;
        return self();
    }

    public S tooltip(Component tooltip) {
        this.tooltip = tooltip;
        return self();
    }

    public S binding(Supplier<T> getter, Consumer<T> setter) {
        this.getter = getter;
        this.setter = setter;
        return self();
    }

    public S defaultValue(T value) {
        this.defaultValue = value;
        return self();
    }

    public S formatter(Function<T, Component> formatter) {
        this.formatter = formatter;
        return self();
    }

    public S flags(OptionFlag... flags) {
        Collections.addAll(this.flags, flags);
        return self();
    }

    public S impact(OptionImpact impact) {
        this.impact = impact;
        return self();
    }

    public S enabled(BooleanSupplier enabled) {
        this.enabled = enabled;
        return self();
    }

    public S visible(BooleanSupplier visible) {
        this.visible = visible;
        return self();
    }

    public S afterApply(Runnable action) {
        this.afterApply = action;
        return self();
    }

    public S instant() {
        this.instant = true;
        return self();
    }

    public S vanilla() {
        this.vanilla = true;
        return self();
    }

    public String id() {
        return this.id;
    }

    public Component name() {
        return this.name;
    }

    public Component tooltip() {
        return this.tooltip;
    }

    public T defaultValue() {
        return this.defaultValue;
    }

    public Set<OptionFlag> flags() {
        return Collections.unmodifiableSet(this.flags);
    }

    public OptionImpact impact() {
        return this.impact;
    }

    public boolean isEnabled() {
        return this.enabled.getAsBoolean();
    }

    public boolean isVisible() {
        return this.visible.getAsBoolean();
    }

    public boolean isInstant() {
        return this.instant;
    }

    public boolean isVanilla() {
        return this.vanilla;
    }

    public OptionGroup group() {
        return this.group;
    }

    public Component format(T value) {
        return this.formatter.apply(value);
    }

    public T stored() {
        return this.getter.get();
    }

    public T value() {
        return this.hasPending ? this.pending : this.getter.get();
    }

    public void set(T value) {
        if (this.instant) {
            this.setter.accept(value);
            this.hasPending = false;
            this.pending = null;
            if (this.group != null) {
                this.group.page().mod().save();
            }
            this.afterApply.run();
            return;
        }
        if (Objects.equals(value, this.getter.get())) {
            this.hasPending = false;
            this.pending = null;
        } else {
            this.hasPending = true;
            this.pending = value;
        }
    }

    public boolean changed() {
        return this.hasPending;
    }

    public boolean apply() {
        if (!this.hasPending) {
            return false;
        }
        T value = this.pending;
        this.hasPending = false;
        this.pending = null;
        this.setter.accept(value);
        this.afterApply.run();
        return true;
    }

    public void discard() {
        this.hasPending = false;
        this.pending = null;
    }

    public boolean resettable() {
        return this.defaultValue != null;
    }

    public boolean isDefault() {
        return this.defaultValue == null || Objects.equals(this.value(), this.defaultValue);
    }

    public void reset() {
        if (this.defaultValue != null) {
            this.set(this.defaultValue);
        }
    }
}
