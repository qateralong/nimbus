package com.qateralong.nimbus.render;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

public interface ChunkSections {
    PalettedContainer<BlockState>[] nimbus$containers();

    int nimbus$minSection();
}
