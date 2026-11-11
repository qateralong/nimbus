package com.qateralong.nimbus.common;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.minecraft.world.ticks.ScheduledTick;

// Scheduled ticks of one chunk in drain order, grouped by (trigger tick, priority). Ticks of a group arrive in schedule
// order, which is their run order, so adding is an append and polling an array read instead of heap sifts.
public final class TickQueue<T> extends AbstractQueue<ScheduledTick<T>> {
    private static final class Group<T> {
        long time;
        int priority;
        @SuppressWarnings("unchecked")
        ScheduledTick<T>[] ticks = new ScheduledTick[8];
        int head;
        int tail;

        void add(ScheduledTick<T> tick) {
            if (this.tail == this.ticks.length) {
                if (this.head > 0) {
                    System.arraycopy(this.ticks, this.head, this.ticks, 0, this.tail - this.head);
                    java.util.Arrays.fill(this.ticks, this.tail - this.head, this.tail, null);
                    this.tail -= this.head;
                    this.head = 0;
                } else {
                    this.ticks = java.util.Arrays.copyOf(this.ticks, this.ticks.length * 2);
                }
            }
            int at = this.tail;
            long order = tick.subTickOrder();
            // out of order only for reloaded or copied ticks
            while (at > this.head && this.ticks[at - 1].subTickOrder() > order) {
                at--;
            }
            if (at < this.tail) {
                System.arraycopy(this.ticks, at, this.ticks, at + 1, this.tail - at);
            }
            this.ticks[at] = tick;
            this.tail++;
        }

        void removeAt(int index) {
            System.arraycopy(this.ticks, index + 1, this.ticks, index, this.tail - index - 1);
            this.ticks[--this.tail] = null;
        }

        int compareTo(long time, int priority) {
            int c = Long.compare(this.time, time);
            return c != 0 ? c : Integer.compare(this.priority, priority);
        }
    }

    @SuppressWarnings("unchecked")
    private Group<T>[] groups = new Group[4];
    private int first;
    private int end;
    private int size;
    private Group<T> spare;

    @Override
    public boolean offer(ScheduledTick<T> tick) {
        long time = tick.triggerTick();
        int priority = tick.priority().ordinal();
        // new ticks nearly always land in the last group or right after it
        int at = this.end;
        while (at > this.first) {
            int c = this.groups[at - 1].compareTo(time, priority);
            if (c == 0) {
                this.groups[at - 1].add(tick);
                this.size++;
                return true;
            }
            if (c < 0) {
                break;
            }
            at--;
        }
        Group<T> group = this.spare != null ? this.spare : new Group<>();
        this.spare = null;
        group.time = time;
        group.priority = priority;
        group.add(tick);
        this.insertGroup(at, group);
        this.size++;
        return true;
    }

    private void insertGroup(int at, Group<T> group) {
        if (this.end == this.groups.length) {
            int live = this.end - this.first;
            if (this.first > 0 && live < this.groups.length / 2) {
                System.arraycopy(this.groups, this.first, this.groups, 0, live);
                java.util.Arrays.fill(this.groups, live, this.end, null);
            } else {
                @SuppressWarnings("unchecked")
                Group<T>[] grown = new Group[this.groups.length * 2];
                System.arraycopy(this.groups, this.first, grown, 0, live);
                this.groups = grown;
            }
            at -= this.first;
            this.first = 0;
            this.end = live;
        }
        System.arraycopy(this.groups, at, this.groups, at + 1, this.end - at);
        this.groups[at] = group;
        this.end++;
    }

    private void removeGroup(int index) {
        Group<T> group = this.groups[index];
        if (index == this.first) {
            this.groups[this.first++] = null;
        } else {
            System.arraycopy(this.groups, index + 1, this.groups, index, this.end - index - 1);
            this.groups[--this.end] = null;
        }
        if (this.first == this.end) {
            this.first = 0;
            this.end = 0;
        }
        group.head = 0;
        group.tail = 0;
        this.spare = group;
    }

    @Override
    public ScheduledTick<T> peek() {
        if (this.size == 0) {
            return null;
        }
        Group<T> group = this.groups[this.first];
        return group.ticks[group.head];
    }

    @Override
    public ScheduledTick<T> poll() {
        if (this.size == 0) {
            return null;
        }
        Group<T> group = this.groups[this.first];
        ScheduledTick<T> tick = group.ticks[group.head];
        group.ticks[group.head++] = null;
        this.size--;
        if (group.head == group.tail) {
            this.removeGroup(this.first);
        }
        return tick;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public void clear() {
        for (int i = this.first; i < this.end; i++) {
            this.groups[i] = null;
        }
        this.first = 0;
        this.end = 0;
        this.size = 0;
    }

    @Override
    public Iterator<ScheduledTick<T>> iterator() {
        return new Iterator<>() {
            private int group = TickQueue.this.first;
            private int index = TickQueue.this.first < TickQueue.this.end ? TickQueue.this.groups[TickQueue.this.first].head : 0;
            private int lastGroup = -1;
            private int lastIndex;

            private void settle() {
                while (this.group < TickQueue.this.end && this.index >= TickQueue.this.groups[this.group].tail) {
                    this.group++;
                    if (this.group < TickQueue.this.end) {
                        this.index = TickQueue.this.groups[this.group].head;
                    }
                }
            }

            @Override
            public boolean hasNext() {
                this.settle();
                return this.group < TickQueue.this.end;
            }

            @Override
            public ScheduledTick<T> next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }
                this.lastGroup = this.group;
                this.lastIndex = this.index;
                return TickQueue.this.groups[this.group].ticks[this.index++];
            }

            @Override
            public void remove() {
                if (this.lastGroup < 0) {
                    throw new IllegalStateException();
                }
                Group<T> group = TickQueue.this.groups[this.lastGroup];
                group.removeAt(this.lastIndex);
                TickQueue.this.size--;
                this.index = this.lastIndex;
                if (group.head == group.tail) {
                    boolean front = this.lastGroup == TickQueue.this.first;
                    TickQueue.this.removeGroup(this.lastGroup);
                    // the front group is dropped by moving first, a later one by shifting the rest down
                    this.group = TickQueue.this.end == 0 ? 0 : front ? TickQueue.this.first : this.lastGroup;
                    if (this.group < TickQueue.this.end) {
                        this.index = TickQueue.this.groups[this.group].head;
                    }
                }
                this.lastGroup = -1;
            }
        };
    }
}
