package com.qateralong.nimbus.light;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;

// Client light engine. The server sends finished light, so the client only stores it (in the chunk itself, no
// per-update map copies) and repairs it locally around changed blocks until the server's own update arrives.
// Sky sources are columns of 15 straight down from the sky; they are kept by walking the changed column, so no
// per-chunk source heightmap is needed.
public final class ClientLight extends LevelLightEngine {
    private static final byte[] ZERO = new byte[2048];
    private static final byte[] FULL = new byte[2048];
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int ALL = 63;
    private static final int UP = 1 << Direction.UP.ordinal();
    private static final long EMISSION = 1L << 10;
    private static final int[] OPPOSITE = new int[6];
    private static final BlockState OUTSIDE = Blocks.BEDROCK.defaultBlockState();

    static {
        Arrays.fill(FULL, (byte) -1);
        for (Direction direction : DIRECTIONS) {
            OPPOSITE[direction.ordinal()] = 1 << direction.getOpposite().ordinal();
        }
    }

    private final LightChunkGetter chunks;
    private final BlockGetter level;
    private final boolean hasSky;
    private final int minSection;
    private final int sections;
    private final LongOpenHashSet pending = new LongOpenHashSet();
    // checks for chunks whose light is not on yet: vanilla drops them, they wait here for the light data instead
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<it.unimi.dsi.fastutil.longs.LongArrayList> deferred = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    public static int repairedSources;
    private final LongOpenHashSet affected = new LongOpenHashSet();
    private final LongConsumer markAffected = this.affected::add;
    private final ArrayList<LightColumn> touched = new ArrayList<>();
    private final LongArrayFIFOQueue increases = new LongArrayFIFOQueue();
    private final LongArrayFIFOQueue decreases = new LongArrayFIFOQueue();
    private final BlockPos.MutableBlockPos posA = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos posB = new BlockPos.MutableBlockPos();
    private final View blockView = new View(false);
    private final View skyView = new View(true);
    private final long[] cacheKey = new long[2];
    private final LightChunk[] cacheChunk = new LightChunk[2];
    private final LightColumn[] cacheColumn = new LightColumn[2];

    public ClientLight(LightChunkGetter chunks, boolean sky) {
        super(chunks, false, false);
        this.chunks = chunks;
        this.level = chunks.getLevel();
        this.hasSky = sky;
        this.minSection = this.getMinLightSection();
        this.sections = this.getLightSectionCount();
        this.clearCache();
    }

    // reads, any thread

    private LightColumn lookup(int chunkX, int chunkZ) {
        return this.chunks.getChunkForLighting(chunkX, chunkZ) instanceof LightHolder holder ? holder.nimbus$light() : null;
    }

    private static int nibble(byte[] data, int x, int y, int z) {
        int index = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        return data[index >> 1] >> ((index & 1) << 2) & 15;
    }

    public int blockAt(int x, int y, int z) {
        LightColumn column = this.lookup(x >> 4, z >> 4);
        if (column == null) {
            return 0;
        }
        int index = (y >> 4) - this.minSection;
        if (index < 0 || index >= this.sections) {
            return 0;
        }
        byte[] data = column.block[index];
        return data == null ? 0 : nibble(data, x, y, z);
    }

    public int skyAt(int x, int y, int z) {
        if (!this.hasSky) {
            return 0;
        }
        LightColumn column = this.lookup(x >> 4, z >> 4);
        if (column == null) {
            return 15;
        }
        int index = (y >> 4) - this.minSection;
        if (index >= this.sections) {
            return 15;
        }
        byte[][] sky = column.sky;
        if (index >= 0) {
            byte[] data = sky[index];
            if (data != null) {
                return nibble(data, x, y, z);
            }
        }
        // missing sections repeat the bottom layer of the first stored section above
        for (int i = Math.max(0, index + 1); i < this.sections; i++) {
            byte[] data = sky[i];
            if (data != null) {
                return nibble(data, x, 0, z);
            }
        }
        return 15;
    }

    // bug reports: what a section's light data is and the range of its values
    public String describe(int sx, int sy, int sz) {
        LightColumn column = this.lookup(sx, sz);
        if (column == null) {
            return "no column";
        }
        int index = sy - this.minSection;
        if (index < 0 || index >= this.sections) {
            return "outside";
        }
        return "on " + column.on + ", block " + range(column.block[index]) + ", sky " + (this.hasSky ? range(column.sky[index]) : "-");
    }

