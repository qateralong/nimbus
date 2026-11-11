package com.qateralong.nimbus.common.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

// How much of an entity an explosion sees: vanilla runs a full clip (fluid lookups, interaction shapes, hit results)
// for each of ~27 rays only to ask "did it hit anything". Same rays, same blocks visited, same collider shapes, but
// stopping at the first hit and with one collision context per entity.
@Mixin(value = Explosion.class, priority = 900)
public abstract class ExplosionExposureMixin {
    /**
     * @author qateralong
     * @reason a hit test instead of a full clip per ray
     */
    @Overwrite
    public static float getSeenPercent(Vec3 explosionVector, Entity entity) {
        AABB box = entity.getBoundingBox();
        double d = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double e = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double f = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double g = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
        double h = (1.0 - Math.floor(1.0 / f) * f) / 2.0;
        if (d < 0.0 || e < 0.0 || f < 0.0) {
            return 0.0F;
        }
        Level level = entity.level();
        CollisionContext context = CollisionContext.of(entity);
        int seen = 0;
        int all = 0;
        for (double k = 0.0; k <= 1.0; k += d) {
            for (double l = 0.0; l <= 1.0; l += e) {
                for (double m = 0.0; m <= 1.0; m += f) {
                    Vec3 from = new Vec3(Mth.lerp(k, box.minX, box.maxX) + g, Mth.lerp(l, box.minY, box.maxY), Mth.lerp(m, box.minZ, box.maxZ) + h);
                    if (!nimbus$blocked(level, from, explosionVector, context)) {
                        seen++;
                    }
                    all++;
                }
            }
        }
        return (float) seen / all;
    }

    @Unique
    private static boolean nimbus$blocked(Level level, Vec3 from, Vec3 to, CollisionContext context) {
        return BlockGetter.traverseBlocks(from, to, context, (ctx, pos) -> {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos, ctx);
            return !shape.isEmpty() && shape.clip(from, to, pos) != null ? Boolean.TRUE : null;
        }, ctx -> Boolean.FALSE);
    }
}
