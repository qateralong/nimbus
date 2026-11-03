package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Leaves;
import com.qateralong.nimbus.render.FaceOcclusion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = Block.class, priority = 900)
public abstract class BlockFaceMixin {
    /**
     * @author qateralong
     * @reason called for every face of every block while meshing: no callback object, no cache key object
     */
    @Overwrite
    public static boolean shouldRenderFace(BlockState state, BlockGetter level, BlockPos pos, Direction face, BlockPos neighbourPos) {
        if (Leaves.hide(state, level, neighbourPos, face)) {
            return false;
        }
        BlockState neighbour = level.getBlockState(neighbourPos);
        if (state.isSolidRender(level, pos) && neighbour.isSolidRender(level, neighbourPos)) {
            return false;
        }
        if (state.skipRendering(neighbour, face)) {
            return false;
        }
        if (!neighbour.canOcclude()) {
            return true;
        }
        return FaceOcclusion.visible(state, neighbour, level, pos, neighbourPos, face);
    }
}