    private static String range(byte[] data) {
        if (data == null) {
            return "none";
        }
        if (data == ZERO) {
            return "zero";
        }
        if (data == FULL) {
            return "full";
        }
        int min = 15;
        int max = 0;
        for (int i = 0; i < 4096; i++) {
            int v = data[i >> 1] >> ((i & 1) << 2) & 15;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return min + ".." + max;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return Math.max(this.blockAt(x, y, z), this.skyAt(x, y, z) - amount);
    }

    @Override
    public boolean lightOnInSection(SectionPos pos) {
        LightColumn column = this.lookup(pos.x(), pos.z());
        return column != null && column.on;
    }

    @Override
    public LayerLightEventListener getLayerListener(LightLayer layer) {
        if (layer == LightLayer.BLOCK) {
            return this.blockView;
        }
        return this.hasSky ? this.skyView : LayerLightEventListener.DummyLightLayerEventListener.INSTANCE;
    }

    private DataLayer dataLayer(boolean sky, SectionPos pos) {
        LightColumn column = this.lookup(pos.x(), pos.z());
        int index = pos.y() - this.minSection;
        if (column == null || index < 0 || index >= this.sections) {
            return null;
        }
        byte[] data = (sky ? column.sky : column.block)[index];
        if (data == null) {
            return null;
        }
        return data == ZERO ? new DataLayer() : data == FULL ? new DataLayer(15) : new DataLayer(data);
    }

    @Override
    public String getDebugData(LightLayer layer, SectionPos pos) {
        return this.getDebugSectionType(layer, pos).display();
    }

    @Override
    public LayerLightSectionStorage.SectionType getDebugSectionType(LightLayer layer, SectionPos pos) {
        return this.dataLayer(layer == LightLayer.SKY, pos) != null
                ? LayerLightSectionStorage.SectionType.LIGHT_AND_DATA : LayerLightSectionStorage.SectionType.EMPTY;
    }

    // data from the server, main thread

    private LightColumn columnForWrite(int chunkX, int chunkZ) {
        if (!(this.chunks.getChunkForLighting(chunkX, chunkZ) instanceof LightHolder holder)) {
            return null;
        }
        LightColumn column = holder.nimbus$light();
        if (column == null) {
            column = new LightColumn(this.sections);
            holder.nimbus$setLight(column);
        }
        return column;
    }

    private static byte[] compact(byte[] data) {
        byte first = data[0];
        if (first != 0 && first != -1) {
            return data;
        }
        for (byte b : data) {
            if (b != first) {
                return data;
            }
        }
        return first == 0 ? ZERO : FULL;
    }

    @Override
    public void queueSectionData(LightLayer layer, SectionPos pos, DataLayer data) {
        boolean sky = layer == LightLayer.SKY;
        int index = pos.y() - this.minSection;
        if ((sky && !this.hasSky) || index < 0 || index >= this.sections) {
            return;
        }
        LightColumn column = data == null ? this.lookup(pos.x(), pos.z()) : this.columnForWrite(pos.x(), pos.z());
        if (column == null) {
            return;
        }
        byte[] bytes = null;
        if (data != null) {
            bytes = data.isEmpty() ? ZERO : data.isDefinitelyFilledWith(15) ? FULL : compact(data.getData());
        }
        (sky ? column.sky : column.block)[index] = bytes;
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean enabled) {
        if (!enabled) {
            this.deferred.remove(pos.toLong());
            LightColumn column = this.lookup(pos.x, pos.z);
            if (column != null) {
                column.on = false;
            }
            return;
        }
        LightColumn column = this.columnForWrite(pos.x, pos.z);
        if (column == null) {
            return;
        }
        boolean wasOn = column.on;
        if (this.hasSky && this.chunks.getChunkForLighting(pos.x, pos.z) instanceof ChunkAccess chunk) {
            // empty sky sections above every block are open sky
            int top = chunk.getHighestFilledSectionIndex();
            if (top >= 0) {
                for (int i = top + 2; i < this.sections; i++) {
                    if (column.sky[i] == ZERO) {
                        column.sky[i] = FULL;
                    }
                }
            }
        }
        column.on = true;
        if (!wasOn) {
            it.unimi.dsi.fastutil.longs.LongArrayList waiting = this.deferred.remove(pos.toLong());
            if (waiting != null) {
                this.pending.addAll(waiting);
            }
            this.checkSources(pos, column);
        }
    }

    // The server builds a chunk's light when it sends it, and only re-sends light to players at the edge of their view:
    // a light source that changed just before sending can arrive with stale light around it. A source darker than its
    // own emission is checked again here.
    private void checkSources(ChunkPos pos, LightColumn column) {
        if (!(this.chunks.getChunkForLighting(pos.x, pos.z) instanceof ChunkAccess chunk)) {
            return;
        }
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
        for (int s = 0; s < sections.length; s++) {
            net.minecraft.world.level.chunk.LevelChunkSection section = sections[s];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> state.getLightEmission() > 0)) {
                continue;
            }
            int index = chunk.getSectionYFromSectionIndex(s) - this.minSection;
            if (index < 0 || index >= this.sections) {
                continue;
            }
            byte[] data = column.block[index];
            int baseX = pos.getMinBlockX();
            int baseY = chunk.getSectionYFromSectionIndex(s) << 4;
            int baseZ = pos.getMinBlockZ();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int emission = section.getBlockState(x, y, z).getLightEmission();
                        if (emission > 0 && (data == null ? 0 : nibble(data, x, y, z)) < emission) {
                            this.pending.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                            repairedSources++;
                        }
                    }
                }
            }
        }
    }

    private void defer(long pos) {
        long chunk = ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
        if (!(this.chunks.getChunkForLighting(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4) instanceof LightHolder)) {
            return;
        }
        it.unimi.dsi.fastutil.longs.LongArrayList list = this.deferred.computeIfAbsent(chunk, k -> new it.unimi.dsi.fastutil.longs.LongArrayList());
        if (list.size() < 4096) {
            list.add(pos);
        }
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean empty) {
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
    }

    @Override
    public void retainData(ChunkPos pos, boolean retain) {
    }

    // local repair, main thread

    @Override
    public void checkBlock(BlockPos pos) {
        this.pending.add(pos.asLong());
    }

    @Override
    public boolean hasLightWork() {
        return !this.pending.isEmpty();
    }

    @Override
    public int runLightUpdates() {
        if (this.pending.isEmpty()) {
            return 0;
        }
        this.clearCache();
        int work = 0;
        for (int pass = 0; pass < (this.hasSky ? 2 : 1); pass++) {
            boolean sky = pass == 1;
            LongIterator iterator = this.pending.iterator();
            while (iterator.hasNext()) {
                long pos = iterator.nextLong();
                if (sky) {
                    this.checkSky(pos);
                } else {
                    this.checkBlockLight(pos);
                }
            }
            work += this.propagateDecreases(sky);
            work += this.propagateIncreases(sky);
        }
        this.pending.clear();
        for (LightColumn column : this.touched) {
            publish(column.skyWork, column.sky);
            publish(column.blockWork, column.block);
            column.touched = false;
        }
        this.touched.clear();
        LongIterator iterator = this.affected.iterator();
        while (iterator.hasNext()) {
            this.chunks.onLightUpdate(LightLayer.BLOCK, SectionPos.of(iterator.nextLong()));
        }
        this.affected.clear();
        this.clearCache();
        return work;
    }

    private static void publish(byte[][] work, byte[][] visible) {
        if (work == null) {
            return;
        }
        for (int i = 0; i < work.length; i++) {
            if (work[i] != null) {
                visible[i] = work[i];
                work[i] = null;
            }
        }
    }

    private void clearCache() {
        Arrays.fill(this.cacheKey, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(this.cacheChunk, null);
        Arrays.fill(this.cacheColumn, null);
    }

    private int slot(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        if (this.cacheKey[0] == key) {
            return 0;
        }
        if (this.cacheKey[1] != key) {
            this.cacheKey[1] = key;
            LightChunk chunk = this.chunks.getChunkForLighting(chunkX, chunkZ);
            this.cacheChunk[1] = chunk;
            this.cacheColumn[1] = chunk instanceof LightHolder holder ? holder.nimbus$light() : null;
        }
        long k = this.cacheKey[0];
        LightChunk c = this.cacheChunk[0];
        LightColumn l = this.cacheColumn[0];
        this.cacheKey[0] = this.cacheKey[1];
        this.cacheChunk[0] = this.cacheChunk[1];
        this.cacheColumn[0] = this.cacheColumn[1];
        this.cacheKey[1] = k;
        this.cacheChunk[1] = c;
        this.cacheColumn[1] = l;
        return 0;
    }

    private LightColumn column(int x, int z) {
        LightColumn column = this.cacheColumn[this.slot(x >> 4, z >> 4)];
        return column != null && column.on ? column : null;
    }

    private BlockState state(int x, int y, int z) {
        LightChunk chunk = this.cacheChunk[this.slot(x >> 4, z >> 4)];
        return chunk == null ? OUTSIDE : chunk.getBlockState(this.posA.set(x, y, z));
    }

    private int opacity(BlockState state, int x, int y, int z) {
        return state.getLightBlock(this.level, this.posB.set(x, y, z));
    }

    private static boolean emptyShape(BlockState state) {
        return !state.canOcclude() || !state.useShapeForLightOcclusion();
    }

    private boolean occludes(BlockState from, long fromPos, BlockState to, long toPos, Direction direction) {
        if (emptyShape(from) && emptyShape(to)) {
            return false;
        }
        return Shapes.faceShapeOccludes(
                LightEngine.getOcclusionShape(this.level, this.posA.set(fromPos), from, direction),
                LightEngine.getOcclusionShape(this.level, this.posB.set(toPos), to, direction.getOpposite()));
    }

    private byte[] data(LightColumn column, boolean sky, int index) {
        byte[][] work = sky ? column.skyWork : column.blockWork;
        if (work != null && work[index] != null) {
            return work[index];
        }
        return (sky ? column.sky : column.block)[index];
    }

    private int get(boolean sky, LightColumn column, int index, int x, int y, int z) {
        byte[] data = this.data(column, sky, index);
        if (data != null) {
            return nibble(data, x, y, z);
        }
        if (!sky) {
            return 0;
        }
        for (int i = index + 1; i < this.sections; i++) {
            data = this.data(column, true, i);
            if (data != null) {
                return nibble(data, x, 0, z);
            }
        }
        return 15;
    }

    private void set(boolean sky, LightColumn column, int index, int x, int y, int z, int value) {
        byte[][] work = sky ? column.skyWork : column.blockWork;
        if (work == null) {
            work = new byte[this.sections][];
            if (sky) {
                column.skyWork = work;
            } else {
                column.blockWork = work;
            }
        }
        byte[] data = work[index];
        if (data == null) {
            byte[] visible = (sky ? column.sky : column.block)[index];
            if (visible != null) {
                data = visible.clone();
            } else if (!sky) {
                data = new byte[2048];
            } else {
                // missing sections below read this one's bottom layer, so they become real before it changes
                data = this.virtualSky(column, index);
                for (int below = index - 1; below >= 0 && this.data(column, true, below) == null; below--) {
                    work[below] = data.clone();
                }
            }
            work[index] = data;
            if (!column.touched) {
                column.touched = true;
                this.touched.add(column);
            }
        }
        int i = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        int shift = (i & 1) << 2;
        data[i >> 1] = (byte) (data[i >> 1] & ~(15 << shift) | value << shift);
        SectionPos.aroundAndAtBlockPos(x, y, z, this.markAffected);
    }

    private byte[] virtualSky(LightColumn column, int index) {
        for (int i = index + 1; i < this.sections; i++) {
            byte[] above = this.data(column, true, i);
            if (above != null) {
                byte[] data = new byte[2048];
                for (int layer = 0; layer < 16; layer++) {
                    System.arraycopy(above, 0, data, layer * 128, 128);
                }
                return data;
            }
        }
        return FULL.clone();
    }

    private static long entry(int level, int directions) {
        return level | (long) directions << 4;
    }

    private void increase(long pos, long entry) {
        this.increases.enqueue(pos);
        this.increases.enqueue(entry);
    }

    private void decrease(long pos, long entry) {
        this.decreases.enqueue(pos);
        this.decreases.enqueue(entry);
    }

    private void checkBlockLight(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        LightColumn column = this.column(x, z);
        int index = (y >> 4) - this.minSection;
        if (column == null) {
            this.defer(pos);
            return;
        }
        if (index < 0 || index >= this.sections) {
            return;
        }
        int emission = this.state(x, y, z).getLightEmission();
        int current = this.get(false, column, index, x, y, z);
        if (emission < current) {
            this.set(false, column, index, x, y, z, 0);
            this.decrease(pos, entry(current, ALL));
        } else {
            this.decrease(pos, entry(1, ALL));
        }
        if (emission > 0) {
            this.increase(pos, entry(emission, ALL) | EMISSION);
        }
    }

    private void checkSky(long pos) {
        int x = BlockPos.getX(pos);
        int y = BlockPos.getY(pos);
        int z = BlockPos.getZ(pos);
        LightColumn column = this.column(x, z);
        int index = (y >> 4) - this.minSection;
        if (column == null) {
            this.defer(pos);
            return;
        }
        if (index < 0 || index >= this.sections) {
            return;
        }
        int current = this.get(true, column, index, x, y, z);
        BlockState state = this.state(x, y, z);
        if (this.isSource(column, x, y, z, state)) {
            if (current != 15) {
                this.set(true, column, index, x, y, z, 15);
            }
            // the new shape may close faces that light used to pass through
            this.decrease(pos, entry(15, ALL & ~UP));
            this.increase(pos, entry(15, ALL & ~UP));
            if (!this.addSourcesBelow(column, x, y, z, state)) {
                this.removeSourcesBelow(column, x, y - 1, z);
            }
            return;
        }
        if (current == 15) {
            this.removeSourcesBelow(column, x, y - 1, z);
        }
        if (current > 0) {
            this.set(true, column, index, x, y, z, 0);
            this.decrease(pos, entry(current, ALL));
        } else {
            this.decrease(pos, entry(1, ALL));
        }
    }

    private boolean isSource(LightColumn column, int x, int y, int z, BlockState state) {
        if (this.opacity(state, x, y, z) != 0) {
            return false;
        }
        int index = (y + 1 >> 4) - this.minSection;
        if (index >= this.sections) {
            return true;
        }
        if (this.get(true, column, index, x, y + 1, z) != 15) {
            return false;
        }
        BlockState above = this.state(x, y + 1, z);
        return !this.occludes(above, BlockPos.asLong(x, y + 1, z), state, BlockPos.asLong(x, y, z), Direction.DOWN);
    }

    // false when the edge right below is closed
    private boolean addSourcesBelow(LightColumn column, int x, int y, int z, BlockState top) {
        BlockState upper = top;
        for (int yy = y - 1; ; yy--) {
            int index = (yy >> 4) - this.minSection;
            if (index < 0) {
                return true;
            }
            BlockState state = this.state(x, yy, z);
            if (this.opacity(state, x, yy, z) != 0
                    || this.occludes(upper, BlockPos.asLong(x, yy + 1, z), state, BlockPos.asLong(x, yy, z), Direction.DOWN)) {
                return yy != y - 1;
            }
            if (this.get(true, column, index, x, yy, z) == 15) {
                return true;
            }
            this.set(true, column, index, x, yy, z, 15);
            this.increase(BlockPos.asLong(x, yy, z), entry(15, ALL & ~UP));
            upper = state;
        }
    }

    private void removeSourcesBelow(LightColumn column, int x, int y, int z) {
        for (int yy = y; ; yy--) {
            int index = (yy >> 4) - this.minSection;
            if (index < 0 || this.get(true, column, index, x, yy, z) != 15) {
                return;
            }
            this.set(true, column, index, x, yy, z, 0);
            this.decrease(BlockPos.asLong(x, yy, z), entry(15, yy == y ? ALL : ALL & ~UP));
        }
    }

    private int propagateDecreases(boolean sky) {
        int count = 0;
        while (!this.decreases.isEmpty()) {
            long pos = this.decreases.dequeueLong();
            long entry = this.decreases.dequeueLong();
            this.propagateDecrease(sky, pos, entry);
            count++;
        }
        return count;
    }

    private int propagateIncreases(boolean sky) {
        int count = 0;
        while (!this.increases.isEmpty()) {
            long pos = this.increases.dequeueLong();
            long entry = this.increases.dequeueLong();
            count++;
            int x = BlockPos.getX(pos);
            int y = BlockPos.getY(pos);
            int z = BlockPos.getZ(pos);
            LightColumn column = this.column(x, z);
            int index = (y >> 4) - this.minSection;
            if (column == null || index < 0 || index >= this.sections) {
                continue;
            }
            int level = (int) (entry & 15);
            int current = this.get(sky, column, index, x, y, z);
            if ((entry & EMISSION) != 0 && current < level) {
                this.set(sky, column, index, x, y, z, level);
                current = level;
            }
            if (current == level) {
                this.propagateIncrease(sky, pos, entry, level);
            }
        }
        return count;
    }

    private void propagateIncrease(boolean sky, long pos, long entry, int level) {
        int directions = (int) (entry >> 4) & ALL;
        BlockState from = null;
        for (Direction direction : DIRECTIONS) {
            if ((directions & 1 << direction.ordinal()) == 0) {
                continue;
            }
            long next = BlockPos.offset(pos, direction);
            int x = BlockPos.getX(next);
            int y = BlockPos.getY(next);
            int z = BlockPos.getZ(next);
            LightColumn column = this.column(x, z);
            int index = (y >> 4) - this.minSection;
            if (column == null || index < 0 || index >= this.sections) {
                continue;
            }
            int current = this.get(sky, column, index, x, y, z);
            if (level - 1 <= current) {
                continue;
            }
            BlockState to = this.state(x, y, z);
            int value = level - Math.max(1, this.opacity(to, x, y, z));
            if (value <= current) {
                continue;
            }
            if (from == null) {
                from = this.state(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
            }
            if (this.occludes(from, pos, to, next, direction)) {
                continue;
            }
            this.set(sky, column, index, x, y, z, value);
            if (value > 1) {
                this.increase(next, entry(value, ALL & ~OPPOSITE[direction.ordinal()]));
            }
        }
    }

    private void propagateDecrease(boolean sky, long pos, long entry) {
        int level = (int) (entry & 15);
        int directions = (int) (entry >> 4) & ALL;
        for (Direction direction : DIRECTIONS) {
            if ((directions & 1 << direction.ordinal()) == 0) {
                continue;
            }
            long next = BlockPos.offset(pos, direction);
            int x = BlockPos.getX(next);
            int y = BlockPos.getY(next);
            int z = BlockPos.getZ(next);
            LightColumn column = this.column(x, z);
            int index = (y >> 4) - this.minSection;
            if (column == null || index < 0 || index >= this.sections) {
                continue;
            }
            int current = this.get(sky, column, index, x, y, z);
            if (current == 0) {
                continue;
            }
            if (current > level - 1) {
                this.increase(next, entry(current, OPPOSITE[direction.ordinal()]));
                continue;
            }
            int emission = sky ? 0 : this.state(x, y, z).getLightEmission();
            this.set(sky, column, index, x, y, z, 0);
            if (emission < current) {
                this.decrease(next, entry(current, ALL & ~OPPOSITE[direction.ordinal()]));
            }
            if (emission > 0) {
                this.increase(next, entry(emission, ALL) | EMISSION);
            }
        }
    }

    private final class View implements LayerLightEventListener {
        private final boolean sky;

        View(boolean sky) {
            this.sky = sky;
        }

        @Override
        public DataLayer getDataLayerData(SectionPos pos) {
            return ClientLight.this.dataLayer(this.sky, pos);
        }

        @Override
        public int getLightValue(BlockPos pos) {
            return this.sky ? ClientLight.this.skyAt(pos.getX(), pos.getY(), pos.getZ()) : ClientLight.this.blockAt(pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public void checkBlock(BlockPos pos) {
            ClientLight.this.checkBlock(pos);
        }

        @Override
        public boolean hasLightWork() {
            return ClientLight.this.hasLightWork();
        }

        @Override
        public int runLightUpdates() {
            return 0;
        }

        @Override
        public void updateSectionStatus(SectionPos pos, boolean empty) {
        }

        @Override
        public void setLightEnabled(ChunkPos pos, boolean enabled) {
        }

        @Override
        public void propagateLightSources(ChunkPos pos) {
        }
    }
}
