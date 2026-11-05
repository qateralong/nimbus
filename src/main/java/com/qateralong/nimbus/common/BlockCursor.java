package com.qateralong.nimbus.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

// Block and fluid reads for loops over a few neighbouring blocks: the chunk of the previous read is reused instead of a
// chunk-cache lookup per block. Server thread only; reset before each loop so no chunk outlives a tick.
public final class BlockCursor {
    private static final BlockCursor SERVER = new BlockCursor();

    private Level level;
    private LevelChunk chunk;
    private int cx;
    private int cz;

    private BlockCursor() {
    }

    // null when the level is not a plain server level read from its own thread
    public static BlockCursor of(Level level) {
        return level.getClass() == ServerLevel.class && level.getServer().isSameThread() ? SERVER : null;
    }

    public static void reset() {
        SERVER.level = null;
        SERVER.chunk = null;
    }

    private LevelChunk chunk(Level level, int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        LevelChunk chunk = this.chunk;
        if (chunk == null || this.level != level || this.cx != cx || this.cz != cz) {
            chunk = level.getChunk(cx, cz);
            this.level = level;
            this.chunk = chunk;
            this.cx = cx;
            this.cz = cz;
        }
        return chunk;
    }

    public BlockState state(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        return this.chunk(level, pos.getX(), pos.getZ()).getBlockState(pos);
    }

    public FluidState fluid(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return this.chunk(level, pos.getX(), pos.getZ()).getFluidState(pos.getX(), pos.getY(), pos.getZ());
    }
}
