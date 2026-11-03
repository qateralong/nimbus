package com.qateralong.nimbus.render;

import net.minecraft.world.level.chunk.PalettedContainer;

// Vanilla copies every non-empty section of a chunk each time a render region needs it. The copy is read-only,
// so it is kept on the source container until the source changes; a ring bounds how many copies stay alive.
public final class StateCopies {
    private static final int LIMIT = 4096;
    private static final Source[] RING = new Source[LIMIT];
    private static int next;

    private StateCopies() {
    }

    public interface Source {
        int nimbus$stamp();

        PalettedContainer<?> nimbus$copy();

        int nimbus$copyStamp();

        void nimbus$keep(PalettedContainer<?> copy, int stamp);
    }

    // Render thread only, like every client-side block change.
    public static PalettedContainer<?> cached(PalettedContainer<?> states) {
        Source source = (Source) states;
        PalettedContainer<?> kept = source.nimbus$copy();
        return kept != null && source.nimbus$copyStamp() == source.nimbus$stamp() ? kept : null;
    }

    public static PalettedContainer<?> keep(PalettedContainer<?> states, PalettedContainer<?> copy) {
        Source source = (Source) states;
        if (source.nimbus$copy() == null) {
            Source old = RING[next];
            if (old != null) {
                old.nimbus$keep(null, 0);
            }
            RING[next] = source;
            next = (next + 1) % LIMIT;
        }
        source.nimbus$keep(copy, source.nimbus$stamp());
        return copy;
    }
}
