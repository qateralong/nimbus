package com.qateralong.nimbus.common.mixin;

import com.google.common.collect.AbstractIterator;
import com.qateralong.nimbus.common.SectionFlags;
import com.qateralong.nimbus.common.ShapeOverlap;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Block collision sweep: states come straight from the cached chunk section, air is skipped before the shape lookup,
// and the outer shell (where vanilla only wants tall blocks and moving pistons) is skipped in sections that have none.
// Walked x fastest, then y, then z like vanilla's cursor, without its three divisions per block.
@Mixin(BlockCollisions.class)
public abstract class BlockCollisionsMixin<T> extends AbstractIterator<T> {
    @Shadow
    @Final
    private AABB box;
    @Shadow
    @Final
    private CollisionContext context;
    @Shadow
    @Final
    private BlockPos.MutableBlockPos pos;
    @Shadow
    @Final
    private VoxelShape entityShape;
    @Shadow
    @Final
    private CollisionGetter collisionGetter;
    @Shadow
    @Final
    private boolean onlySuffocatingBlocks;
    @Shadow
    @Final
    private BiFunction<BlockPos.MutableBlockPos, VoxelShape, T> resultProvider;

    @Shadow
    @Nullable
    private BlockGetter getChunk(int x, int z) {
        throw new AssertionError();
    }

    @Unique
    private boolean nimbus$started;
    @Unique
    private int nimbus$x0;
    @Unique
    private int nimbus$y0;
    @Unique
    private int nimbus$z0;
    @Unique
    private int nimbus$w;
    @Unique
    private int nimbus$h;
    @Unique
    private int nimbus$d;
    @Unique
    private int nimbus$i;
    @Unique
    private int nimbus$j;
    @Unique
    private int nimbus$k;
    @Unique
    private static final BlockGetter[] NIMBUS_WIDE = new BlockGetter[0];
    @Unique
    private BlockGetter[] nimbus$chunks;
    @Unique
    private int nimbus$fetched;
    @Unique
    private int nimbus$chunkX;
    @Unique
    private int nimbus$chunkZ;
    @Unique
    private int nimbus$chunkWidth;
    @Unique
    private LevelChunk nimbus$chunk;
    @Unique
    private int nimbus$sectionIndex = Integer.MIN_VALUE;
    @Unique
    @Nullable
    private LevelChunkSection nimbus$section;

    // chunks of the swept box fetched once each: the cursor runs x fastest, so a box across a chunk border would
    // otherwise switch chunks on every row
    @Unique
    @Nullable
    private BlockGetter nimbus$chunkAt(int x, int z) {
        BlockGetter[] chunks = this.nimbus$chunks;
        if (chunks == null) {
            int x0 = Mth.floor(this.box.minX - 1.0E-7) - 1 >> 4;
            int x1 = Mth.floor(this.box.maxX + 1.0E-7) + 1 >> 4;
            int z0 = Mth.floor(this.box.minZ - 1.0E-7) - 1 >> 4;
            int z1 = Mth.floor(this.box.maxZ + 1.0E-7) + 1 >> 4;
            int width = x1 - x0 + 1;
            int depth = z1 - z0 + 1;
            chunks = width * depth <= 16 ? new BlockGetter[width * depth] : NIMBUS_WIDE;
            this.nimbus$chunks = chunks;
            this.nimbus$chunkX = x0;
            this.nimbus$chunkZ = z0;
            this.nimbus$chunkWidth = width;
        }
        if (chunks == NIMBUS_WIDE) {
            return this.getChunk(x, z);
        }
        int index = ((z >> 4) - this.nimbus$chunkZ) * this.nimbus$chunkWidth + (x >> 4) - this.nimbus$chunkX;
        if ((this.nimbus$fetched >>> index & 1) == 0) {
            this.nimbus$fetched |= 1 << index;
            chunks[index] = this.collisionGetter.getChunkForCollisions(x >> 4, z >> 4);
        }
        return chunks[index];
    }

