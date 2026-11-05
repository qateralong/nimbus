package com.qateralong.nimbus.common;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import net.minecraft.world.phys.shapes.VoxelShape;

// Entity.collectColliders' list, in the same order, with the block sweep run only as far as it is read: an entity
// resting on the ground stops reading at the first block under it.
public final class LazyColliders extends AbstractList<VoxelShape> implements RandomAccess {
    private final ArrayList<VoxelShape> shapes;
    private Iterator<VoxelShape> blocks;

    public LazyColliders(List<VoxelShape> entities, VoxelShape border, Iterable<VoxelShape> blocks) {
        this.shapes = new ArrayList<>(entities.size() + 4);
        this.shapes.addAll(entities);
        if (border != null) {
            this.shapes.add(border);
        }
        this.blocks = blocks.iterator();
    }

    private boolean reach(int index) {
        while (index >= this.shapes.size()) {
            Iterator<VoxelShape> blocks = this.blocks;
            if (blocks == null) {
                return false;
            }
            if (!blocks.hasNext()) {
                this.blocks = null;
                return false;
            }
            this.shapes.add(blocks.next());
        }
        return true;
    }

    @Override
    public VoxelShape get(int index) {
        if (index < 0 || !this.reach(index)) {
            throw new IndexOutOfBoundsException(index);
        }
        return this.shapes.get(index);
    }

    @Override
    public int size() {
        while (this.reach(this.shapes.size())) {
        }
        return this.shapes.size();
    }

    @Override
    public boolean isEmpty() {
        return !this.reach(0);
    }

    @Override
    public Iterator<VoxelShape> iterator() {
        return new Iterator<>() {
            private int next;

            @Override
            public boolean hasNext() {
                return LazyColliders.this.reach(this.next);
            }

            @Override
            public VoxelShape next() {
                if (!LazyColliders.this.reach(this.next)) {
                    throw new NoSuchElementException();
                }
                return LazyColliders.this.shapes.get(this.next++);
            }
        };
    }
}
