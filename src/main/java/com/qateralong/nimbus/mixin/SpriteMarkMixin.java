package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qateralong.nimbus.render.AnimatedSprites;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class SpriteMarkMixin {
    @Inject(method = "renderQuadList", at = @At("HEAD"))
    private void nimbus$mark(PoseStack pose, VertexConsumer consumer, List<BakedQuad> quads, ItemStack stack, int light, int overlay, CallbackInfo ci) {
        AnimatedSprites.mark(quads);
    }
}
