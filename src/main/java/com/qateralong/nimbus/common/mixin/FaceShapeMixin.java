package com.qateralong.nimbus.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.common.FaceShapes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase$Cache")
public abstract class FaceShapeMixin {
    @Shadow
    @Final
    @Mutable
    VoxelShape[] occlusionShapes;

    @Shadow
    @Final
    @Mutable
    private boolean[] faceSturdy;

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;getFaceShape(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape nimbus$sharedFace(VoxelShape shape, Direction direction, Operation<VoxelShape> original) {
        return FaceShapes.face(shape, direction);
    }

    // both arrays are read-only after this: states with equal contents share one
    @Inject(method = "<init>", at = @At("RETURN"))
    private void nimbus$sharedArrays(CallbackInfo ci) {
        if (this.occlusionShapes != null) {
            this.occlusionShapes = FaceShapes.shared(this.occlusionShapes);
        }
        this.faceSturdy = FaceShapes.shared(this.faceSturdy);
    }
}
