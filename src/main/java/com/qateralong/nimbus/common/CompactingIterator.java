package com.qateralong.nimbus.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

// One pass over an ArrayList where remove() costs nothing: kept elements slide down as the pass goes and the tail is cut
// once at the end. Same order and contents as removing through the list's own iterator, without the shift per removal.
// The pass has to run to the end, as vanilla's block entity loop does.
public final class CompactingIterator<E> implements Iterator<E> {
    private final ArrayList<E> list;
    private final int size;
    private int read;
    private int write;
    private E current;
    private boolean holding;
    private boolean dropped;

    public CompactingIterator(ArrayList<E> list) {
        this.list = list;
        this.size = list.size();
    }

    private void commit() {
        if (this.holding) {
            if (!this.dropped) {
                if (this.write != this.read - 1) {
                    this.list.set(this.write, this.current);
                }
                this.write++;
            }
            this.holding = false;
            this.current = null;
        }
    }

    @Override
    public boolean hasNext() {
        this.commit();
        if (this.read < this.size) {
            return true;
        }
        if (this.write < this.size) {
            this.list.subList(this.write, this.size).clear();
        }
        return false;
    }

    @Override
    public E next() {
        this.commit();
        if (this.read >= this.size) {
            throw new NoSuchElementException();
        }
        this.current = this.list.get(this.read++);
        this.holding = true;
        this.dropped = false;
        return this.current;
    }

    @Override
    public void remove() {
        if (!this.holding || this.dropped) {
            throw new IllegalStateException();
        }
        this.dropped = true;
    }
}
