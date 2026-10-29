package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.Visibility;
import java.util.List;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionOcclusionGraph.class)
public abstract class FrustumCollectMixin {
    @Inject(method = "addSectionsInFrustum", at = @At("HEAD"))
    private void nimbus$begin(Frustum frustum, List<SectionRenderDispatcher.RenderSection> output, CallbackInfo ci) {
        Visibility.begin();
    }

    @Redirect(method = "addSectionsInFrustum", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean nimbus$add(List<Object> output, Object section) {
        Visibility.add((SectionRenderDispatcher.RenderSection) section);
        return output.add(section);
    }
}
