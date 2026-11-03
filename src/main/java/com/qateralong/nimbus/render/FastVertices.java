package com.qateralong.nimbus.render;

public interface FastVertices {
    // Address of `count` consecutive NEW_ENTITY vertices already counted by the builder, or 0 when not possible.
    long nimbus$reserveEntity(int count);
}
