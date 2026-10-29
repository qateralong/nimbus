package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.render.MapAtlas;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.MapRenderer$MapInstance")
public abstract class MapAtlasMixin {
    @Shadow
    @Final
    private DynamicTexture texture;

    @Unique
    private int nimbus$slot = -1;

    @Unique
    private boolean nimbus$uploaded;

    @Inject(method = "updateTexture", at = @At("TAIL"))
    private void nimbus$upload(CallbackInfo ci) {
        if (this.nimbus$slot < 0) {
            this.nimbus$slot = MapAtlas.allocate();
        }
        if (this.nimbus$slot >= 0 && this.texture.getPixels() != null) {
            MapAtlas.upload(this.nimbus$slot, this.texture.getPixels());
            this.nimbus$uploaded = true;
        }
    }

    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", ordinal = 0))
    private VertexConsumer nimbus$atlas(MultiBufferSource buffers, RenderType type, Operation<VertexConsumer> original) {
        if (this.nimbus$uploaded && MapAtlas.enabled()) {
            return MapAtlas.remap(original.call(buffers, MapAtlas.renderType()), this.nimbus$slot);
        }
        return original.call(buffers, type);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void nimbus$release(CallbackInfo ci) {
        MapAtlas.release(this.nimbus$slot);
        this.nimbus$slot = -1;
        this.nimbus$uploaded = false;
    }
}
