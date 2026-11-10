package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.pack.Pipeline;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The hand keeps the world's depth under a pack (drawn in the near eighth of the depth range, as packs expect from
// MC_HAND_DEPTH), and the pack's composite and final passes run once the hand is in.
@Mixin(GameRenderer.class)
public abstract class PackGameRendererMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void nimbus$packHandClear(int mask, boolean osx, Operation<Void> original) {
        Pipeline p = Pipeline.get();
        if (p != null && p.inWorld()) {
            p.beforeHand();
            return;
        }
        original.call(mask, osx);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void nimbus$packHand(CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null && p.inWorld()) {
            GL11.glDepthRange(0.0, 0.125);
        }
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void nimbus$packHandDone(CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null && p.inWorld()) {
            GL11.glDepthRange(0.0, 1.0);
        }
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void nimbus$packEnd(DeltaTracker delta, CallbackInfo ci) {
        Pipeline p = Pipeline.get();
        if (p != null) {
            p.endWorld();
        }
    }
}
