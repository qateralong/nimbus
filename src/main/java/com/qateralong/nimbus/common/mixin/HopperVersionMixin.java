package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.ContentVersion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// hoppers override these without calling setChanged
@Mixin(HopperBlockEntity.class)
public abstract class HopperVersionMixin {
    @Inject(method = "removeItem", at = @At("HEAD"))
    private void nimbus$remove(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        ((ContentVersion) this).nimbus$bump();
    }

    @Inject(method = "setItem", at = @At("HEAD"))
    private void nimbus$set(int slot, ItemStack stack, CallbackInfo ci) {
        ((ContentVersion) this).nimbus$bump();
    }
}
