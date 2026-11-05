package com.qateralong.nimbus.common;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;

// A brain's memory map that counts changes of which memories are registered and which hold a value; behaviours whose
// entry conditions depend only on that can reuse their last check while the count stands.
public final class PresenceMap<K, V> extends Reference2ObjectOpenHashMap<K, V> {
    public int version;

    public PresenceMap(Map<K, V> from) {
        super(from);
    }

    private static boolean present(Object value) {
        return value instanceof Optional<?> optional && optional.isPresent();
    }

    @Override
    public V put(K key, V value) {
        boolean had = this.containsKey(key);
        V old = super.put(key, value);
        if (!had || present(old) != present(value)) {
            this.version++;
        }
        return old;
    }

    @Override
    public V remove(Object key) {
        boolean had = this.containsKey(key);
        V old = super.remove(key);
        if (had) {
            this.version++;
        }
        return old;
    }

    @Override
    public void clear() {
        super.clear();
        this.version++;
    }

    // anything else that may change the map counts as a change
    @Override
    public V putIfAbsent(K key, V value) {
        this.version++;
        return super.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        this.version++;
        return super.remove(key, value);
    }

    @Override
    public V replace(K key, V value) {
        this.version++;
        return super.replace(key, value);
    }

    @Override
    public boolean replace(K key, V oldValue, V value) {
        this.version++;
        return super.replace(key, oldValue, value);
    }

    @Override
    public V computeIfAbsent(K key, java.util.function.Function<? super K, ? extends V> function) {
        this.version++;
        return super.computeIfAbsent(key, function);
    }

    @Override
    public V computeIfPresent(K key, java.util.function.BiFunction<? super K, ? super V, ? extends V> function) {
        this.version++;
        return super.computeIfPresent(key, function);
    }

    @Override
    public V compute(K key, java.util.function.BiFunction<? super K, ? super V, ? extends V> function) {
        this.version++;
        return super.compute(key, function);
    }

    @Override
    public V merge(K key, V value, java.util.function.BiFunction<? super V, ? super V, ? extends V> function) {
        this.version++;
        return super.merge(key, value, function);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        super.putAll(map);
        this.version++;
    }
}
