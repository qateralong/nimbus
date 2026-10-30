package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.Terrain;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererTerrainMixin {
    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true)
    private void nimbus$terrain(RenderType layer, double x, double y, double z, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        Terrain.prepare((LevelRenderer) (Object) this, x, y, z);
        com.qateralong.nimbus.render.GpuTimer.layerStart(layer);
        if (Terrain.render((LevelRenderer) (Object) this, layer, x, y, z, modelView, projection)) {
            ci.cancel();
            com.qateralong.nimbus.render.GpuTimer.layerEnd(layer);
        }
    }

    @Inject(method = "renderSectionLayer", at = @At("RETURN"))
    private void nimbus$terrainEnd(RenderType layer, double x, double y, double z, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        com.qateralong.nimbus.render.GpuTimer.layerEnd(layer);
    }
}
