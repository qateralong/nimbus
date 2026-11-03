package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.Visibility;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public abstract class BlockEntityLoopMixin {
    @Redirect(method = "renderLevel", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> nimbus$withBlockEntities(LevelRenderer renderer) {
        if (Visibility.valid && NimbusConfig.get().enabled) {
            return com.qateralong.nimbus.render.Terrain.gpuFrustum() ? Visibility.blockEntitiesInView() : Visibility.WITH_BLOCK_ENTITIES;
        }
        return ((LevelRendererAccessor) renderer).nimbus$visibleSections();
    }
}
