package com.qateralong.nimbus.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace")
public abstract class AoFaceMixin {
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> NIMBUS_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    // calculate() only uses its mutable position locally, once per quad
    @Redirect(method = "calculate", at = @At(value = "NEW", target = "()Lnet/minecraft/core/BlockPos$MutableBlockPos;"))
    private BlockPos.MutableBlockPos nimbus$pos() {
        return NIMBUS_POS.get();
    }
}
