package com.qateralong.nimbus.lod;

import com.qateralong.nimbus.Nimbus;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

// Surface summary of every chunk the client has seen: per 4x4 block cell the top height and colour, kept in regions of
// 32x32 chunks and saved per server and dimension, so terrain beyond the render distance can be drawn from memory.
public final class LodStore {
    public static final int CELLS = 4;
    public static final int REGION_CHUNKS = 32;
    public static final int SIDE = REGION_CHUNKS * CELLS;
    public static final short EMPTY = Short.MIN_VALUE;
    private static final int MAGIC = 0x4E4C4F44;
    private static final int VERSION = 1;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Nimbus LOD io");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    public static final class Region {
        final int x;
        final int z;
        final short[] height = new short[SIDE * SIDE];
        final int[] color = new int[SIDE * SIDE];
        volatile boolean loaded;
        boolean dirty;
        public int version;

        Region(int x, int z) {
            this.x = x;
            this.z = z;
            java.util.Arrays.fill(this.height, EMPTY);
        }
    }

    private static final Long2ObjectOpenHashMap<Region> REGIONS = new Long2ObjectOpenHashMap<>();
    private static Path folder;
    private static long lastSave;
    public static int version;

    private LodStore() {
    }

    public static boolean open() {
        return folder != null;
    }

    public static void join(Minecraft minecraft) {
        leave();
        if (minecraft.level == null) {
            return;
        }
        String world;
        if (minecraft.getSingleplayerServer() != null) {
            world = "local-" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        } else if (minecraft.getCurrentServer() != null) {
            world = minecraft.getCurrentServer().ip;
        } else {
            world = "unknown";
        }
        String dimension = minecraft.level.dimension().location().toString();
        folder = minecraft.gameDirectory.toPath().resolve("nimbus-lod").resolve(safe(world)).resolve(safe(dimension));
        lastSave = System.currentTimeMillis();
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static void leave() {
        if (folder != null) {
            saveDirty(true);
        }
        REGIONS.clear();
        folder = null;
        version++;
    }

    // game shutdown: the io thread is a daemon, so pending writes are waited for here
    public static void close() {
        leave();
        IO.shutdown();
        try {
            IO.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    // null while the region is still being read from disk
    public static Region region(int x, int z) {
        if (folder == null) {
            return null;
        }
        long key = key(x, z);
        Region region = REGIONS.get(key);
        if (region == null) {
            region = new Region(x, z);
            REGIONS.put(key, region);
            Region target = region;
            Path file = folder.resolve("r." + x + "." + z + ".lod");
            IO.execute(() -> read(file, target));
        }
        return region.loaded ? region : null;
    }

    private static void read(Path file, Region region) {
        if (Files.isRegularFile(file)) {
            try (DataInputStream in = new DataInputStream(new InflaterInputStream(new java.io.BufferedInputStream(Files.newInputStream(file))))) {
                if (in.readInt() == MAGIC && in.readInt() == VERSION) {
                    short[] height = new short[SIDE * SIDE];
                    int[] color = new int[SIDE * SIDE];
                    for (int i = 0; i < height.length; i++) {
                        height[i] = in.readShort();
                    }
                    for (int i = 0; i < color.length; i++) {
                        color[i] = in.readInt();
                    }
                    Minecraft.getInstance().execute(() -> merge(region, height, color));
                    return;
                }
            } catch (IOException e) {
                Nimbus.LOGGER.warn("Nimbus LOD: unreadable {} ({})", file.getFileName(), e.toString());
            }
        }
        Minecraft.getInstance().execute(() -> {
            region.loaded = true;
            region.version++;
            version++;
        });
    }

    // cells captured while the file was loading are newer than the file
    private static void merge(Region region, short[] height, int[] color) {
        for (int i = 0; i < height.length; i++) {
            if (region.height[i] == EMPTY) {
                region.height[i] = height[i];
                region.color[i] = color[i];
            }
        }
        region.loaded = true;
        region.version++;
        version++;
    }

    public static void capture(LevelChunk chunk) {
        if (folder == null) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        int rx = pos.x >> 5;
        int rz = pos.z >> 5;
        long key = key(rx, rz);
        Region region = REGIONS.get(key);
        if (region == null) {
            region = new Region(rx, rz);
            REGIONS.put(key, region);
            Region target = region;
            Path file = folder.resolve("r." + rx + "." + rz + ".lod");
            IO.execute(() -> read(file, target));
        }
        int min = chunk.getMinBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = (pos.x & 31) * CELLS;
        int baseZ = (pos.z & 31) * CELLS;
        boolean changed = false;
        for (int cz = 0; cz < CELLS; cz++) {
            for (int cx = 0; cx < CELLS; cx++) {
                int best = Integer.MIN_VALUE;
                int bestX = 0;
                int bestZ = 0;
                for (int dz = 0; dz < 4; dz++) {
                    for (int dx = 0; dx < 4; dx++) {
                        int x = cx * 4 + dx;
                        int z = cz * 4 + dz;
                        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                        if (y > best) {
                            best = y;
                            bestX = x;
                            bestZ = z;
                        }
                    }
                }
                short height = EMPTY;
                int color = 0;
                if (best >= min) {
                    cursor.set(pos.getMinBlockX() + bestX, best, pos.getMinBlockZ() + bestZ);
                    var state = chunk.getBlockState(cursor);
                    int y = best;
                    // plants and snow layers take the colour of what they stand on when they are see-through from afar
                    color = LodColors.color(chunk.getLevel(), state, cursor);
                    if ((color >>> 24) == 0 && y > min) {
                        cursor.setY(--y);
                        color = LodColors.color(chunk.getLevel(), chunk.getBlockState(cursor), cursor);
                    }
                    height = (short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, y + 1));
                }
                int index = (baseZ + cz) * SIDE + baseX + cx;
                if (region.height[index] != height || region.color[index] != color) {
                    region.height[index] = height;
                    region.color[index] = color;
                    changed = true;
                }
            }
        }
        if (changed) {
            region.dirty = true;
            region.version++;
            version++;
        }
        long now = System.currentTimeMillis();
        if (now - lastSave > 60_000L) {
            lastSave = now;
            saveDirty(false);
        }
    }

    private static void saveDirty(boolean all) {
        List<Region> dirty = new ArrayList<>();
        for (Region region : REGIONS.values()) {
            if (region.dirty && region.loaded) {
                dirty.add(region);
            }
        }
        Path target = folder;
        for (Region region : dirty) {
            region.dirty = false;
            short[] height = region.height.clone();
            int[] color = region.color.clone();
            Path file = target.resolve("r." + region.x + "." + region.z + ".lod");
            IO.execute(() -> write(file, height, color));
        }
    }

    private static void write(Path file, short[] height, int[] color) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(temp))))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                for (short h : height) {
                    out.writeShort(h);
                }
                for (int c : color) {
                    out.writeInt(c);
                }
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Nimbus.LOGGER.warn("Nimbus LOD: could not save {} ({})", file.getFileName(), e.toString());
        }
    }

    static short height(Region region, int index) {
        return region.height[index];
    }

    static int color(Region region, int index) {
        return region.color[index];
    }
}
