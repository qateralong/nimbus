package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.pack.EntityTags;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

// Vertices of a block entity rendered under a shader pack carry its block state's block.properties id.
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class PackBlockEntityTagMixin {
    @WrapMethod(method = "render")
    private void nimbus$tagBlockEntity(BlockEntity entity, float partial, PoseStack pose, MultiBufferSource buffers, Operation<Void> original) {
        if (!EntityTags.on()) {
            original.call(entity, partial, pose, buffers);
            return;
        }
        EntityTags.pushBlockEntity(entity);
        try {
            original.call(entity, partial, pose, buffers);
        } finally {
            EntityTags.pop();
        }
    }
}
