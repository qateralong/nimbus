package com.qateralong.nimbus.render;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface BlockEntitySnapshot {
    Map<BlockPos, BlockEntity> nimbus$snapshot();
}
