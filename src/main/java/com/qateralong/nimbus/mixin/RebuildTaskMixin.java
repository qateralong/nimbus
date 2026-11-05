package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.Terrain;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Uploads queued by a rebuild carry its cancel flag: a cancelled rebuild that already finished meshing must not
// overwrite what a newer rebuild stored.
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$RebuildTask")
public abstract class RebuildTaskMixin {
    @Inject(method = "doTask", at = @At("HEAD"))
    private void nimbus$begin(SectionBufferBuilderPack pack, CallbackInfoReturnable<?> cir) {
        Terrain.TASK.set(((CompileTaskAccessor) (Object) this).nimbus$cancelled());
    }

    @Inject(method = "doTask", at = @At("RETURN"))
    private void nimbus$end(SectionBufferBuilderPack pack, CallbackInfoReturnable<?> cir) {
        Terrain.TASK.remove();
    }
}
