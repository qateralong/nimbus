package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.ContentVersion;
import com.qateralong.nimbus.common.IdleTicker;
import com.qateralong.nimbus.common.RecipeEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A brewing stand that starts and ends a tick not brewing has evaluated every way to start and refuel; it repeats
// that tick exactly until its contents or the recipes change. (A tick that just finished a brew skipped the start
// check, so it does not count.)
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingIdleMixin implements IdleTicker {
    @Shadow
    int brewTime;

    @Unique
    private static boolean nimbus$startedIdle;
    @Unique
    private boolean nimbus$asleep;
    @Unique
    private int nimbus$version;
    @Unique
    private int nimbus$epoch;

    @Override
    public boolean nimbus$idleTick() {
        BlockEntity self = (BlockEntity) (Object) this;
        return this.nimbus$asleep && this.brewTime == 0 && ContentVersion.of(self) == this.nimbus$version && RecipeEpoch.value == this.nimbus$epoch
                && !self.isRemoved();
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void nimbus$start(Level level, BlockPos pos, BlockState state, BrewingStandBlockEntity stand, CallbackInfo ci) {
        nimbus$startedIdle = ((BrewingIdleMixin) (Object) stand).brewTime == 0;
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void nimbus$learn(Level level, BlockPos pos, BlockState state, BrewingStandBlockEntity stand, CallbackInfo ci) {
        BrewingIdleMixin self = (BrewingIdleMixin) (Object) stand;
        self.nimbus$asleep = nimbus$startedIdle && self.brewTime == 0 && ContentVersion.TRUSTED.contains(stand.getClass());
        self.nimbus$version = ContentVersion.of(stand);
        self.nimbus$epoch = RecipeEpoch.value;
    }
}
