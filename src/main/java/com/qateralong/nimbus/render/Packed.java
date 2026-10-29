package com.qateralong.nimbus.render;

import org.joml.Vector3f;

public final class Packed {
    final Faces faces;
    final int[] sprites;
    final Vector3f[] centroids;
    int[] order;

    Packed(Faces faces, int[] sprites, Vector3f[] centroids) {
        this.faces = faces;
        this.sprites = sprites;
        this.centroids = centroids;
    }
}
