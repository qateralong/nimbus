package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.qateralong.nimbus.render.SectionLight;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$Cache")
public abstract class LightCacheMixin {
    @Unique
    private static final int VOLUME = SectionLight.SIZE * SectionLight.SIZE * SectionLight.SIZE;

    @Shadow
    private boolean enabled;

    @Unique
    private SectionLight nimbus$section;
    @Unique
    private int[] nimbus$light;
    @Unique
    private int[] nimbus$lightStamp;
    @Unique
    private float[] nimbus$shade;
    @Unique
    private int[] nimbus$shadeStamp;
    @Unique
    private int nimbus$stamp;

    @Inject(method = "enable", at = @At("HEAD"))
    private void nimbus$begin(CallbackInfo ci) {
        this.nimbus$section = SectionLight.take();
        if (this.nimbus$section == null) {
            return;
        }
        if (this.nimbus$light == null) {
            this.nimbus$light = new int[VOLUME];
            this.nimbus$lightStamp = new int[VOLUME];
            this.nimbus$shade = new float[VOLUME];
            this.nimbus$shadeStamp = new int[VOLUME];
        }
        if (++this.nimbus$stamp == 0) {
            Arrays.fill(this.nimbus$lightStamp, 0);
            Arrays.fill(this.nimbus$shadeStamp, 0);
            this.nimbus$stamp = 1;
        }
    }

    @Inject(method = "disable", at = @At("HEAD"))
    private void nimbus$end(CallbackInfo ci) {
        this.nimbus$section = null;
    }

    @Unique
    private int nimbus$index(BlockAndTintGetter level, BlockPos pos) {
        SectionLight section = this.nimbus$section;
        if (!this.enabled || section == null || level != section.level) {
            return -1;
        }
        int x = pos.getX() - section.x;
        int y = pos.getY() - section.y;
        int z = pos.getZ() - section.z;
        int size = SectionLight.SIZE;
        if (x < 0 || y < 0 || z < 0 || x >= size || y >= size || z >= size) {
            return -1;
        }
        return (y * size + z) * size + x;
    }

    @WrapMethod(method = "getLightColor")
    private int nimbus$light(BlockState state, BlockAndTintGetter level, BlockPos pos, Operation<Integer> original) {
        int index = this.nimbus$index(level, pos);
        if (index < 0) {
            return original.call(state, level, pos);
        }
        if (this.nimbus$lightStamp[index] == this.nimbus$stamp) {
            return this.nimbus$light[index];
        }
        int value = original.call(state, level, pos);
        this.nimbus$light[index] = value;
        this.nimbus$lightStamp[index] = this.nimbus$stamp;
        return value;
    }

    @WrapMethod(method = "getShadeBrightness")
    private float nimbus$shade(BlockState state, BlockAndTintGetter level, BlockPos pos, Operation<Float> original) {
        int index = this.nimbus$index(level, pos);
        if (index < 0) {
            return original.call(state, level, pos);
        }
        if (this.nimbus$shadeStamp[index] == this.nimbus$stamp) {
            return this.nimbus$shade[index];
        }
        float value = original.call(state, level, pos);
        this.nimbus$shade[index] = value;
        this.nimbus$shadeStamp[index] = this.nimbus$stamp;
        return value;
    }
}
