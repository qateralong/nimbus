package com.qateralong.nimbus.common.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Every getBlockState checks the build height through the dimension type holder; a level's dimension type never
// changes, so the bounds are read once.
@Mixin(Level.class)
public abstract class LevelHeightMixin implements LevelAccessor {
    @Unique
    private int nimbus$minY;

    @Unique
    private int nimbus$height;

    @Unique
    private volatile boolean nimbus$heightKnown;

    @Shadow
    public abstract DimensionType dimensionType();

    @Unique
    private void nimbus$readHeight() {
        DimensionType type = this.dimensionType();
        this.nimbus$minY = type.minY();
        this.nimbus$height = type.height();
        this.nimbus$heightKnown = true;
    }

    @Override
    public int getMinBuildHeight() {
        if (!this.nimbus$heightKnown) {
            this.nimbus$readHeight();
        }
        return this.nimbus$minY;
    }

    @Override
    public int getHeight() {
        if (!this.nimbus$heightKnown) {
            this.nimbus$readHeight();
        }
        return this.nimbus$height;
    }

    @Override
    public int getMaxBuildHeight() {
        if (!this.nimbus$heightKnown) {
            this.nimbus$readHeight();
        }
        return this.nimbus$minY + this.nimbus$height;
    }

    @Override
    public boolean isOutsideBuildHeight(int y) {
        if (!this.nimbus$heightKnown) {
            this.nimbus$readHeight();
        }
        return y < this.nimbus$minY || y >= this.nimbus$minY + this.nimbus$height;
    }
}
