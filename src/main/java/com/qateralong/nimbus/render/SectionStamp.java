package com.qateralong.nimbus.render;

public interface SectionStamp {
    int nimbus$visibleFrame();

    void nimbus$setVisibleFrame(int frame);

    int nimbus$lightRetry();

    void nimbus$setLightRetry(int frame);

    TerrainSlot[] nimbus$slots();

    int nimbus$id();
}
