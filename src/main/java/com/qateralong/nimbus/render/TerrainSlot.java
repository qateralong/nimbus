package com.qateralong.nimbus.render;

public interface TerrainSlot {
    int nimbus$start();

    int nimbus$count();

    void nimbus$set(int start, int count);

    long nimbus$copy();

    void nimbus$setCopy(long address);

    Faces nimbus$faces();

    void nimbus$setFaces(Faces faces);

    void nimbus$bind(int section, int layer);

    int nimbus$section();

    int nimbus$layer();

    // stores queued for this buffer and not run yet
    int nimbus$pending(int delta);
}
