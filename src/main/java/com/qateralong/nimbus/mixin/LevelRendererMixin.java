package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Cull;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    private Frustum cullingFrustum;

    @Shadow
    @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void nimbus$frame(DeltaTracker delta, boolean outline, Camera camera, GameRenderer renderer, LightTexture light, Matrix4f view, Matrix4f projection,
                             CallbackInfo ci) {
        Vec3 position = camera.getPosition();
        Cull.beginFrame(position.x, position.y, position.z, this.visibleSections);
    }

    @Inject(method = "prepareCullFrustum", at = @At("RETURN"))
    private void nimbus$frustum(Vec3 position, Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        Cull.frustum = this.cullingFrustum;
    }
}
