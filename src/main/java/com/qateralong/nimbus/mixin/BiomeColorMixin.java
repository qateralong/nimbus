package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.ColormapStamp;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// The base grass and foliage colours only change with the colormap textures; vanilla boxes them on every lookup.
@Mixin(value = Biome.class, priority = 900)
public abstract class BiomeColorMixin {
    @Shadow
    @Final
    private BiomeSpecialEffects specialEffects;

    @Shadow
    private int getGrassColorFromTexture() {
        throw new AssertionError();
    }

    @Shadow
    private int getFoliageColorFromTexture() {
        throw new AssertionError();
    }

    // stamp in the high half, colour in the low half, so worker threads never see a colour from another stamp
    @Unique
    private long nimbus$grass = -1L;

    @Unique
    private long nimbus$foliage = -1L;

    /**
     * @author qateralong
     * @reason cached base colour instead of a boxed Integer and a lambda per block
     */
    @Overwrite
    public int getGrassColor(double x, double z) {
        int stamp = ColormapStamp.value;
        long cached = this.nimbus$grass;
        int color;
        if ((int) (cached >>> 32) == stamp) {
            color = (int) cached;
        } else {
            var override = this.specialEffects.getGrassColorOverride();
            color = override.isPresent() ? override.get() : this.getGrassColorFromTexture();
            this.nimbus$grass = (long) stamp << 32 | color & 0xFFFFFFFFL;
        }
        return this.specialEffects.getGrassColorModifier().modifyColor(x, z, color);
    }

    /**
     * @author qateralong
     * @reason cached base colour instead of a boxed Integer and a lambda per block
     */
    @Overwrite
    public int getFoliageColor() {
        int stamp = ColormapStamp.value;
        long cached = this.nimbus$foliage;
        if ((int) (cached >>> 32) == stamp) {
            return (int) cached;
        }
        var override = this.specialEffects.getFoliageColorOverride();
        int color = override.isPresent() ? override.get() : this.getFoliageColorFromTexture();
        this.nimbus$foliage = (long) stamp << 32 | color & 0xFFFFFFFFL;
        return color;
    }
}
