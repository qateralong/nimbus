package com.qateralong.nimbus.common;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// The block an entity stands on, as CollisionGetter.findSupportingBlock finds it, remembered per entity: reused while
// the box and position are the same and the scanned region is unchanged with static shapes only (see RegionStamp).
public final class SupportCache {

    public static final boolean VERIFY = Boolean.getBoolean("nimbus.verifySupport");
    private static long checked;
    private static long hits;
    private static long wrong;

    private Level level;
    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;
    private double px;
    private double py;
    private double pz;
    private boolean valid;
    private Optional<BlockPos> result;
    private final RegionStamp stamp = new RegionStamp();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public Optional<BlockPos> find(Level level, Entity entity, AABB box) {
        Vec3 position = entity.position();
        if (this.valid && this.level == level && this.minX == box.minX && this.minY == box.minY && this.minZ == box.minZ && this.maxX == box.maxX
                && this.maxY == box.maxY && this.maxZ == box.maxZ && this.px == position.x && this.py == position.y && this.pz == position.z && this.stamp.fresh(level)) {
            if (VERIFY) {
                hits++;
            }
            return this.result;
        }
        this.valid = false;
        int x0 = Mth.floor(box.minX - 1.0E-7) - 1;
        int y0 = Mth.floor(box.minY - 1.0E-7) - 1;
        int z0 = Mth.floor(box.minZ - 1.0E-7) - 1;
        int x1 = Mth.floor(box.maxX + 1.0E-7) + 1;
        int y1 = Mth.floor(box.maxY + 1.0E-7) + 1;
        int z1 = Mth.floor(box.maxZ + 1.0E-7) + 1;
        boolean cacheable = !level.isDebug();
        CollisionContext context = CollisionContext.of(entity);
        VoxelShape entityShape = Shapes.create(box);
        BlockPos.MutableBlockPos pos = this.pos;
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        for (int z = z0; z <= z1; z++) {
            int tz = z == z0 || z == z1 ? 1 : 0;
            for (int x = x0; x <= x1; x++) {
                int txz = tz + (x == x0 || x == x1 ? 1 : 0);
                BlockGetter getter = level.getChunkForCollisions(x >> 4, z >> 4);
                if (getter == null) {
                    cacheable = false;
                    continue;
                }
                if (getter.getClass() != LevelChunk.class) {
                    cacheable = false;
                }
                for (int y = y0; y <= y1; y++) {
                    int type = txz + (y == y0 || y == y1 ? 1 : 0);
                    if (type == 3) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = getter.getBlockState(pos);
                    if (state.isAir() || type == 1 && !state.hasLargeCollisionShape() || type == 2 && !state.is(Blocks.MOVING_PISTON)) {
                        continue;
                    }
                    if (cacheable && !RegionStamp.staticShape(state)) {
                        cacheable = false;
                    }
                    VoxelShape shape = state.getCollisionShape(level, pos, context);
                    boolean hit;
                    if (shape == Shapes.block()) {
                        hit = box.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0);
                    } else if (shape.isEmpty()) {
                        hit = false;
                    } else {
                        int test = ShapeOverlap.test(shape, x, y, z, entityShape);
                        hit = test == 1 || test == ShapeOverlap.UNKNOWN && Shapes.joinIsNotEmpty(shape.move(x, y, z), entityShape, BooleanOp.AND);
                    }
                    if (hit) {
                        double e = pos.distToCenterSqr(position);
                        if (e < distance || e == distance && (best == null || best.compareTo(pos) < 0)) {
                            best = pos.immutable();
                            distance = e;
                        }
                    }
                }
            }
        }
        Optional<BlockPos> result = Optional.ofNullable(best);
        if (cacheable && this.stamp.remember(level, x0, y0, z0, x1, y1, z1)) {
            this.level = level;
            this.minX = box.minX;
            this.minY = box.minY;
            this.minZ = box.minZ;
            this.maxX = box.maxX;
            this.maxY = box.maxY;
            this.maxZ = box.maxZ;
            this.px = position.x;
            this.py = position.y;
            this.pz = position.z;
            this.result = result;
            this.valid = true;
        }
        return result;
    }


    public static void check(Optional<BlockPos> expected, Optional<BlockPos> actual, Entity entity) {
        checked++;
        if (!expected.equals(actual)) {
            wrong++;
            com.qateralong.nimbus.common.NimbusCommon.LOGGER.warn("Nimbus support cache: {} expected {} got {}", entity, expected, actual);
        }
        if ((checked & 0x1FFFF) == 0) {
            com.qateralong.nimbus.common.NimbusCommon.LOGGER.info("Nimbus support cache: {} checked, {} reused, {} wrong", checked, hits, wrong);
        }
    }

    private static long fallChecked;
    private static long fallWrong;

    public static void checkFall(Vec3 expected, Vec3 actual, Entity entity) {
        fallChecked++;
        if (!expected.equals(actual)) {
            fallWrong++;
            com.qateralong.nimbus.common.NimbusCommon.LOGGER.warn("Nimbus fall cache: {} expected {} got {}", entity, expected, actual);
        }
        if ((fallChecked & 0x1FFFF) == 0) {
            com.qateralong.nimbus.common.NimbusCommon.LOGGER.info("Nimbus fall cache: {} reused and checked, {} wrong", fallChecked, fallWrong);
        }
    }
}
