package com.qateralong.nimbus.render;

import java.util.function.Supplier;
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
    public static PalettedContainer<?> get(PalettedContainer<?> states, Supplier<PalettedContainer<?>> copier) {
        Source source = (Source) states;
        PalettedContainer<?> kept = source.nimbus$copy();
        int stamp = source.nimbus$stamp();
        if (kept != null && source.nimbus$copyStamp() == stamp) {
            return kept;
        }
        PalettedContainer<?> copy = copier.get();
        if (kept == null) {
            Source old = RING[next];
            if (old != null) {
                old.nimbus$keep(null, 0);
            }
            RING[next] = source;
            next = (next + 1) % LIMIT;
        }
        source.nimbus$keep(copy, stamp);
        return copy;
    }
}
