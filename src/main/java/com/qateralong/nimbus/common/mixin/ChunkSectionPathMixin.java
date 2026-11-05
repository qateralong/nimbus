package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.PathCache;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunkSection.class)
public abstract class ChunkSectionPathMixin implements PathCache.Section {
    @Unique
    private volatile byte nimbus$danger;

    @Unique
    private int nimbus$dangerGeneration;

    @Override
    public byte nimbus$danger() {
        return this.nimbus$danger;
    }

    @Override
    public int nimbus$dangerGeneration() {
        return this.nimbus$dangerGeneration;
    }

    @Override
    public void nimbus$setDanger(byte danger, int generation) {
        this.nimbus$dangerGeneration = generation;
        this.nimbus$danger = danger;
    }

    @Inject(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"))
    private void nimbus$changed(int x, int y, int z, BlockState state, boolean locks, CallbackInfoReturnable<BlockState> cir) {
        this.nimbus$danger = 0;
    }
}
