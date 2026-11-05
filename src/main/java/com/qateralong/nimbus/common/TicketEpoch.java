package com.qateralong.nimbus.common;

// Bumped whenever chunk ticket levels are recomputed, so per-loop caches of "does this chunk tick" stay exact.
public final class TicketEpoch {
    public static volatile int value;

    private TicketEpoch() {
    }
}
