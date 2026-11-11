package com.qateralong.nimbus.common;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.AbstractSet;
import java.util.Iterator;
import net.minecraft.world.ticks.ScheduledTick;

// The (position, type) set of scheduled ticks keyed by the packed position: no BlockPos hashing and no probe objects.
// A position almost always holds one type; more are kept in an array.
public final class TickSet extends AbstractSet<ScheduledTick<?>> {
    // most chunks never hold a scheduled tick: the map comes with the first one
    private Long2ObjectOpenHashMap<Object> types;
    private int size;

    public boolean contains(long pos, Object type) {
        if (this.size == 0) {
            return false;
        }
        Object held = this.types.get(pos);
        if (held == type) {
            return held != null;
        }
        if (held instanceof Object[] many) {
            for (Object t : many) {
                if (t == type) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean add(long pos, Object type) {
        if (this.types == null) {
            this.types = new Long2ObjectOpenHashMap<>(4);
        }
        Object held = this.types.putIfAbsent(pos, type);
        if (held == null) {
            this.size++;
            return true;
        }
        if (held == type) {
            return false;
        }
        Object[] many;
        if (held instanceof Object[] array) {
            for (Object t : array) {
                if (t == type) {
                    return false;
                }
            }
            many = java.util.Arrays.copyOf(array, array.length + 1);
        } else {
            many = new Object[]{held, null};
        }
        many[many.length - 1] = type;
        this.types.put(pos, many);
        this.size++;
        return true;
    }

    public boolean remove(long pos, Object type) {
        if (this.size == 0) {
            return false;
        }
        Object held = this.types.get(pos);
        if (held == null) {
            return false;
        }
        if (held == type) {
            this.types.remove(pos);
            this.size--;
            return true;
        }
        if (!(held instanceof Object[] array)) {
            return false;
        }
        for (int i = 0; i < array.length; i++) {
            if (array[i] == type) {
                if (array.length == 2) {
                    this.types.put(pos, array[1 - i]);
                } else {
                    Object[] rest = new Object[array.length - 1];
                    System.arraycopy(array, 0, rest, 0, i);
                    System.arraycopy(array, i + 1, rest, i, array.length - i - 1);
                    this.types.put(pos, rest);
                }
                this.size--;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean add(ScheduledTick<?> tick) {
        return this.add(tick.pos().asLong(), tick.type());
    }

    @Override
    public boolean remove(Object o) {
        return o instanceof ScheduledTick<?> tick && this.remove(tick.pos().asLong(), tick.type());
    }

    @Override
    public boolean contains(Object o) {
        return o instanceof ScheduledTick<?> tick && this.contains(tick.pos().asLong(), tick.type());
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        return this.size == 0;
    }

    @Override
    public void clear() {
        if (this.size > 0) {
            this.types.clear();
            this.size = 0;
        }
    }

    // vanilla only adds, removes and probes; nothing walks these sets
    @Override
    public Iterator<ScheduledTick<?>> iterator() {
        throw new UnsupportedOperationException("TickSet is not iterable");
    }
}
