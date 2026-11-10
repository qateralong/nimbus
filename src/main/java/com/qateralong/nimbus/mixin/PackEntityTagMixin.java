package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.pack.EntityTags;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

// Vertices of an entity rendered under a shader pack carry its entity.properties id.
@Mixin(EntityRenderDispatcher.class)
public abstract class PackEntityTagMixin {
    @WrapMethod(method = "render")
    private void nimbus$tagEntity(Entity entity, double x, double y, double z, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
                                  Operation<Void> original) {
        if (!EntityTags.on()) {
            original.call(entity, x, y, z, yaw, partial, pose, buffers, light);
            return;
        }
        EntityTags.pushEntity(entity);
        try {
            original.call(entity, x, y, z, yaw, partial, pose, buffers, light);
        } finally {
            EntityTags.pop();
        }
    }
}
