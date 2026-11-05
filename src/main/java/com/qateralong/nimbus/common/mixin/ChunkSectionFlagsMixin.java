package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.SectionFlags;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunkSection.class)
public abstract class ChunkSectionFlagsMixin implements SectionFlags, com.qateralong.nimbus.common.Watchers.Watched {
    @Unique
    private volatile int nimbus$flags;
    @Unique
    private volatile int nimbus$version;

    @Unique
    private net.minecraft.world.level.block.Block nimbus$block;
    @Unique
    private int nimbus$blockVersion;
    @Unique
    private boolean nimbus$blockMay;

    @Unique
    private Object nimbus$watchers;

    @Override
    public int nimbus$version() {
        return this.nimbus$version;
    }

    @Override
    public void nimbus$watch(com.qateralong.nimbus.common.Watchers.Sleeper sleeper) {
        this.nimbus$watchers = com.qateralong.nimbus.common.Watchers.add(this.nimbus$watchers, sleeper);
    }

    @Override
    public void nimbus$wakeAll() {
        Object watchers = this.nimbus$watchers;
        if (watchers != null) {
            this.nimbus$watchers = null;
            com.qateralong.nimbus.common.Watchers.wake(watchers);
        }
    }

    @Override
    public boolean nimbus$mayHave(net.minecraft.world.level.block.Block block) {
        int version = this.nimbus$version;
        if (block != this.nimbus$block || version != this.nimbus$blockVersion) {
            this.nimbus$blockMay = this.maybeHas(state -> state.is(block));
            this.nimbus$blockVersion = version;
            this.nimbus$block = block;
        }
        return this.nimbus$blockMay;
    }

    @Shadow
    public abstract boolean maybeHas(java.util.function.Predicate<BlockState> predicate);

    @Override
    public int nimbus$flags() {
        int flags = this.nimbus$flags;
        if (flags == 0) {
            flags = 0x100;
            if (this.maybeHas(ChunkSectionFlagsMixin::nimbus$large)) {
                flags |= LARGE;
            }
            if (this.maybeHas(ChunkSectionFlagsMixin::nimbus$fluid)) {
                flags |= FLUID;
            }
            this.nimbus$flags = flags;
        }
        return flags;
    }

    @Unique
    private static boolean nimbus$large(BlockState state) {
        return state.hasLargeCollisionShape() || state.is(Blocks.MOVING_PISTON);
    }

    @Unique
    private static boolean nimbus$fluid(BlockState state) {
        return !state.getFluidState().isEmpty();
    }

    @Inject(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    private void nimbus$changed(int x, int y, int z, BlockState state, boolean locks, CallbackInfoReturnable<BlockState> cir) {
        this.nimbus$flags = 0;
        this.nimbus$version++;
        this.nimbus$wakeAll();
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void nimbus$read(FriendlyByteBuf buffer, CallbackInfo ci) {
        this.nimbus$flags = 0;
        this.nimbus$version++;
        this.nimbus$wakeAll();
    }
}
