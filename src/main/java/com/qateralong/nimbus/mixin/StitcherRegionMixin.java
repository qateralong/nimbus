package com.qateralong.nimbus.mixin;

import java.util.List;
import net.minecraft.client.renderer.texture.Stitcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.qateralong.nimbus.render.StitcherBounds;

// Vanilla places every sprite by walking the whole region tree, full branches included. Each region keeps the largest
// free leaf width and height below it, and a branch where the sprite cannot fit is skipped: same first fit, same atlas.
@Mixin(Stitcher.Region.class)
public abstract class StitcherRegionMixin implements StitcherBounds {
    @Shadow
    private List<Stitcher.Region<?>> subSlots;

    @Unique
    private static final boolean SLOW = Boolean.getBoolean("nimbus.slowStitch");

    @Unique
    private int nimbus$freeWidth;

    @Unique
    private int nimbus$freeHeight;

    @Override
    public int nimbus$freeWidth() {
        return this.nimbus$freeWidth;
    }

    @Override
    public int nimbus$freeHeight() {
        return this.nimbus$freeHeight;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nimbus$init(int x, int y, int width, int height, CallbackInfo ci) {
        this.nimbus$freeWidth = width;
        this.nimbus$freeHeight = height;
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true, require = 0)
    private void nimbus$prune(@Coerce Object holder, CallbackInfoReturnable<Boolean> cir) {
        if (SLOW) {
            return;
        }
        StitcherHolderAccessor sprite = (StitcherHolderAccessor) holder;
        if (sprite.nimbus$width() > this.nimbus$freeWidth || sprite.nimbus$height() > this.nimbus$freeHeight) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "add", at = @At("RETURN"), require = 0)
    private void nimbus$refresh(@Coerce Object holder, CallbackInfoReturnable<Boolean> cir) {
        if (this.subSlots == null) {
            if (cir.getReturnValueZ()) {
                this.nimbus$freeWidth = 0;
                this.nimbus$freeHeight = 0;
            }
        } else {
            int w = 0;
            int h = 0;
            for (Stitcher.Region<?> region : this.subSlots) {
                StitcherBounds bounds = (StitcherBounds) region;
                w = Math.max(w, bounds.nimbus$freeWidth());
                h = Math.max(h, bounds.nimbus$freeHeight());
            }
            this.nimbus$freeWidth = w;
            this.nimbus$freeHeight = h;
        }
    }
}
