package com.qateralong.nimbus.render;

import com.mojang.blaze3d.vertex.VertexFormat;

public interface FastVertices {
    // Address of `count` consecutive quad vertices in `format`, already counted by the builder, or 0 when not possible.
    long nimbus$reserve(VertexFormat format, int count);
}
