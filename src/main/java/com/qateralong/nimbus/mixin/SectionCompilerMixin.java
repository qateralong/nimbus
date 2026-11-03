package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.PackedBuilder;
import com.qateralong.nimbus.render.SectionLight;
import com.qateralong.nimbus.render.PackedMesh;
import com.qateralong.nimbus.render.Terrain;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private void nimbus$light(SectionPos section, RenderChunkRegion region, VertexSorting sorting, SectionBufferBuilderPack pack,
                              CallbackInfoReturnable<SectionCompiler.Results> cir) {
        if (NimbusConfig.get().enabled) {
            ((com.qateralong.nimbus.render.BlockWindow) region).nimbus$begin(section);
        }
        if (NimbusConfig.get().enabled && NimbusConfig.get().sectionLightCache) {
            SectionLight.begin(section, region);
        }
    }

    /**
     * @author qateralong
     * @reason called for every block while meshing; packed builders when our mesher is on, no callback allocation
     */
    @org.spongepowered.asm.mixin.Overwrite
    private BufferBuilder getOrBeginLayer(Map<RenderType, BufferBuilder> builders, SectionBufferBuilderPack pack, RenderType layer) {
        BufferBuilder builder = builders.get(layer);
        if (builder == null) {
            builder = Terrain.meshing() ? new PackedBuilder(pack.buffer(layer), layer == RenderType.translucent())
                    : new BufferBuilder(pack.buffer(layer), com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK);
            builders.put(layer, builder);
        }
        return builder;
    }

    @WrapOperation(method = "compile", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/MeshData;sortQuads(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;Lcom/mojang/blaze3d/vertex/VertexSorting;)Lcom/mojang/blaze3d/vertex/MeshData$SortState;"))
    private MeshData.SortState nimbus$sort(MeshData mesh, ByteBufferBuilder buffer, VertexSorting sorting, Operation<MeshData.SortState> original) {
        if (((PackedMesh) mesh).nimbus$packed() != null) {
            return PackedBuilder.sort(mesh, sorting);
        }
        return original.call(mesh, buffer, sorting);
    }
}
