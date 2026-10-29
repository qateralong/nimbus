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
public abstract class TextureAtlasSpriteMixin implements com.qateralong.nimbus.render.AnimatedIndex {
    @Shadow
    @Final
    private SpriteContents contents;

    @Shadow
    @Final
    private net.minecraft.resources.ResourceLocation atlasLocation;

    @org.spongepowered.asm.mixin.Unique
    private int nimbus$animIndex = -1;

    @Override
    public int nimbus$animIndex() {
        return this.nimbus$animIndex;
    }

    @Inject(method = "createTicker", at = @At("RETURN"), cancellable = true)
    private void nimbus$ticker(CallbackInfoReturnable<TextureAtlasSprite.Ticker> cir) {
        TextureAtlasSprite.Ticker ticker = cir.getReturnValue();
        if (ticker != null) {
            if (net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS.equals(this.atlasLocation)) {
                this.nimbus$animIndex = com.qateralong.nimbus.render.AnimatedSprites.register((TextureAtlasSprite) (Object) this);
            }
            cir.setReturnValue(Animations.wrap(this.contents.name(), ticker, this.nimbus$animIndex));
        }
    }
}
