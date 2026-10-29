package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.render.EntityBatch;
import java.util.SequencedMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceMixin {
    @Shadow
    @Final
    protected SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void nimbus$batch(RenderType type, CallbackInfoReturnable<VertexConsumer> cir) {
        if (EntityBatch.active((MultiBufferSource.BufferSource) (Object) this) && type.canConsolidateConsecutiveGeometry() && !this.fixedBuffers.containsKey(type)) {
            BufferBuilder builder = EntityBatch.buffer(type);
            if (builder != null) {
                cir.setReturnValue(builder);
            }
        }
    }

    @Inject(method = {"endBatch()V", "endBatch(Lnet/minecraft/client/renderer/RenderType;)V", "endLastBatch"}, at = @At("HEAD"))
    private void nimbus$flush(CallbackInfo ci) {
        if (EntityBatch.active((MultiBufferSource.BufferSource) (Object) this)) {
            EntityBatch.flush();
        }
    }
}
