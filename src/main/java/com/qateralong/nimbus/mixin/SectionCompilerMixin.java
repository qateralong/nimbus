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

    @Inject(method = "getOrBeginLayer", at = @At("HEAD"), cancellable = true)
    private void nimbus$packed(Map<RenderType, BufferBuilder> builders, SectionBufferBuilderPack pack, RenderType layer, CallbackInfoReturnable<BufferBuilder> cir) {
        BufferBuilder existing = builders.get(layer);
        if (existing != null) {
            cir.setReturnValue(existing);
            return;
        }
        if (Terrain.meshing()) {
            BufferBuilder builder = new PackedBuilder(pack.buffer(layer), layer == RenderType.translucent());
            builders.put(layer, builder);
            cir.setReturnValue(builder);
        }
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
