package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.ShapeOverlap;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// "Does this shape touch that box" (boats, inside-block checks, mods) answered by the allocation-free walk; anything
// else goes through vanilla's mergers.
@Mixin(Shapes.class)
public abstract class ShapesJoinMixin {
    @Inject(method = "joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void nimbus$box(VoxelShape shape1, VoxelShape shape2, BooleanOp op, CallbackInfoReturnable<Boolean> cir) {
        if (op == BooleanOp.AND && shape1 != shape2 && !shape1.isEmpty() && !shape2.isEmpty()) {
            int hit = ShapeOverlap.test(shape1, 0.0, 0.0, 0.0, shape2);
            if (hit != ShapeOverlap.UNKNOWN) {
                cir.setReturnValue(hit == 1);
            }
        }
    }
}
