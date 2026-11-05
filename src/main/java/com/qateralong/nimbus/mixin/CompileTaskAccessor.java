package com.qateralong.nimbus.mixin;

import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$CompileTask")
public interface CompileTaskAccessor {
    @Accessor("isCancelled")
    AtomicBoolean nimbus$cancelled();
}
