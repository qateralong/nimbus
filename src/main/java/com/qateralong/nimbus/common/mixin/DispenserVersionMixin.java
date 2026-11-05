package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.ContentVersion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// dispensers put remainders (filled bottles, buckets) back by growing stacks in place
@Mixin(DispenserBlockEntity.class)
public abstract class DispenserVersionMixin {
    @Inject(method = "insertItem", at = @At("HEAD"))
    private void nimbus$insert(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        ((ContentVersion) this).nimbus$bump();
    }
}
