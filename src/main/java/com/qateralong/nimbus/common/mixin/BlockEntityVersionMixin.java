package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.ContentVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityVersionMixin implements ContentVersion, com.qateralong.nimbus.common.Watchers.Watched {
    @Unique
    private volatile int nimbus$contentVersion;

    @Override
    public int nimbus$contentVersion() {
        return this.nimbus$contentVersion;
    }

    @Unique
    private Object nimbus$watchers;

    @Override
    public void nimbus$bump() {
        this.nimbus$contentVersion++;
        Object watchers = this.nimbus$watchers;
        if (watchers != null) {
            this.nimbus$watchers = null;
            com.qateralong.nimbus.common.Watchers.wake(watchers);
        }
    }

    @Override
    public void nimbus$watch(com.qateralong.nimbus.common.Watchers.Sleeper sleeper) {
        this.nimbus$watchers = com.qateralong.nimbus.common.Watchers.add(this.nimbus$watchers, sleeper);
    }

    @Override
    public void nimbus$wakeAll() {
        this.nimbus$bump();
    }

    @Inject(method = "setChanged()V", at = @At("HEAD"))
    private void nimbus$changed(CallbackInfo ci) {
        this.nimbus$bump();
    }

    // tick code (furnaces, brewing stands, hoppers) changes its own stacks and calls the static form directly
    @Inject(method = "setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("HEAD"))
    private static void nimbus$changedAt(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (level.getBlockEntity(pos) instanceof ContentVersion version) {
            version.nimbus$bump();
        }
    }
}
