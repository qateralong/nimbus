package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.SectionBufferMap;
import java.util.stream.Collector;
import java.util.stream.Stream;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Each section allocated five vertex buffers and a hash map up front (~0.5 KB, ~13 MB at 16 chunks); they now appear on first use
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class SectionBufferMapMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Object nimbus$lazyBuffers(Stream<?> stream, Collector<?, ?, ?> collector) {
        return new SectionBufferMap();
    }
}
