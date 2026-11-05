package com.qateralong.nimbus.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.IntUnaryOperator;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Saving a section remaps its 4096 palette indices; vanilla asks the new palette again at every change of index. For
// small palettes the answer per index is remembered, so each index is looked up once, in the same first-seen order.
@Mixin(PalettedContainer.class)
public abstract class PalettePackMixin {
    @WrapOperation(method = "pack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/PalettedContainer;swapPalette([ILjava/util/function/IntUnaryOperator;)V"))
    private void nimbus$swap(int[] bits, IntUnaryOperator operator, Operation<Void> original) {
        int max = 0;
        for (int value : bits) {
            max = Math.max(max, value);
        }
        if (max >= 1024) {
            original.call(bits, operator);
            return;
        }
        int[] memo = new int[max + 1];
        java.util.Arrays.fill(memo, -1);
        for (int k = 0; k < bits.length; k++) {
            int value = bits[k];
            int mapped = memo[value];
            if (mapped < 0) {
                mapped = operator.applyAsInt(value);
                memo[value] = mapped;
            }
            bits[k] = mapped;
        }
    }
}
