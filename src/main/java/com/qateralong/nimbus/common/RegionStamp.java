package com.qateralong.nimbus.common;

import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.MudBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

// The blocks a collision sweep over a box reads: the same chunk and section objects at the same block versions mean the
// same states. Collision shapes of "static" states depend on the state alone: not on the entity, a block entity or the
// position (vanilla caches those shapes itself; overrides of getCollisionShape are trusted only for the vanilla blocks
// listed, which read nothing else).
public final class RegionStamp {
    private static final int MAX = 16;
    private static final String COLLISION_SHAPE = FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary", "net.minecraft.class_4970",
            "method_9549", "(Lnet/minecraft/class_2680;Lnet/minecraft/class_1922;Lnet/minecraft/class_2338;Lnet/minecraft/class_3726;)Lnet/minecraft/class_265;");
    private static final Set<Class<?>> PLAIN = Set.of(BambooStalkBlock.class, BellBlock.class, BigDripleafBlock.class, CactusBlock.class, ComposterBlock.class,
            CrossCollisionBlock.class, FenceGateBlock.class, GrindstoneBlock.class, HoneyBlock.class, LecternBlock.class, MudBlock.class, PitcherCropBlock.class,
            SculkShriekerBlock.class, SnowLayerBlock.class, SoulSandBlock.class, WallBlock.class, WallHangingSignBlock.class);
    private static final ClassValue<Boolean> STATIC_SHAPE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != BlockBehaviour.class; c = c.getSuperclass()) {
                try {
                    c.getDeclaredMethod(COLLISION_SHAPE, BlockState.class, BlockGetter.class, BlockPos.class, CollisionContext.class);
                    return PLAIN.contains(c);
                } catch (NoSuchMethodException ignored) {
                }
            }
            return true;
        }
    };
    public static final String CLIENT_LEVEL = FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", "net.minecraft.class_638");

    private int chunkCount;
    private final LevelChunk[] chunks = new LevelChunk[MAX];
    private final int[] chunkX = new int[MAX];
    private final int[] chunkZ = new int[MAX];
    private int sectionCount;
    private final LevelChunkSection[] sections = new LevelChunkSection[MAX];
    private final int[] versions = new int[MAX];
    private final int[] sectionChunk = new int[MAX];
    private final int[] sectionIndex = new int[MAX];

    public static boolean plainLevel(Level level) {
        Class<?> type = level.getClass();
        return type == net.minecraft.server.level.ServerLevel.class || CLIENT_LEVEL.equals(type.getName());
    }

    public static boolean staticShape(BlockState state) {
        return !state.getBlock().hasDynamicShape() && STATIC_SHAPE.get(state.getBlock().getClass());
    }

    // true when a BlockCollisions sweep over the box only meets loaded plain chunks and static shapes; then the region is stamped
    public boolean stampStatic(Level level, AABB box, BlockPos.MutableBlockPos pos) {
        if (level.isDebug()) {
            return false;
        }
        int x0 = Mth.floor(box.minX - 1.0E-7) - 1;
        int y0 = Mth.floor(box.minY - 1.0E-7) - 1;
        int z0 = Mth.floor(box.minZ - 1.0E-7) - 1;
        int x1 = Mth.floor(box.maxX + 1.0E-7) + 1;
        int y1 = Mth.floor(box.maxY + 1.0E-7) + 1;
        int z1 = Mth.floor(box.maxZ + 1.0E-7) + 1;
        for (int z = z0; z <= z1; z++) {
            int tz = z == z0 || z == z1 ? 1 : 0;
            for (int x = x0; x <= x1; x++) {
                int txz = tz + (x == x0 || x == x1 ? 1 : 0);
                if (!(level.getChunkForCollisions(x >> 4, z >> 4) instanceof LevelChunk chunk) || chunk.getClass() != LevelChunk.class) {
                    return false;
                }
                for (int y = y0; y <= y1; y++) {
                    int type = txz + (y == y0 || y == y1 ? 1 : 0);
                    if (type == 3) {
                        continue;
                    }
                    BlockState state = chunk.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || type == 1 && !state.hasLargeCollisionShape() || type == 2 && !state.is(Blocks.MOVING_PISTON)) {
                        continue;
                    }
                    if (!staticShape(state)) {
                        return false;
                    }
                }
            }
        }
        return this.remember(level, x0, y0, z0, x1, y1, z1);
    }

    public boolean remember(Level level, int x0, int y0, int z0, int x1, int y1, int z1) {
        this.chunkCount = 0;
        this.sectionCount = 0;
        for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
            for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
                if (!(level.getChunkForCollisions(cx, cz) instanceof LevelChunk chunk) || this.chunkCount == MAX) {
                    return false;
                }
                int c = this.chunkCount++;
                this.chunks[c] = chunk;
                this.chunkX[c] = cx;
                this.chunkZ[c] = cz;
                LevelChunkSection[] all = chunk.getSections();
                for (int sy = y0 >> 4; sy <= y1 >> 4; sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= all.length) {
                        continue;
                    }
                    if (this.sectionCount == MAX) {
                        return false;
                    }
                    int s = this.sectionCount++;
                    this.sections[s] = all[index];
                    this.versions[s] = ((SectionFlags) all[index]).nimbus$version();
                    this.sectionChunk[s] = c;
                    this.sectionIndex[s] = index;
                }
            }
        }
        return true;
    }

    public boolean fresh(Level level) {
        for (int c = 0; c < this.chunkCount; c++) {
            if (level.getChunkForCollisions(this.chunkX[c], this.chunkZ[c]) != this.chunks[c]) {
                return false;
            }
        }
        for (int s = 0; s < this.sectionCount; s++) {
            LevelChunkSection section = this.sections[s];
            if (this.chunks[this.sectionChunk[s]].getSections()[this.sectionIndex[s]] != section
                    || ((SectionFlags) section).nimbus$version() != this.versions[s]) {
                return false;
            }
        }
        return true;
    }
}