    // null: the block is air
    @Unique
    @Nullable
    private LevelChunkSection nimbus$sectionAt(LevelChunk chunk, int y) {
        int index = chunk.getSectionIndex(y);
        if (chunk != this.nimbus$chunk || index != this.nimbus$sectionIndex) {
            this.nimbus$chunk = chunk;
            this.nimbus$sectionIndex = index;
            LevelChunkSection[] sections = chunk.getSections();
            LevelChunkSection section = index >= 0 && index < sections.length ? sections[index] : null;
            this.nimbus$section = section == null || section.hasOnlyAir() ? null : section;
        }
        return this.nimbus$section;
    }

    /**
     * @author qateralong
     * @reason section-cached states and an early air skip in the hottest loop of entity movement
     */
    @Overwrite
    @Override
    protected T computeNext() {
        if (!this.nimbus$started) {
            this.nimbus$started = true;
            this.nimbus$x0 = Mth.floor(this.box.minX - 1.0E-7) - 1;
            this.nimbus$y0 = Mth.floor(this.box.minY - 1.0E-7) - 1;
            this.nimbus$z0 = Mth.floor(this.box.minZ - 1.0E-7) - 1;
            this.nimbus$w = Mth.floor(this.box.maxX + 1.0E-7) + 1 - this.nimbus$x0 + 1;
            this.nimbus$h = Mth.floor(this.box.maxY + 1.0E-7) + 1 - this.nimbus$y0 + 1;
            this.nimbus$d = Mth.floor(this.box.maxZ + 1.0E-7) + 1 - this.nimbus$z0 + 1;
        }
        int w = this.nimbus$w;
        int h = this.nimbus$h;
        int d = this.nimbus$d;
        int w1 = w - 1;
        int h1 = h - 1;
        int d1 = d - 1;
        while (this.nimbus$k < d && w > 0 && h > 0) {
            int i = this.nimbus$i;
            int j = this.nimbus$j;
            int k = this.nimbus$k;
            if (++this.nimbus$i == w) {
                this.nimbus$i = 0;
                if (++this.nimbus$j == h) {
                    this.nimbus$j = 0;
                    this.nimbus$k++;
                }
            }
            int type = (i == 0 || i == w1 ? 1 : 0) + (j == 0 || j == h1 ? 1 : 0) + (k == 0 || k == d1 ? 1 : 0);
            if (type == 3) {
                continue;
            }
            int x = this.nimbus$x0 + i;
            int y = this.nimbus$y0 + j;
            int z = this.nimbus$z0 + k;
            BlockGetter getter = this.nimbus$chunkAt(x, z);
            if (getter == null) {
                continue;
            }
            BlockState state;
            if (getter instanceof LevelChunk chunk && chunk.getClass() == LevelChunk.class && !chunk.getLevel().isDebug()) {
                LevelChunkSection section = this.nimbus$sectionAt(chunk, y);
                if (section == null || type != 0 && (((SectionFlags) section).nimbus$flags() & SectionFlags.LARGE) == 0) {
                    continue;
                }
                state = section.getBlockState(x & 15, y & 15, z & 15);
            } else {
                state = getter.getBlockState(this.pos.set(x, y, z));
            }
            if (state.isAir()) {
                continue;
            }
            this.pos.set(x, y, z);
            if ((!this.onlySuffocatingBlocks || state.isSuffocating(getter, this.pos))
                    && (type != 1 || state.hasLargeCollisionShape())
                    && (type != 2 || state.is(Blocks.MOVING_PISTON))) {
                VoxelShape shape = state.getCollisionShape(this.collisionGetter, this.pos, this.context);
                if (shape == Shapes.block()) {
                    if (this.box.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)) {
                        return this.resultProvider.apply(this.pos, shape.move(x, y, z));
                    }
                } else if (!shape.isEmpty()) {
                    int hit = ShapeOverlap.test(shape, x, y, z, this.entityShape);
                    if (hit == 1) {
                        return this.resultProvider.apply(this.pos, shape.move(x, y, z));
                    }
                    if (hit == ShapeOverlap.UNKNOWN) {
                        VoxelShape moved = shape.move(x, y, z);
                        if (Shapes.joinIsNotEmpty(moved, this.entityShape, BooleanOp.AND)) {
                            return this.resultProvider.apply(this.pos, moved);
                        }
                    }
                }
            }
        }
        return this.endOfData();
    }
}
