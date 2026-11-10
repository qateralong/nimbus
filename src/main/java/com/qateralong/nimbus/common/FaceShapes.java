package com.qateralong.nimbus.common;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Face slices of block occlusion shapes, shared: every state used to get its own six slice shapes even when the states
// share one occlusion shape (~100k slice objects in a modpack), and full cubes built with Block.box got slices instead of block()
public final class FaceShapes {
    private static final Map<VoxelShape, VoxelShape[]> FACES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int DIRECTIONS = Direction.values().length;

    private FaceShapes() {
    }

    // shapes built separately per state but with the same boxes share their faces too
    private static final Map<List<net.minecraft.world.phys.AABB>, VoxelShape[]> BY_CONTENT = new ConcurrentHashMap<>();
    private static final Map<List<VoxelShape>, VoxelShape[]> FACE_ARRAYS = new ConcurrentHashMap<>();
    private static final Map<Long, boolean[]> STURDY = new ConcurrentHashMap<>();

    // VoxelShape has identity equality, so equal lists mean the same face objects
    public static VoxelShape[] shared(VoxelShape[] faces) {
        for (VoxelShape face : faces) {
            if (face == null) {
                return faces;
            }
        }
        return FACE_ARRAYS.computeIfAbsent(List.of(faces), key -> faces);
    }

    public static boolean[] shared(boolean[] flags) {
        if (flags.length > 57) {
            return flags;
        }
        long key = flags.length;
        for (int i = 0; i < flags.length; i++) {
            if (flags[i]) {
                key |= 1L << (i + 6);
            }
        }
        return STURDY.computeIfAbsent(key, k -> flags);
    }

    public static VoxelShape face(VoxelShape shape, Direction direction) {
        VoxelShape[] faces = FACES.computeIfAbsent(shape, key -> BY_CONTENT.computeIfAbsent(key.toAabbs(), boxes -> new VoxelShape[DIRECTIONS]));
        synchronized (faces) {
            VoxelShape face = faces[direction.ordinal()];
            if (face == null) {
                face = shape != Shapes.block() && Block.isShapeFullBlock(shape) ? Shapes.block() : Shapes.getFaceShape(shape, direction);
                faces[direction.ordinal()] = face;
            }
            return face;
        }
    }
}
