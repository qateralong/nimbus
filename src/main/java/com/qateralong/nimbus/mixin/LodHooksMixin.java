package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.lod.LodRenderer;
import com.qateralong.nimbus.lod.LodStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientChunkCache.class)
public abstract class LodHooksMixin {
    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void nimbus$lod(CallbackInfoReturnable<LevelChunk> cir) {
        LevelChunk chunk = cir.getReturnValue();
        if (chunk != null && LodRenderer.active()) {
            LodRenderer.sync(Minecraft.getInstance());
            LodStore.capture(chunk);
        }
    }
}
