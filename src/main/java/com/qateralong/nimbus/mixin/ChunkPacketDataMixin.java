package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.ChunkPrep;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class ChunkPacketDataMixin implements ChunkPrep.Packet {
    @Unique
    private volatile LevelChunkSection[] nimbus$sections;

    @Override
    public LevelChunkSection[] nimbus$sections() {
        return this.nimbus$sections;
    }

    @Override
    public void nimbus$setSections(LevelChunkSection[] sections) {
        this.nimbus$sections = sections;
    }
}
