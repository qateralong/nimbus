package com.qateralong.nimbus.common;

import java.util.ArrayList;

// Sleeping block entities registered on what would wake them (a container's contents, a chunk section). Woken and
// forgotten on the next change. Server thread only.
public final class Watchers {
    public interface Sleeper {
        void nimbus$wake();
    }

    public interface Watched {
        void nimbus$watch(Sleeper sleeper);

        void nimbus$wakeAll();
    }

    private Watchers() {
    }

    // holder is null, one sleeper or an ArrayList of them
    @SuppressWarnings("unchecked")
    public static Object add(Object holder, Sleeper sleeper) {
        if (holder == null) {
            return sleeper;
        }
        if (holder == sleeper) {
            return holder;
        }
        if (holder instanceof ArrayList<?> list) {
            if (!list.contains(sleeper)) {
                ((ArrayList<Sleeper>) list).add(sleeper);
            }
            return list;
        }
        ArrayList<Sleeper> list = new ArrayList<>(4);
        list.add((Sleeper) holder);
        list.add(sleeper);
        return list;
    }

    public static void wake(Object holder) {
        if (holder instanceof Sleeper sleeper) {
            sleeper.nimbus$wake();
        } else if (holder instanceof ArrayList<?> list) {
            for (Object sleeper : list) {
                ((Sleeper) sleeper).nimbus$wake();
            }
        }
    }
}
