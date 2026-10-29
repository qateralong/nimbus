package com.qateralong.nimbus.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class SodiumGraphMixin {
    @Shadow
    private boolean needsGraphUpdate;

    @Inject(method = "update", at = @At("HEAD"), require = 0)
    private void nimbus$update(CallbackInfo ci) {
        if (this.needsGraphUpdate) {
            com.qateralong.nimbus.render.SodiumGraph.version++;
        }
    }

    @Inject(method = {"markGraphDirty", "onSectionAdded", "onSectionRemoved", "onChunkAdded", "onChunkRemoved"}, at = @At("HEAD"), require = 0)
    private void nimbus$dirty(CallbackInfo ci) {
        com.qateralong.nimbus.render.SodiumGraph.version++;
    }
}
