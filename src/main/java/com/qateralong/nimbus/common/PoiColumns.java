package com.qateralong.nimbus.common;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public interface PoiColumns {
    // the column's sections from the lowest up; null when the column was never read
    @Nullable
    Optional<?>[] nimbus$column(long chunk);
}
