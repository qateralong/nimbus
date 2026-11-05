package com.qateralong.nimbus.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.common.BlockCursor;
import com.qateralong.nimbus.common.ShapeOverlap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Boat ground and water checks: blocks read through the last chunk instead of a lookup each, and the ground overlap with
// the thin box under the hull tested on the unmoved shape instead of a moved copy per block.
@Mixin(Boat.class)
public abstract class BoatFrictionMixin {
    @Unique
    private double nimbus$x;
    @Unique
    private double nimbus$y;
    @Unique
    private double nimbus$z;

    @Inject(method = {"getGroundFriction", "checkInWater", "isUnderwater", "getWaterLevelAbove"}, at = @At("HEAD"))
    private void nimbus$start(CallbackInfoReturnable<?> cir) {
        BlockCursor.reset();
    }

    @WrapOperation(method = "getGroundFriction", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState nimbus$state(Level level, BlockPos pos, Operation<BlockState> original) {
        BlockCursor cursor = BlockCursor.of(level);
        return cursor == null ? original.call(level, pos) : cursor.state(level, pos);
    }

    @WrapOperation(method = {"checkInWater", "isUnderwater", "getWaterLevelAbove"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private FluidState nimbus$fluid(Level level, BlockPos pos, Operation<FluidState> original) {
        BlockCursor cursor = BlockCursor.of(level);
        return cursor == null ? original.call(level, pos) : cursor.fluid(level, pos);
    }

    @WrapOperation(method = "getGroundFriction", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/VoxelShape;move(DDD)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape nimbus$move(VoxelShape shape, double x, double y, double z, Operation<VoxelShape> original) {
        this.nimbus$x = x;
        this.nimbus$y = y;
        this.nimbus$z = z;
        return shape;
    }

    @WrapOperation(method = "getGroundFriction", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"))
    private boolean nimbus$overlap(VoxelShape shape, VoxelShape box, BooleanOp op, Operation<Boolean> original) {
        if (shape.isEmpty()) {
            return false;
        }
        int hit = ShapeOverlap.test(shape, this.nimbus$x, this.nimbus$y, this.nimbus$z, box);
        return hit == ShapeOverlap.UNKNOWN ? original.call(shape.move(this.nimbus$x, this.nimbus$y, this.nimbus$z), box, op) : hit == 1;
    }
}
