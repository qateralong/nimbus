package com.qateralong.nimbus.api;

import java.util.List;
import java.util.function.Supplier;

public final class CycleOption<T> extends Option<T, CycleOption<T>> {
    private Supplier<List<T>> values = List::of;

    CycleOption(String id) {
        super(id);
    }

    public CycleOption<T> values(List<T> values) {
        List<T> copy = List.copyOf(values);
        this.values = () -> copy;
        return this;
    }

    public CycleOption<T> values(Supplier<List<T>> values) {
        this.values = values;
        return this;
    }

    public List<T> values() {
        return this.values.get();
    }

    public T step(int direction) {
        List<T> list = this.values();
        if (list.isEmpty()) {
            return this.value();
        }
        int index = list.indexOf(this.value());
        int next = Math.floorMod((index < 0 ? 0 : index) + direction, list.size());
        return list.get(next);
    }
}
