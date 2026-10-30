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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Shadow
    private RenderChunk getChunk(int x, int z) {
        throw new AssertionError();
    }

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

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
        BlockState[] states = new BlockState[NIMBUS_SIZE * NIMBUS_SIZE * NIMBUS_SIZE];
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
            int[] ids = new int[4096];
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
        this.nimbus$resolvers = new net.minecraft.world.level.ColorResolver[3];
        this.nimbus$tints = new int[3][];
    }

    @Inject(method = "getBlockTint", at = @At("HEAD"), cancellable = true)
    private void nimbus$tint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver, CallbackInfoReturnable<Integer> cir) {
        net.minecraft.world.level.ColorResolver[] resolvers = this.nimbus$resolvers;
        if (resolvers == null) {
            return;
        }
        int x = pos.getX() - this.nimbus$x;
        int y = pos.getY() - this.nimbus$y;
        int z = pos.getZ() - this.nimbus$z;
        if ((x | y | z) < 0 || x >= NIMBUS_SIZE || y >= NIMBUS_SIZE || z >= NIMBUS_SIZE) {
            return;
        }
        int slot = 0;
        while (slot < resolvers.length && resolvers[slot] != null && resolvers[slot] != resolver) {
            slot++;
        }
        if (slot == resolvers.length) {
            return;
        }
        int[] tints = this.nimbus$tints[slot];
        if (resolvers[slot] == null) {
            resolvers[slot] = resolver;
            tints = new int[NIMBUS_SIZE * NIMBUS_SIZE * NIMBUS_SIZE];
            this.nimbus$tints[slot] = tints;
        }
        int index = (y * NIMBUS_SIZE + z) * NIMBUS_SIZE + x;
        int color = tints[index];
        if (color == 0) {
            color = this.level.getBlockTint(pos, resolver) | 0xFF000000;
            tints[index] = color;
        }
        cir.setReturnValue(color & 0xFFFFFF);
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void nimbus$window(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState[] states = this.nimbus$states;
        if (states != null) {
            int x = pos.getX() - this.nimbus$x;
            int y = pos.getY() - this.nimbus$y;
            int z = pos.getZ() - this.nimbus$z;
            if ((x | y | z) >= 0 && x < NIMBUS_SIZE && y < NIMBUS_SIZE && z < NIMBUS_SIZE) {
                cir.setReturnValue(states[(y * NIMBUS_SIZE + z) * NIMBUS_SIZE + x]);
            }
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void nimbus$fluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        BlockState[] states = this.nimbus$states;
        if (states != null) {
            int x = pos.getX() - this.nimbus$x;
            int y = pos.getY() - this.nimbus$y;
            int z = pos.getZ() - this.nimbus$z;
            if ((x | y | z) >= 0 && x < NIMBUS_SIZE && y < NIMBUS_SIZE && z < NIMBUS_SIZE) {
                cir.setReturnValue(states[(y * NIMBUS_SIZE + z) * NIMBUS_SIZE + x].getFluidState());
            }
        }
    }
}
