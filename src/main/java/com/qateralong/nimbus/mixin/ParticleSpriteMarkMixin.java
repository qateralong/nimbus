package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.AnimatedSprites;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextureSheetParticle.class)
public abstract class ParticleSpriteMarkMixin {
    @Shadow
    protected TextureAtlasSprite sprite;

    @Inject(method = "getU0", at = @At("HEAD"))
    private void nimbus$mark(CallbackInfoReturnable<Float> cir) {
        AnimatedSprites.mark(this.sprite);
    }
}
