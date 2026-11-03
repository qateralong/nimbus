package com.qateralong.nimbus.mixin;

import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidNeighbourMixin {
    @Unique
    private static final ThreadLocal<BlockPos.MutableBlockPos> NIMBUS_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    // the six neighbour lookups at the start of tesselate only pass the position straight to getBlockState
    @Redirect(method = "tesselate", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos;"),
            slice = @Slice(to = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getFluidState()Lnet/minecraft/world/level/material/FluidState;", ordinal = 5)))
    private BlockPos nimbus$neighbour(BlockPos pos, Direction direction) {
        return NIMBUS_POS.get().setWithOffset(pos, direction);
    }
}
