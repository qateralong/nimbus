package com.qateralong.nimbus.common;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.pathfinder.PathType;

// Pathfinding shortcuts: a block's own path type depends only on its state and the block tags, and a chunk section
// whose palette holds nothing a mob avoids next to it lets the 26-neighbour danger scan be skipped.
public final class PathCache {
    private static final PathType[] TYPES = PathType.values();
    public static volatile int generation = 1;

    public interface State {
        byte nimbus$pathType();

        int nimbus$pathGeneration();

        void nimbus$setPathType(byte type, int generation);
    }

    public interface Section {
        byte nimbus$danger();

        int nimbus$dangerGeneration();

        void nimbus$setDanger(byte danger, int generation);
    }

    private PathCache() {
    }

    public static void tagsChanged() {
        generation++;
    }

    public static PathType cached(BlockState state) {
        State holder = (State) state;
        byte type = holder.nimbus$pathType();
        return type != 0 && holder.nimbus$pathGeneration() == generation ? TYPES[type - 1] : null;
    }

    public static void store(BlockState state, PathType type) {
        ((State) state).nimbus$setPathType((byte) (type.ordinal() + 1), generation);
    }

    // the neighbour types checkNeighbourBlocks reacts to
    private static boolean dangerous(PathType type) {
        return type == PathType.DAMAGE_OTHER || type == PathType.DAMAGE_FIRE || type == PathType.LAVA || type == PathType.WATER
                || type == PathType.DAMAGE_CAUTIOUS;
    }

    static boolean dangerous(BlockState state) {
        PathType type = cached(state);
        if (type == null) {
            type = pathType(state);
        }
        return dangerous(type);
    }

    // set by the walk evaluator mixin: vanilla's state-only computation
    public static java.util.function.Function<BlockState, PathType> compute;

    private static PathType pathType(BlockState state) {
        PathType type = compute.apply(state);
        store(state, type);
        return type;
    }

    // true when no block within one of (x, y, z) can make it a danger or water border
    public static boolean quietAround(CollisionGetter level, int x, int y, int z) {
        if (compute == null) {
            return false;
        }
        int minSectionX = SectionPos.blockToSectionCoord(x - 1);
        int maxSectionX = SectionPos.blockToSectionCoord(x + 1);
        int minSectionY = SectionPos.blockToSectionCoord(y - 1);
        int maxSectionY = SectionPos.blockToSectionCoord(y + 1);
        int minSectionZ = SectionPos.blockToSectionCoord(z - 1);
        int maxSectionZ = SectionPos.blockToSectionCoord(z + 1);
        for (int sx = minSectionX; sx <= maxSectionX; sx++) {
            for (int sz = minSectionZ; sz <= maxSectionZ; sz++) {
                BlockGetter chunk = level.getChunkForCollisions(sx, sz);
                if (!(chunk instanceof ChunkAccess access)) {
                    return false;
                }
                for (int sy = minSectionY; sy <= maxSectionY; sy++) {
                    int index = access.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= access.getSectionsCount()) {
                        continue;
                    }
                    if (!quiet(access.getSection(index))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean quiet(LevelChunkSection section) {
        Section holder = (Section) section;
        int gen = generation;
        byte danger = holder.nimbus$danger();
        if (danger == 0 || holder.nimbus$dangerGeneration() != gen) {
            danger = section.maybeHas(PathCache::dangerous) ? (byte) 2 : (byte) 1;
            holder.nimbus$setDanger(danger, gen);
        }
        return danger == 1;
    }
}
