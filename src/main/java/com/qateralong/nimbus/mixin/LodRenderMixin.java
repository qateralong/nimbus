package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.lod.LodRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LodRenderMixin {
    // after the opaque terrain layers: the depth test drops LOD pixels the real world already covers
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", ordinal = 2, shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void nimbus$lod(DeltaTracker delta, boolean outline, Camera camera, GameRenderer gameRenderer, LightTexture light, Matrix4f frustum,
                            Matrix4f projection, CallbackInfo ci) {
        var position = camera.getPosition();
        LodRenderer.render(frustum, projection, position.x, position.y, position.z);
    }
}
