package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.ChunkPrep;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientChunkPacketMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"))
    private void nimbus$decode(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) {
            ChunkPrep.parse(packet.getChunkData(), this.level);
        }
    }

    @Inject(method = "updateLevelChunk", at = @At("HEAD"))
    private void nimbus$begin(int x, int z, ClientboundLevelChunkPacketData data, CallbackInfo ci) {
        ChunkPrep.begin(data);
    }

    @Inject(method = "updateLevelChunk", at = @At("RETURN"))
    private void nimbus$end(int x, int z, ClientboundLevelChunkPacketData data, CallbackInfo ci) {
        ChunkPrep.end();
    }
}
