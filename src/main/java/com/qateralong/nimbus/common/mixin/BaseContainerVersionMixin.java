package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.ContentVersion;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// the content changes that don't call setChanged
@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerVersionMixin {
    @Inject(method = "removeItemNoUpdate", at = @At("HEAD"))
    private void nimbus$take(int slot, CallbackInfoReturnable<?> cir) {
        ((ContentVersion) this).nimbus$bump();
    }

    @Inject(method = "clearContent", at = @At("HEAD"))
    private void nimbus$clear(CallbackInfo ci) {
        ((ContentVersion) this).nimbus$bump();
    }
}
