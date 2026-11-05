package com.qateralong.nimbus.common.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelChunk.class)
public interface LevelChunkTickingAccessor {
    @Invoker("isTicking")
    boolean nimbus$isTicking(BlockPos pos);
}
