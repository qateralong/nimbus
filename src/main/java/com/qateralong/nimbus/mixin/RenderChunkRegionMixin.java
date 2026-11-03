package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.BlockWindow;
import com.qateralong.nimbus.render.ChunkSections;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(RenderChunkRegion.class)
public abstract class RenderChunkRegionMixin implements BlockWindow {
    @Unique
    private static final int NIMBUS_SIZE = 18;

    @Unique
    private static final BlockState NIMBUS_AIR = Blocks.AIR.defaultBlockState();

    @Unique
    private BlockState[] nimbus$states;

    @Unique
    private int nimbus$x;

    @Unique
    private int nimbus$y;

    @Unique
    private int nimbus$z;

    @Unique
    private net.minecraft.world.level.ColorResolver[] nimbus$resolvers;

    @Unique
    private int[][] nimbus$tints;

    @Unique
    private com.qateralong.nimbus.render.BiomeBlend nimbus$blend;

    @Shadow
    private RenderChunk getChunk(int x, int z) {
        throw new AssertionError();
    }


    @Shadow
    @org.spongepowered.asm.mixin.Final
    protected net.minecraft.world.level.Level level;

    @Override
    public void nimbus$begin(SectionPos section) {
        this.nimbus$states = null;
        ChunkSections center = (ChunkSections) this.getChunk(section.x(), section.z());
        PalettedContainer<BlockState>[] containers = center.nimbus$containers();
        if (containers == null) {
            return;
        }
        com.qateralong.nimbus.render.WindowPool pool = com.qateralong.nimbus.render.WindowPool.get();
        BlockState[] states = pool.states;
        int index = section.y() - center.nimbus$minSection();
        PalettedContainer<BlockState> container = index >= 0 && index < containers.length ? containers[index] : null;
        if (container == null) {
            for (int y = 1; y <= 16; y++) {
                for (int z = 1; z <= 16; z++) {
                    int row = (y * NIMBUS_SIZE + z) * NIMBUS_SIZE;
                    java.util.Arrays.fill(states, row + 1, row + 17, NIMBUS_AIR);
                }
            }
        } else {
            PalettedContainer.Data<BlockState> data = container.data;
            int[] ids = pool.ids;
            data.storage().unpack(ids);
            net.minecraft.world.level.chunk.Palette<BlockState> palette = data.palette();
            for (int i = 0; i < 4096; i++) {
                BlockState state = palette.valueFor(ids[i]);
                states[(((i >> 8) + 1) * NIMBUS_SIZE + ((i >> 4 & 15) + 1)) * NIMBUS_SIZE + (i & 15) + 1] = state == null ? NIMBUS_AIR : state;
            }
        }
        int minX = section.minBlockX() - 1;
        int minY = section.minBlockY() - 1;
        int minZ = section.minBlockZ() - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < NIMBUS_SIZE; y++) {
            boolean edgeY = y == 0 || y == NIMBUS_SIZE - 1;
            for (int z = 0; z < NIMBUS_SIZE; z++) {
                boolean edgeYZ = edgeY || z == 0 || z == NIMBUS_SIZE - 1;
                int row = (y * NIMBUS_SIZE + z) * NIMBUS_SIZE;
                for (int x = 0; x < NIMBUS_SIZE; x++) {
                    if (edgeYZ || x == 0 || x == NIMBUS_SIZE - 1) {
                        states[row + x] = this.getBlockState(pos.set(minX + x, minY + y, minZ + z));
                    }
                }
            }
        }
        this.nimbus$x = minX;
        this.nimbus$y = minY;
        this.nimbus$z = minZ;
        this.nimbus$states = states;
        this.nimbus$resolvers = pool.resolvers;
        this.nimbus$tints = pool.tints;
        this.nimbus$blend = com.qateralong.nimbus.NimbusConfig.get().fastBiomeBlend
                ? com.qateralong.nimbus.render.BiomeBlend.get(this.level, minX, minY, minZ, NIMBUS_SIZE) : null;
    }

    /**
     * @author qateralong
     * @reason per-region tint cache with blended biome colours, without a callback allocation per call
     */
    @org.spongepowered.asm.mixin.Overwrite
    public int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver) {
        int cached = this.nimbus$tint(pos, resolver);
        return cached >= 0 ? cached : this.level.getBlockTint(pos, resolver);
    }

    @Unique
    private int nimbus$tint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver) {
        net.minecraft.world.level.ColorResolver[] resolvers = this.nimbus$resolvers;
        if (resolvers == null) {
            return -1;
        }
        int x = pos.getX() - this.nimbus$x;
        int y = pos.getY() - this.nimbus$y;
        int z = pos.getZ() - this.nimbus$z;
        if ((x | y | z) < 0 || x >= NIMBUS_SIZE || y >= NIMBUS_SIZE || z >= NIMBUS_SIZE) {
            return -1;
        }
        int slot = 0;
        while (slot < resolvers.length && resolvers[slot] != null && resolvers[slot] != resolver) {
            slot++;
        }
        if (slot == resolvers.length) {
            return -1;
        }
        int[] tints = this.nimbus$tints[slot];
        if (resolvers[slot] == null) {
            resolvers[slot] = resolver;
            java.util.Arrays.fill(tints, 0);
        }
        int index = (y * NIMBUS_SIZE + z) * NIMBUS_SIZE + x;
        int color = tints[index];
        if (color == 0) {
            com.qateralong.nimbus.render.BiomeBlend blend = this.nimbus$blend;
            int blended = blend != null ? blend.color(resolver, x, y, z) : -1;
            color = (blended >= 0 ? blended : this.level.getBlockTint(pos, resolver)) | 0xFF000000;
            tints[index] = color;
        }
        return color & 0xFFFFFF;
    }

    /**
     * @author qateralong
     * @reason allocation-free lookup in the prepared 18x18x18 window on the meshing hot path
     */
    @org.spongepowered.asm.mixin.Overwrite
    public BlockState getBlockState(BlockPos pos) {
        BlockState[] states = this.nimbus$states;
        if (states != null) {
            int x = pos.getX() - this.nimbus$x;
            int y = pos.getY() - this.nimbus$y;
            int z = pos.getZ() - this.nimbus$z;
            if ((x | y | z) >= 0 && x < NIMBUS_SIZE && y < NIMBUS_SIZE && z < NIMBUS_SIZE) {
                return states[(y * NIMBUS_SIZE + z) * NIMBUS_SIZE + x];
            }
        }
        return this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())).getBlockState(pos);
    }

    /**
     * @author qateralong
     * @reason same window lookup as getBlockState, without a callback allocation per call
     */
    @org.spongepowered.asm.mixin.Overwrite
    public FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos).getFluidState();
    }
}
