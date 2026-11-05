package com.qateralong.nimbus.common;

public interface IdleTicker {
    // does this tick's work if it is known to be nothing but bookkeeping; false: tick normally
    boolean nimbus$idleTick();
}
