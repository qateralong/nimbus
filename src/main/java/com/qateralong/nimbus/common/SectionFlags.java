package com.qateralong.nimbus.common;

// What a chunk section may contain, from its palette (a superset: stale palette entries count). Recomputed lazily after
// any block change in the section.
public interface SectionFlags {
    // blocks reaching outside their cell (fences, walls) or moving pistons
    int LARGE = 1;
    int FLUID = 2;

    int nimbus$flags();

    // bumped on every block change in the section
    int nimbus$version();

    // palette may hold a state of this block; one block remembered per section
    boolean nimbus$mayHave(net.minecraft.world.level.block.Block block);
}
