package com.qateralong.nimbus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class Leaves {
    private Leaves() {
    }

    public static boolean hide(BlockState self, BlockGetter level, BlockPos neighbour, Direction face) {
        if (!(self.getBlock() instanceof LeavesBlock)) {
            return false;
        }
        NimbusConfig config = NimbusConfig.get();
        int mode = config.enabled ? config.leavesCulling : 0;
        if (mode == 0 || !(level.getBlockState(neighbour).getBlock() instanceof LeavesBlock)) {
            return false;
        }
        return mode == 2 || level.getBlockState(neighbour.relative(face)).getBlock() instanceof LeavesBlock;
    }
}
