package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Chests;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DecoratedPotBlockEntity.class)
public abstract class DecoratedPotMixin extends BlockEntity {
    @Shadow
    public abstract PotDecorations getDecorations();

    protected DecoratedPotMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Object getRenderData() {
        return this.getDecorations();
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void nimbus$loaded(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        Chests.potChanged((DecoratedPotBlockEntity) (Object) this);
    }

    @Inject(method = "wobble", at = @At("TAIL"))
    private void nimbus$wobble(DecoratedPotBlockEntity.WobbleStyle style, CallbackInfo ci) {
        Chests.potWobble((DecoratedPotBlockEntity) (Object) this, style.duration);
    }
}
