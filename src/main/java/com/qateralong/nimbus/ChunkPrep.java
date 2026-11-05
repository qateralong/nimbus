package com.qateralong.nimbus;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunkSection;

// Chunk sections are decoded on the network thread; the render thread only swaps the finished sections in.
public final class ChunkPrep {
    private static LevelChunkSection[] current;

    private ChunkPrep() {
    }

    public interface Packet {
        LevelChunkSection[] nimbus$sections();

        void nimbus$setSections(LevelChunkSection[] sections);
    }

    public static void parse(ClientboundLevelChunkPacketData data, ClientLevel level) {
        NimbusConfig config = NimbusConfig.get();
        if (level == null || !config.enabled || !config.chunkDecode) {
            return;
        }
        try {
            Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            FriendlyByteBuf buffer = data.getReadBuffer();
            LevelChunkSection[] sections = new LevelChunkSection[level.getSectionsCount()];
            for (int i = 0; i < sections.length; i++) {
                sections[i] = new LevelChunkSection(biomes);
                sections[i].read(buffer);
            }
            ((Packet) data).nimbus$setSections(sections);
        } catch (RuntimeException e) {
            // the level changed under us; the render thread decodes it the usual way
        }
    }

    public static void begin(ClientboundLevelChunkPacketData data) {
        current = ((Packet) data).nimbus$sections();
    }

    public static void end() {
        current = null;
    }

    public static boolean apply(LevelChunkSection[] target) {
        LevelChunkSection[] parsed = current;
        if (parsed == null || parsed.length != target.length) {
            return false;
        }
        // whole sections are swapped: a container's palette is bound to the container that read it
        System.arraycopy(parsed, 0, target, 0, target.length);
        return true;
    }
}
