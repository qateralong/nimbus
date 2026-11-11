package com.qateralong.nimbus.common.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// A moving block's collision shape was rebuilt (Shapes.or of a moved copy) on every collision query. Shapes are
// immutable: the same two input shapes moved by the same amount give the same result, so the last one is kept.
@Mixin(value = PistonMovingBlockEntity.class, priority = 900)
public abstract class PistonShapeMixin {
    @Shadow
    @Final
    private static ThreadLocal<Direction> NOCLIP;
    @Shadow
    private BlockState movedState;
    @Shadow
    private Direction direction;
    @Shadow
    private boolean extending;
    @Shadow
    private boolean isSourcePiston;
    @Shadow
    private float progress;

    @Unique
    private VoxelShape nimbus$base;
    @Unique
    private VoxelShape nimbus$moved;
    @Unique
    private float nimbus$extended = Float.NaN;
    @Unique
    private Direction nimbus$direction;
    @Unique
    private VoxelShape nimbus$result;

    @Shadow
    public abstract Direction getMovementDirection();

    @Shadow
    public abstract boolean isSourcePiston();

    @Shadow
    private float getExtendedProgress(float progress) {
        throw new AssertionError();
    }

    /**
     * @author qateralong
     * @reason reuse the combined shape while its inputs are unchanged
     */
    @Overwrite
    public VoxelShape getCollisionShape(BlockGetter level, BlockPos pos) {
        VoxelShape base;
        if (!this.extending && this.isSourcePiston && this.movedState.getBlock() instanceof PistonBaseBlock) {
            base = this.movedState.setValue(PistonBaseBlock.EXTENDED, true).getCollisionShape(level, pos);
        } else {
            base = Shapes.empty();
        }
        Direction noclip = NOCLIP.get();
        if (this.progress < 1.0F && noclip == this.getMovementDirection()) {
            return base;
        }
        BlockState state;
        if (this.isSourcePiston()) {
            state = Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, this.direction)
                    .setValue(PistonHeadBlock.SHORT, this.extending != 1.0F - this.progress < 0.25F);
        } else {
            state = this.movedState;
        }
        VoxelShape moved = state.getCollisionShape(level, pos);
        float extended = this.getExtendedProgress(this.progress);
        VoxelShape result = this.nimbus$result;
        if (result != null && base == this.nimbus$base && moved == this.nimbus$moved && extended == this.nimbus$extended && this.direction == this.nimbus$direction) {
            return result;
        }
        double x = this.direction.getStepX() * extended;
        double y = this.direction.getStepY() * extended;
        double z = this.direction.getStepZ() * extended;
        result = Shapes.or(base, moved.move(x, y, z));
        this.nimbus$base = base;
        this.nimbus$moved = moved;
        this.nimbus$extended = extended;
        this.nimbus$direction = this.direction;
        this.nimbus$result = result;
        return result;
    }
}
