package com.qateralong.nimbus.mixin;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Shapes never change after construction, but vanilla scans the voxel grid on every isEmpty() call; client sky light
// sources call it per block of every received chunk.
@Mixin(value = VoxelShape.class, priority = 900)
public abstract class VoxelShapeMixin {
    @Shadow
    @Final
    protected DiscreteVoxelShape shape;

    @Unique
    private byte nimbus$empty;

    /**
     * @author Nimbus
     * @reason cache the result
     */
    @Overwrite
    public boolean isEmpty() {
        byte empty = this.nimbus$empty;
        if (empty == 0) {
            empty = this.shape.isEmpty() ? (byte) 1 : (byte) 2;
            this.nimbus$empty = empty;
        }
        return empty == 1;
    }
}
