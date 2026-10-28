package com.qateralong.nimbus;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

public final class Occlusion {
    private static final long INTERVAL = 40_000_000L;
    private static final double NEAR = 3.0;
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final Thread WORKER;
    private static final Object LOCK = new Object();

    private static List<Target> pending = new ArrayList<>();
    private static List<Target> collecting = new ArrayList<>();
    private static volatile IntOpenHashSet hiddenEntities = new IntOpenHashSet();
    private static volatile LongOpenHashSet hiddenBlocks = new LongOpenHashSet();
    private static double camX;
    private static double camY;
    private static double camZ;
    private static long lastSubmit;
    public static int lastTargets;
    public static int lastHidden;
    public static long lastNanos;

    private record Target(int entity, long block, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    static {
        WORKER = new Thread(Occlusion::loop, "Nimbus occlusion");
        WORKER.setDaemon(true);
        WORKER.setPriority(Thread.NORM_PRIORITY - 1);
        WORKER.start();
    }

    private Occlusion() {
    }

    static boolean enabled() {
        return NimbusConfig.get().occlusionCulling;
    }

    public static boolean entityHidden(int id, AABB box) {
        if (!enabled()) {
            return false;
        }
        collecting.add(new Target(id, Long.MIN_VALUE, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        return hiddenEntities.contains(id);
    }

    public static boolean blockHidden(BlockPos pos) {
        if (!enabled()) {
            return false;
        }
        long key = pos.asLong();
        collecting.add(new Target(0, key, pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
        return hiddenBlocks.contains(key);
    }

    static void frame(double x, double y, double z) {
        long now = System.nanoTime();
        if (!enabled()) {
            collecting.clear();
            if (!hiddenEntities.isEmpty() || !hiddenBlocks.isEmpty()) {
                hiddenEntities = new IntOpenHashSet();
                hiddenBlocks = new LongOpenHashSet();
            }
            return;
        }
        if (now - lastSubmit < INTERVAL || BUSY.get() || collecting.isEmpty() && hiddenEntities.isEmpty() && hiddenBlocks.isEmpty()) {
            collecting.clear();
            return;
        }
        lastSubmit = now;
        synchronized (LOCK) {
            pending = collecting;
            camX = x;
            camY = y;
            camZ = z;
            BUSY.set(true);
            LOCK.notify();
        }
        collecting = new ArrayList<>(pending.size() + 16);
    }

    private static void loop() {
        while (true) {
            List<Target> targets;
            double x;
            double y;
            double z;
            synchronized (LOCK) {
                while (!BUSY.get()) {
                    try {
                        LOCK.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                targets = pending;
                x = camX;
                y = camY;
                z = camZ;
            }
            try {
                compute(targets, x, y, z);
            } catch (Throwable t) {
                Nimbus.LOGGER.debug("Occlusion pass failed", t);
            } finally {
                BUSY.set(false);
            }
        }
    }

    private static void compute(List<Target> targets, double x, double y, double z) {
        long start = System.nanoTime();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Grid grid = new Grid(level);
        IntOpenHashSet entities = new IntOpenHashSet();
        LongOpenHashSet blocks = new LongOpenHashSet();
        LongOpenHashSet doneBlocks = new LongOpenHashSet();
        IntOpenHashSet doneEntities = new IntOpenHashSet();
        int hidden = 0;
        for (Target target : targets) {
            if (target.block() != Long.MIN_VALUE ? !doneBlocks.add(target.block()) : !doneEntities.add(target.entity())) {
                continue;
            }
            if (!visible(grid, target, x, y, z)) {
                hidden++;
                if (target.block() != Long.MIN_VALUE) {
                    blocks.add(target.block());
                } else {
                    entities.add(target.entity());
                }
            }
        }
        hiddenEntities = entities;
        hiddenBlocks = blocks;
        lastTargets = doneBlocks.size() + doneEntities.size();
        lastHidden = hidden;
        lastNanos = System.nanoTime() - start;
    }

    private static boolean visible(Grid grid, Target t, double x, double y, double z) {
        if (x >= t.minX() - NEAR && x <= t.maxX() + NEAR && y >= t.minY() - NEAR && y <= t.maxY() + NEAR && z >= t.minZ() - NEAR && z <= t.maxZ() + NEAR) {
            return true;
        }
        double ex = 0.01;
        double x1 = x < t.minX() ? t.minX() + ex : x > t.maxX() ? t.maxX() - ex : (t.minX() + t.maxX()) * 0.5;
        double y1 = y < t.minY() ? t.minY() + ex : y > t.maxY() ? t.maxY() - ex : (t.minY() + t.maxY()) * 0.5;
        double z1 = z < t.minZ() ? t.minZ() + ex : z > t.maxZ() ? t.maxZ() - ex : (t.minZ() + t.maxZ()) * 0.5;
        if (ray(grid, x, y, z, (t.minX() + t.maxX()) * 0.5, (t.minY() + t.maxY()) * 0.5, (t.minZ() + t.maxZ()) * 0.5)
                || ray(grid, x, y, z, x1, y1, z1)) {
            return true;
        }
        for (int i = 0; i < 8; i++) {
            double px = (i & 1) == 0 ? t.minX() + ex : t.maxX() - ex;
            double py = (i & 2) == 0 ? t.minY() + ex : t.maxY() - ex;
            double pz = (i & 4) == 0 ? t.minZ() + ex : t.maxZ() - ex;
            if (ray(grid, x, y, z, px, py, pz)) {
                return true;
            }
        }
        return false;
    }

    private static boolean ray(Grid grid, double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        int bx = (int) Math.floor(x0);
        int by = (int) Math.floor(y0);
        int bz = (int) Math.floor(z0);
        int ex = (int) Math.floor(x1);
        int ey = (int) Math.floor(y1);
        int ez = (int) Math.floor(z1);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int sy = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tdy = sy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tdz = sz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double tx = sx == 0 ? Double.MAX_VALUE : (sx > 0 ? bx + 1 - x0 : x0 - bx) * tdx;
        double ty = sy == 0 ? Double.MAX_VALUE : (sy > 0 ? by + 1 - y0 : y0 - by) * tdy;
        double tz = sz == 0 ? Double.MAX_VALUE : (sz > 0 ? bz + 1 - z0 : z0 - bz) * tdz;
        int steps = Math.abs(ex - bx) + Math.abs(ey - by) + Math.abs(ez - bz);
        for (int i = 0; i < steps; i++) {
            if (tx < ty && tx < tz) {
                bx += sx;
                tx += tdx;
            } else if (ty < tz) {
                by += sy;
                ty += tdy;
            } else {
                bz += sz;
                tz += tdz;
            }
            if (bx == ex && by == ey && bz == ez) {
                return true;
            }
            if (grid.opaque(bx, by, bz)) {
                return false;
            }
        }
        return true;
    }

    private static final class Grid {
        private final ClientLevel level;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;
        private LevelChunk chunk;

        Grid(ClientLevel level) {
            this.level = level;
        }

        boolean opaque(int x, int y, int z) {
            int cx = x >> 4;
            int cz = z >> 4;
            if (cx != this.chunkX || cz != this.chunkZ) {
                this.chunkX = cx;
                this.chunkZ = cz;
                this.chunk = (LevelChunk) this.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            }
            if (this.chunk == null) {
                return false;
            }
            int index = this.chunk.getSectionIndex(y);
            if (index < 0 || index >= this.chunk.getSectionsCount()) {
                return false;
            }
            LevelChunkSection section = this.chunk.getSection(index);
            if (section.hasOnlyAir()) {
                return false;
            }
            BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
            return state.isSolidRender(this.level, this.pos.set(x, y, z));
        }
    }
}
