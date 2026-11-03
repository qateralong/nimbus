package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.Traversal;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionOcclusionGraph.class)
public abstract class TraversalGraphMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void nimbus$update(boolean smartCull, Camera camera, Frustum frustum, List<SectionRenderDispatcher.RenderSection> sections, CallbackInfo ci) {
        if (!Traversal.enabled()) {
            return;
        }
        Vec3 position = camera.getPosition();
        Traversal.smartCull = smartCull;
        Traversal.cameraX = position.x;
        Traversal.cameraY = position.y;
        Traversal.cameraZ = position.z;
        ci.cancel();
    }

    @Inject(method = "consumeFrustumUpdate", at = @At("HEAD"), cancellable = true)
    private void nimbus$frustum(CallbackInfoReturnable<Boolean> cir) {
        if (Traversal.enabled()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "onSectionCompiled", at = @At("HEAD"), cancellable = true)
    private void nimbus$compiled(SectionRenderDispatcher.RenderSection section, CallbackInfo ci) {
        if (Traversal.enabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"), cancellable = true)
    private void nimbus$chunk(ChunkPos pos, CallbackInfo ci) {
        if (Traversal.enabled()) {
            ci.cancel();
        }
    }
}
