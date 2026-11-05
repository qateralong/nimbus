package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.ContentVersion;
import com.qateralong.nimbus.common.IdleTicker;
import com.qateralong.nimbus.common.RecipeEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A furnace that ends a tick unlit with no cooking progress repeats that tick exactly until its contents or the
// recipes change: such ticks are skipped. Only for the vanilla furnace classes.
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceIdleMixin implements IdleTicker {
    @Shadow
    int litTime;
    @Shadow
    int cookingProgress;

    @Unique
    private boolean nimbus$asleep;
    @Unique
    private int nimbus$version;
    @Unique
    private int nimbus$epoch;

    @Override
    public boolean nimbus$idleTick() {
        BlockEntity self = (BlockEntity) (Object) this;
        return this.nimbus$asleep && this.litTime <= 0 && this.cookingProgress == 0 && ContentVersion.of(self) == this.nimbus$version
                && RecipeEpoch.value == this.nimbus$epoch && !self.isRemoved();
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void nimbus$learn(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        FurnaceIdleMixin self = (FurnaceIdleMixin) (Object) furnace;
        self.nimbus$asleep = self.litTime <= 0 && self.cookingProgress == 0 && ContentVersion.TRUSTED.contains(furnace.getClass());
        self.nimbus$version = ContentVersion.of(furnace);
        self.nimbus$epoch = RecipeEpoch.value;
    }
}
