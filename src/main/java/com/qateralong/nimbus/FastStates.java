package com.qateralong.nimbus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.state.properties.Property;

public final class FastStates {
    private static Object lastSource;
    private static FastStates lastBuilt;

    private final Property<?>[] properties;
    private final List<?>[] values;
    private final int[] strides;
    private final Object[] states;

    private FastStates(Map<Map<Property<?>, Comparable<?>>, ?> all) {
        Map<Property<?>, Comparable<?>> sample = all.keySet().iterator().next();
        this.properties = sample.keySet().toArray(new Property<?>[0]);
        this.values = new List<?>[this.properties.length];
        this.strides = new int[this.properties.length];
        int stride = 1;
        for (int i = 0; i < this.properties.length; i++) {
            this.values[i] = new ArrayList<>(this.properties[i].getPossibleValues());
            this.strides[i] = stride;
            stride *= this.values[i].size();
        }
        this.states = new Object[stride];
        for (Map.Entry<Map<Property<?>, Comparable<?>>, ?> entry : all.entrySet()) {
            int index = index(entry.getKey());
            if (index >= 0) {
                this.states[index] = entry.getValue();
            }
        }
    }

    public static synchronized FastStates of(Map<Map<Property<?>, Comparable<?>>, ?> all) {
        if (lastSource != all) {
            lastSource = all;
            lastBuilt = all.isEmpty() ? null : new FastStates(all);
        }
        return lastBuilt;
    }

    public int index(Map<Property<?>, Comparable<?>> state) {
        int index = 0;
        for (int i = 0; i < this.properties.length; i++) {
            Comparable<?> value = state.get(this.properties[i]);
            int position = value == null ? -1 : this.values[i].indexOf(value);
            if (position < 0) {
                return -1;
            }
            index += position * this.strides[i];
        }
        return index;
    }

    public Object with(int index, Object property, Object value) {
        for (int i = 0; i < this.properties.length; i++) {
            if (this.properties[i] == property) {
                int position = this.values[i].indexOf(value);
                if (position < 0) {
                    return null;
                }
                int current = index / this.strides[i] % this.values[i].size();
                return this.states[index + (position - current) * this.strides[i]];
            }
        }
        return null;
    }
}
