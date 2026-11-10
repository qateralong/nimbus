package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.FastQuit;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// a world still saving after a fast quit is finished before it is opened (or its files touched) again
@Mixin(LevelStorageSource.class)
public abstract class FastQuitStorageMixin {
    @Inject(method = {"createAccess", "validateAndCreateAccess"}, at = @At("HEAD"))
    private void nimbus$await(String levelId, CallbackInfoReturnable<LevelStorageSource.LevelStorageAccess> cir) {
        FastQuit.await(levelId);
    }
}
