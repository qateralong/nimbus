package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {
    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private net.minecraft.resources.ResourceLocation location;

    @Inject(method = "upload", at = @At("HEAD"))
    private void nimbus$reset(net.minecraft.client.renderer.texture.SpriteLoader.Preparations preparations, CallbackInfo ci) {
        if (TextureAtlas.LOCATION_BLOCKS.equals(this.location)) {
            com.qateralong.nimbus.render.AnimatedSprites.reset();
        }
        if (Boolean.getBoolean("nimbus.atlasHash")) {
            long hash = 0L;
            for (var entry : new java.util.TreeMap<>(preparations.regions()).entrySet()) {
                var sprite = entry.getValue();
                hash = hash * 31L + entry.getKey().hashCode();
                hash = hash * 31L + ((long) sprite.getX() << 32 | sprite.getY());
            }
            com.qateralong.nimbus.Nimbus.LOGGER.info("Nimbus atlas {}: {}x{}, {} sprites, layout {}", this.location, preparations.width(), preparations.height(),
                    preparations.regions().size(), Long.toHexString(hash));
        }
    }

    @Inject(method = "upload", at = @At("RETURN"))
    private void nimbus$built(net.minecraft.client.renderer.texture.SpriteLoader.Preparations preparations, CallbackInfo ci) {
        if (TextureAtlas.LOCATION_BLOCKS.equals(this.location)) {
            com.qateralong.nimbus.render.AnimatedSprites.built();
        }
    }

    @Inject(method = "cycleAnimationFrames", at = @At("HEAD"), cancellable = true)
    private void nimbus$animations(CallbackInfo ci) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && !config.animatedTextures) {
            ci.cancel();
        }
    }
}
