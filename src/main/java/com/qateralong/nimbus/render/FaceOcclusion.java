package com.qateralong.nimbus.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Vanilla's face occlusion cache, keyed by (state, neighbour, face) like vanilla, but direct-mapped per thread
// so a lookup allocates nothing (vanilla builds a BlockStatePairKey for every face it asks about).
public final class FaceOcclusion {
    private static final int SIZE = 4096;
    private static final ThreadLocal<FaceOcclusion> CACHE = ThreadLocal.withInitial(FaceOcclusion::new);
    private final BlockState[] states = new BlockState[SIZE];
    private final BlockState[] neighbours = new BlockState[SIZE];
    private final byte[] faces = new byte[SIZE];
    private final boolean[] visible = new boolean[SIZE];

    private FaceOcclusion() {
    }

    // The neighbour can occlude; true when part of the face stays visible.
    public static boolean visible(BlockState state, BlockState neighbour, BlockGetter level, BlockPos pos, BlockPos neighbourPos, Direction face) {
        FaceOcclusion cache = CACHE.get();
        int hash = (System.identityHashCode(state) * 31 + System.identityHashCode(neighbour)) * 6 + face.ordinal();
        int slot = (hash ^ hash >>> 15) & SIZE - 1;
        if (cache.states[slot] == state && cache.neighbours[slot] == neighbour && cache.faces[slot] == face.ordinal()) {
            return cache.visible[slot];
        }
        VoxelShape shape = state.getFaceOcclusionShape(level, pos, face);
        if (shape.isEmpty()) {
            return true;
        }
        boolean result = Shapes.joinIsNotEmpty(shape, neighbour.getFaceOcclusionShape(level, neighbourPos, face.getOpposite()), BooleanOp.ONLY_FIRST);
        cache.states[slot] = state;
        cache.neighbours[slot] = neighbour;
        cache.faces[slot] = (byte) face.ordinal();
        cache.visible[slot] = result;
        return result;
    }
}
