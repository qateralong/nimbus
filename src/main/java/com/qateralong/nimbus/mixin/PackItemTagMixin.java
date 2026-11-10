package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.pack.EntityTags;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

// Vertices of an item rendered in the world under a shader pack carry its item.properties id (currentRenderedItemId).
@Mixin(ItemRenderer.class)
public abstract class PackItemTagMixin {
    @WrapMethod(method = "render")
    private void nimbus$tagItem(ItemStack stack, ItemDisplayContext context, boolean left, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                BakedModel model, Operation<Void> original) {
        if (!EntityTags.on()) {
            original.call(stack, context, left, pose, buffers, light, overlay, model);
            return;
        }
        EntityTags.pushItem(stack);
        try {
            original.call(stack, context, left, pose, buffers, light, overlay, model);
        } finally {
            EntityTags.pop();
        }
    }
}
