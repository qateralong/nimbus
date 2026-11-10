package com.qateralong.nimbus.render;

import com.mojang.blaze3d.vertex.VertexBuffer;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import net.minecraft.client.renderer.RenderType;

// A vanilla section's per-layer buffers, created on first use: with the Nimbus engine most sections never get any
public final class SectionBufferMap extends AbstractMap<RenderType, VertexBuffer> {
    private static final List<RenderType> LAYERS = RenderType.chunkBufferLayers();
    private final VertexBuffer[] buffers = new VertexBuffer[LAYERS.size()];

    private static int index(Object key) {
        for (int i = 0; i < LAYERS.size(); i++) {
            if (LAYERS.get(i) == key) {
                return i;
            }
        }
        return -1;
    }

    public synchronized VertexBuffer peek(int i) {
        return this.buffers[i];
    }

    @Override
    public synchronized VertexBuffer get(Object key) {
        int i = index(key);
        if (i < 0) {
            return null;
        }
        VertexBuffer buffer = this.buffers[i];
        if (buffer == null) {
            // build workers ask for the buffer they upload to: no GL objects off the render thread, they follow on first use
            boolean off = !com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread();
            if (off) {
                LazyBuffers.offThread(true);
            }
            try {
                buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            } finally {
                if (off) {
                    LazyBuffers.offThread(false);
                }
            }
            this.buffers[i] = buffer;
        }
        return buffer;
    }

    @Override
    public boolean containsKey(Object key) {
        return index(key) >= 0;
    }

    @Override
    public synchronized VertexBuffer put(RenderType key, VertexBuffer value) {
        int i = index(key);
        if (i < 0) {
            throw new IllegalArgumentException("Not a chunk layer: " + key);
        }
        VertexBuffer old = this.buffers[i];
        this.buffers[i] = value;
        return old;
    }

    // only the buffers that exist: closing must not create new ones
    @Override
    public Set<Entry<RenderType, VertexBuffer>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<RenderType, VertexBuffer>> iterator() {
                return new Iterator<>() {
                    private int next = advance(0);

                    private int advance(int from) {
                        while (from < SectionBufferMap.this.buffers.length && SectionBufferMap.this.buffers[from] == null) {
                            from++;
                        }
                        return from;
                    }

                    @Override
                    public boolean hasNext() {
                        return this.next < SectionBufferMap.this.buffers.length;
                    }

                    @Override
                    public Entry<RenderType, VertexBuffer> next() {
                        if (!this.hasNext()) {
                            throw new NoSuchElementException();
                        }
                        int i = this.next;
                        this.next = this.advance(i + 1);
                        return Map.entry(LAYERS.get(i), SectionBufferMap.this.buffers[i]);
                    }
                };
            }

            @Override
            public int size() {
                int count = 0;
                for (VertexBuffer buffer : SectionBufferMap.this.buffers) {
                    if (buffer != null) {
                        count++;
                    }
                }
                return count;
            }
        };
    }
}
