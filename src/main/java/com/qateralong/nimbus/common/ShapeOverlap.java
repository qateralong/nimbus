package com.qateralong.nimbus.common;

import com.qateralong.nimbus.common.mixin.VoxelShapeAccessor;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Shapes.joinIsNotEmpty(shape moved by (x, y, z), box, AND) for a box made by Shapes.create, without building the
// moved shape or the index mergers: the same coordinate merge (with its 1e-7 tolerance) is walked per axis to find
// which cells of the shape overlap the box, then those cells are checked.
public final class ShapeOverlap {
    public static final int UNKNOWN = -1;
    private static final long NONE = Long.MIN_VALUE;

    private ShapeOverlap() {
    }

    // 1 overlap, 0 none, UNKNOWN when the box is not the single-cell kind this handles
    public static int test(VoxelShape shape, double x, double y, double z, VoxelShape box) {
        if (!(box instanceof ArrayVoxelShape) || ((VoxelShapeAccessor) box).nimbus$discrete() != ((VoxelShapeAccessor) Shapes.block()).nimbus$discrete()) {
            return UNKNOWN;
        }
        VoxelShapeAccessor self = (VoxelShapeAccessor) shape;
        VoxelShapeAccessor other = (VoxelShapeAccessor) box;
        DoubleList bx = other.nimbus$coords(Direction.Axis.X);
        DoubleList by = other.nimbus$coords(Direction.Axis.Y);
        DoubleList bz = other.nimbus$coords(Direction.Axis.Z);
        if (!(bx instanceof DoubleArrayList) || !(by instanceof DoubleArrayList) || !(bz instanceof DoubleArrayList) || bx.size() != 2 || by.size() != 2 || bz.size() != 2) {
            return UNKNOWN;
        }
        if (shape.max(Direction.Axis.X) + x < bx.getDouble(0) - 1.0E-7 || bx.getDouble(1) < shape.min(Direction.Axis.X) + x - 1.0E-7
                || shape.max(Direction.Axis.Y) + y < by.getDouble(0) - 1.0E-7 || by.getDouble(1) < shape.min(Direction.Axis.Y) + y - 1.0E-7
                || shape.max(Direction.Axis.Z) + z < bz.getDouble(0) - 1.0E-7 || bz.getDouble(1) < shape.min(Direction.Axis.Z) + z - 1.0E-7) {
            return 0;
        }
        DiscreteVoxelShape cells = self.nimbus$discrete();
        long rx = range(self.nimbus$coords(Direction.Axis.X), x, bx.getDouble(0), bx.getDouble(1), cells.getXSize());
        if (rx == NONE) {
            return 0;
        }
        long ry = range(self.nimbus$coords(Direction.Axis.Y), y, by.getDouble(0), by.getDouble(1), cells.getYSize());
        if (ry == NONE) {
            return 0;
        }
        long rz = range(self.nimbus$coords(Direction.Axis.Z), z, bz.getDouble(0), bz.getDouble(1), cells.getZSize());
        if (rz == NONE) {
            return 0;
        }
        for (int i = (int) (rx >> 32); i <= (int) rx; i++) {
            for (int j = (int) (ry >> 32); j <= (int) ry; j++) {
                for (int k = (int) (rz >> 32); k <= (int) rz; k++) {
                    if (cells.isFullWide(i, j, k)) {
                        return 1;
                    }
                }
            }
        }
        return 0;
    }

    // cells of the shape paired with the box's only cell, as Shapes.createIndexMerger pairs them; NONE if none
    private static long range(DoubleList coords, double offset, double b0, double b1, int size) {
        int count = coords.size();
        if (count == 2 && coords.getDouble(0) + offset == b0 && coords.getDouble(1) + offset == b1) {
            return 0L;
        }
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        double last = Double.NaN;
        boolean open = false;
        int pendingFirst = 0;
        int pendingSecond = 0;
        int m = 0;
        int n = 0;
        while (true) {
            boolean lowerDone = m >= count;
            boolean upperDone = n >= 2;
            if (lowerDone && upperDone) {
                break;
            }
            boolean takeLower = !lowerDone && (upperDone || coords.getDouble(m) + offset < (n == 0 ? b0 : b1) + 1.0E-7);
            if (takeLower) {
                m++;
                if (n == 0 || upperDone) {
                    continue;
                }
            } else {
                n++;
                if (m == 0 || lowerDone) {
                    continue;
                }
            }
            int first = m - 1;
            int second = n - 1;
            double value = takeLower ? coords.getDouble(first) + offset : (second == 0 ? b0 : b1);
            if (!(last >= value - 1.0E-7)) {
                if (open && pendingSecond == 0 && pendingFirst >= 0 && pendingFirst < size) {
                    lo = Math.min(lo, pendingFirst);
                    hi = Math.max(hi, pendingFirst);
                }
                open = true;
                last = value;
            }
            pendingFirst = first;
            pendingSecond = second;
        }
        return lo > hi ? NONE : (long) lo << 32 | hi & 0xFFFFFFFFL;
    }
}
