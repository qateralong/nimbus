package com.qateralong.nimbus.common.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

// The block above the wire and each neighbour's conductor test were read again for every side; they are pure reads,
// so each is done once. Wire updates cascade, so this runs a great many times per redstone change.
@Mixin(RedStoneWireBlock.class)
public abstract class RedstoneWireMixin {
    @Shadow
    private boolean shouldSignal;

    @Shadow
    private int getWireSignal(BlockState state) {
        throw new AssertionError();
    }

    /**
     * @author qateralong
     * @reason same reads and maxima as vanilla, without the repeated ones
     */
    @Overwrite
    private int calculateTargetStrength(Level level, BlockPos pos) {
        this.shouldSignal = false;
        int best = level.getBestNeighborSignal(pos);
        this.shouldSignal = true;
        int wire = 0;
        if (best < 15) {
            BlockPos above = pos.above();
            boolean aboveConducts = level.getBlockState(above).isRedstoneConductor(level, above);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = pos.relative(direction);
                BlockState state = level.getBlockState(side);
                wire = Math.max(wire, this.getWireSignal(state));
                boolean conducts = state.isRedstoneConductor(level, side);
                if (conducts && !aboveConducts) {
                    wire = Math.max(wire, this.getWireSignal(level.getBlockState(side.above())));
                } else if (!conducts) {
                    wire = Math.max(wire, this.getWireSignal(level.getBlockState(side.below())));
                }
            }
        }
        return Math.max(best, wire - 1);
    }
}
