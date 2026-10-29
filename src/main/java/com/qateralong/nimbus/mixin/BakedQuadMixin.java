package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.QuadDedup;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BakedQuad.class)
public abstract class BakedQuadMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static int[] nimbus$dedup(int[] vertices) {
        return QuadDedup.intern(vertices);
    }
}
