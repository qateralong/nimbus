package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.BuildExecutor;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SectionRenderDispatcher.class)
public abstract class BuildExecutorMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static Executor nimbus$limit(Executor executor) {
        return NimbusConfig.get().enabled ? new BuildExecutor(executor) : executor;
    }
}
