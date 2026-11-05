package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.Terrain;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// When the engine has drawn a layer, vanilla's body still runs over an empty section list instead of being cancelled:
// mods that draw at its end (Litematica's schematic layers) keep working.
@Mixin(LevelRenderer.class)
public abstract class LevelRendererTerrainMixin {
    @Unique
    private static final ObjectArrayList<SectionRenderDispatcher.RenderSection> NIMBUS_NONE = new ObjectArrayList<>();

    @Unique
    private boolean nimbus$drawn;

    @Inject(method = "renderSectionLayer", at = @At("HEAD"))
    private void nimbus$terrain(RenderType layer, double x, double y, double z, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        Terrain.prepare((LevelRenderer) (Object) this, x, y, z);
        com.qateralong.nimbus.render.GpuTimer.layerStart(layer);
        this.nimbus$drawn = Terrain.render((LevelRenderer) (Object) this, layer, x, y, z, modelView, projection);
    }

    @Redirect(method = "renderSectionLayer", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> nimbus$sections(LevelRenderer renderer) {
        return this.nimbus$drawn ? NIMBUS_NONE : ((LevelRendererAccessor) renderer).nimbus$visibleSections();
    }

    @Inject(method = "renderSectionLayer", at = @At("RETURN"))
    private void nimbus$terrainEnd(RenderType layer, double x, double y, double z, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        this.nimbus$drawn = false;
        com.qateralong.nimbus.render.GpuTimer.layerEnd(layer);
    }
}
