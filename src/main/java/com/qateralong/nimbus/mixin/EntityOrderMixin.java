package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.EntityBatch;
import com.qateralong.nimbus.render.EntityOrder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class EntityOrderMixin {
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;entitiesForRendering()Ljava/lang/Iterable;"))
    private Iterable<Entity> nimbus$group(ClientLevel level, Operation<Iterable<Entity>> original) {
        Iterable<Entity> entities = original.call(level);
        EntityBatch.begin();
        NimbusConfig config = NimbusConfig.get();
        return config.enabled && config.entityGrouping ? EntityOrder.group(entities) : entities;
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", ordinal = 0))
    private void nimbus$endEntities(CallbackInfo ci) {
        EntityBatch.end();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void nimbus$endFrame(CallbackInfo ci) {
        EntityBatch.end();
    }
}
