package com.qateralong.nimbus.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// setBlockState updates four heightmaps one after another, each scanning the column down on its own when the top
// block goes away. All four are updated in the first call with one shared scan; the other three calls are skipped.
@Mixin(LevelChunk.class)
public abstract class ChunkHeightmapsMixin {
    @Unique
    private static final Heightmap.Types[] NIMBUS_TYPES = {Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE};

    @Unique
    private boolean nimbus$combined;

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/Heightmap;update(IIILnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 0))
    private boolean nimbus$all(Heightmap first, int x, int y, int z, BlockState state, Operation<Boolean> original) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        Heightmap[] maps = new Heightmap[4];
        for (int n = 0; n < 4; n++) {
            maps[n] = chunk.getOrCreateHeightmapUnprimed(NIMBUS_TYPES[n]);
        }
        if (maps[0] != first) {
            return original.call(first, x, y, z, state);
        }
        this.nimbus$combined = true;
        int pending = 0;
        for (int n = 0; n < 4; n++) {
            Heightmap map = maps[n];
            int top = map.getFirstAvailable(x, z);
            if (y <= top - 2) {
                continue;
            }
            if (((HeightmapAccessor) map).nimbus$opaque().test(state)) {
                if (y >= top) {
                    ((HeightmapAccessor) map).nimbus$setHeight(x, z, y + 1);
                }
            } else if (top - 1 == y) {
                pending |= 1 << n;
            }
        }
        if (pending != 0) {
            int min = chunk.getMinBuildHeight();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int j = y - 1; j >= min && pending != 0; j--) {
                pos.set(x, j, z);
                BlockState below = chunk.getBlockState(pos);
                for (int n = 0; n < 4; n++) {
                    if ((pending >> n & 1) != 0 && ((HeightmapAccessor) maps[n]).nimbus$opaque().test(below)) {
                        ((HeightmapAccessor) maps[n]).nimbus$setHeight(x, z, j + 1);
                        pending &= ~(1 << n);
                    }
                }
            }
            for (int n = 0; n < 4; n++) {
                if ((pending >> n & 1) != 0) {
                    ((HeightmapAccessor) maps[n]).nimbus$setHeight(x, z, min);
                }
            }
        }
        return false;
    }

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/Heightmap;update(IIILnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 1))
    private boolean nimbus$second(Heightmap map, int x, int y, int z, BlockState state, Operation<Boolean> original) {
        return this.nimbus$combined ? false : original.call(map, x, y, z, state);
    }

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/Heightmap;update(IIILnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 2))
    private boolean nimbus$third(Heightmap map, int x, int y, int z, BlockState state, Operation<Boolean> original) {
        return this.nimbus$combined ? false : original.call(map, x, y, z, state);
    }

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/Heightmap;update(IIILnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 3))
    private boolean nimbus$fourth(Heightmap map, int x, int y, int z, BlockState state, Operation<Boolean> original) {
        if (this.nimbus$combined) {
            this.nimbus$combined = false;
            return false;
        }
        return original.call(map, x, y, z, state);
    }
}
