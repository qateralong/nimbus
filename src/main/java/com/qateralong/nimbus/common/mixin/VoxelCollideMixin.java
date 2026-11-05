package com.qateralong.nimbus.common.mixin;

import net.minecraft.core.Direction;
import net.minecraft.core.AxisCycle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Most shapes an entity collides with are one full voxel (moved full blocks, unaligned boxes). For a 1x1x1 grid
// vanilla's collideX, binary searches and all, reduces to comparing against the box's two coordinates per axis; this
// is that reduction, with the same 1.0E-7 margins. Other shapes take vanilla's path.
@Mixin(VoxelShape.class)
public abstract class VoxelCollideMixin {
    @Unique
    private static final double EPS = 1.0E-7;

    @Shadow
    @Final
    protected DiscreteVoxelShape shape;

    @Shadow
    protected abstract double collideX(AxisCycle movementAxis, AABB collisionBox, double desiredOffset);

    @Shadow
    protected abstract double get(Direction.Axis axis, int index);

    // 0 unknown, 1 one full voxel, 2 anything else
    @Unique
    private byte nimbus$single;

    /**
     * @author qateralong
     * @reason single full voxel shapes skip the general grid walk
     */
    @Overwrite
    public double collide(Direction.Axis movementAxis, AABB box, double desiredOffset) {
        byte single = this.nimbus$single;
        if (single == 0) {
            DiscreteVoxelShape grid = this.shape;
            single = grid.getXSize() == 1 && grid.getYSize() == 1 && grid.getZSize() == 1 && grid.isFull(0, 0, 0) ? (byte) 1 : (byte) 2;
            this.nimbus$single = single;
        }
        if (single != 1) {
            return this.collideX(AxisCycle.between(movementAxis, Direction.Axis.X), box, desiredOffset);
        }
        if (Math.abs(desiredOffset) < EPS) {
            return 0.0;
        }
        Direction.Axis second;
        Direction.Axis third;
        switch (movementAxis) {
            case X -> {
                second = Direction.Axis.Y;
                third = Direction.Axis.Z;
            }
            case Y -> {
                second = Direction.Axis.Z;
                third = Direction.Axis.X;
            }
            default -> {
                second = Direction.Axis.X;
                third = Direction.Axis.Y;
            }
        }
        if (!(box.min(second) + EPS < this.get(second, 1) && box.max(second) - EPS >= this.get(second, 0)
                && box.min(third) + EPS < this.get(third, 1) && box.max(third) - EPS >= this.get(third, 0))) {
            return desiredOffset;
        }
        if (desiredOffset > 0.0) {
            double max = box.max(movementAxis);
            double lo = this.get(movementAxis, 0);
            if (max - EPS < lo) {
                double gap = lo - max;
                if (gap >= -EPS) {
                    desiredOffset = Math.min(desiredOffset, gap);
                }
            }
        } else {
            double min = box.min(movementAxis);
            double hi = this.get(movementAxis, 1);
            if (min + EPS >= hi) {
                double gap = hi - min;
                if (gap <= EPS) {
                    desiredOffset = Math.max(desiredOffset, gap);
                }
            }
        }
        return desiredOffset;
    }
}
