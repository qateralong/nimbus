package com.qateralong.nimbus.common.mixin;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Each block read in a chunk finds its section through the level's height chain; the chunk keeps the numbers itself.
@Mixin(ChunkAccess.class)
public abstract class ChunkHeightMixin implements LevelHeightAccessor {
    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Unique
    private int nimbus$minY;

    @Unique
    private int nimbus$height;

    @Unique
    private int nimbus$minSection;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nimbus$height(CallbackInfo ci) {
        this.nimbus$minY = this.levelHeightAccessor.getMinBuildHeight();
        this.nimbus$height = this.levelHeightAccessor.getHeight();
        this.nimbus$minSection = this.nimbus$minY >> 4;
    }

    /**
     * @author qateralong
     * @reason read once in the constructor
     */
    @Overwrite
    public int getMinBuildHeight() {
        return this.nimbus$minY;
    }

    /**
     * @author qateralong
     * @reason read once in the constructor
     */
    @Overwrite
    public int getHeight() {
        return this.nimbus$height;
    }

    @Override
    public int getSectionIndex(int y) {
        return (y >> 4) - this.nimbus$minSection;
    }

    @Override
    public int getMinSection() {
        return this.nimbus$minSection;
    }
}
