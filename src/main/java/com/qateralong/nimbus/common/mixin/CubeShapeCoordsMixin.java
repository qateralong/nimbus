package com.qateralong.nimbus.common.mixin;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.CubePointRange;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

// A new coordinate list per call, on every collision test against a cube shape; the lists are immutable, so each shape
// keeps its three.
@Mixin(CubeVoxelShape.class)
public abstract class CubeShapeCoordsMixin extends VoxelShape {
    @Unique
    private final DoubleList[] nimbus$coords = new DoubleList[3];

    protected CubeShapeCoordsMixin(DiscreteVoxelShape shape) {
        super(shape);
    }

    /**
     * @author qateralong
     * @reason built once per axis
     */
    @Overwrite
    public DoubleList getCoords(Direction.Axis axis) {
        int index = axis.ordinal();
        DoubleList coords = this.nimbus$coords[index];
        if (coords == null) {
            coords = new CubePointRange(this.shape.getSize(axis));
            this.nimbus$coords[index] = coords;
        }
        return coords;
    }
}
