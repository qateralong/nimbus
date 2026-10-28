package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Animations;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin {
    @Shadow
    @Final
    private SpriteContents contents;

    @Inject(method = "createTicker", at = @At("RETURN"), cancellable = true)
    private void nimbus$ticker(CallbackInfoReturnable<TextureAtlasSprite.Ticker> cir) {
        TextureAtlasSprite.Ticker ticker = cir.getReturnValue();
        if (ticker != null) {
            cir.setReturnValue(Animations.wrap(this.contents.name(), ticker));
        }
    }
}
