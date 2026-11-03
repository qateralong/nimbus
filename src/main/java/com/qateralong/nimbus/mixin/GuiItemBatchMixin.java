package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla flushes after every item icon. Icons now stay in the buffers and are drawn together; the flat-item lighting
// they need is applied around whichever flush draws them, and a change of lighting between icons flushes first.
@Mixin(GuiGraphics.class)
public abstract class GuiItemBatchMixin {
    @Unique
    private boolean nimbus$pending;

    @Unique
    private boolean nimbus$pendingFlat;

    @Unique
    private boolean nimbus$nextFlat;

    @Unique
    private boolean nimbus$lit;

    @Unique
    private boolean nimbus$icon;

    @Shadow
    public abstract void flush();

    @Unique
    private static boolean nimbus$enabled() {
        NimbusConfig config = NimbusConfig.get();
        return config.enabled && config.guiItemBatching;
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Lighting;setupForFlatItems()V"))
    private void nimbus$flat(Operation<Void> original) {
        if (nimbus$enabled()) {
            this.nimbus$nextFlat = true;
        } else {
            original.call();
        }
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V"))
    private void nimbus$render(ItemRenderer renderer, ItemStack stack, ItemDisplayContext context, boolean left, PoseStack pose, MultiBufferSource buffers,
                               int light, int overlay, BakedModel model, Operation<Void> original) {
        if (com.qateralong.nimbus.render.ItemIcons.draw(stack, model, pose)) {
            this.nimbus$nextFlat = false;
            this.nimbus$icon = true;
            return;
        }
        if (nimbus$enabled()) {
            boolean flat = this.nimbus$nextFlat;
            this.nimbus$nextFlat = false;
            if (this.nimbus$pending && this.nimbus$pendingFlat != flat) {
                this.flush();
            }
            this.nimbus$pending = true;
            this.nimbus$pendingFlat = flat;
            com.qateralong.nimbus.render.GuiBatch.itemQueued(flat);
        }
        original.call(renderer, stack, context, left, pose, buffers, light, overlay, model);
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void nimbus$skipFlush(GuiGraphics graphics, Operation<Void> original) {
        if (this.nimbus$icon) {
            this.nimbus$icon = false;
            return;
        }
        if (!nimbus$enabled() || !this.nimbus$pending) {
            original.call(graphics);
        }
    }

    @WrapOperation(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Lighting;setupFor3DItems()V"))
    private void nimbus$restore(Operation<Void> original) {
        if (!nimbus$enabled() || !this.nimbus$pending) {
            original.call();
        }
    }

    @Inject(method = "flush", at = @At("HEAD"))
    private void nimbus$light(CallbackInfo ci) {
        com.qateralong.nimbus.render.ItemIcons.drawPending();
        this.nimbus$lit = this.nimbus$pending && this.nimbus$pendingFlat;
        this.nimbus$pending = false;
        if (this.nimbus$lit) {
            Lighting.setupForFlatItems();
        }
    }

    @Inject(method = "flush", at = @At("RETURN"))
    private void nimbus$unlight(CallbackInfo ci) {
        com.qateralong.nimbus.render.GuiBatch.itemsFlushed();
        if (this.nimbus$lit) {
            this.nimbus$lit = false;
            Lighting.setupFor3DItems();
        }
    }
}
