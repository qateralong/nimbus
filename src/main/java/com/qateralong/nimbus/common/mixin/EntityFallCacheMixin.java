package com.qateralong.nimbus.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.common.RegionStamp;
import com.qateralong.nimbus.common.SupportCache;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// A grounded entity falls into the block under it on every tick: with a purely vertical move, no colliding entities and
// no world border nearby, the result depends only on the box, the move and the blocks, so it is kept while those stay.
@Mixin(Entity.class)
public abstract class EntityFallCacheMixin {
    @Unique
    private RegionStamp nimbus$fallStamp;
    @Unique
    private boolean nimbus$fallValid;
    @Unique
    private Level nimbus$fallLevel;
    @Unique
    private AABB nimbus$fallBox;
    @Unique
    private double nimbus$fallY;
    @Unique
    private Vec3 nimbus$fallResult;

    @WrapOperation(method = "collide", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/entity/Entity;collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 nimbus$fall(Entity entity, Vec3 move, AABB box, Level level, List<VoxelShape> hits, Operation<Vec3> original) {
        if (move.x != 0.0 || move.z != 0.0 || !hits.isEmpty() || !RegionStamp.plainLevel(level)) {
            return original.call(entity, move, box, level, hits);
        }
        AABB swept = box.expandTowards(move);
        if (level.getWorldBorder().isInsideCloseToBorder(entity, swept)) {
            this.nimbus$fallValid = false;
            return original.call(entity, move, box, level, hits);
        }
        AABB last = this.nimbus$fallBox;
        if (this.nimbus$fallValid && this.nimbus$fallLevel == level && this.nimbus$fallY == move.y && last.minX == box.minX && last.minY == box.minY
                && last.minZ == box.minZ && last.maxX == box.maxX && last.maxY == box.maxY && last.maxZ == box.maxZ && this.nimbus$fallStamp.fresh(level)) {
            Vec3 kept = this.nimbus$fallResult;
            if (SupportCache.VERIFY) {
                SupportCache.checkFall(original.call(entity, move, box, level, hits), kept, entity);
            }
            return kept;
        }
        Vec3 result = original.call(entity, move, box, level, hits);
        if (this.nimbus$fallStamp == null) {
            this.nimbus$fallStamp = new RegionStamp();
        }
        this.nimbus$fallValid = this.nimbus$fallStamp.stampStatic(level, swept, new BlockPos.MutableBlockPos());
        this.nimbus$fallLevel = level;
        this.nimbus$fallBox = box;
        this.nimbus$fallY = move.y;
        this.nimbus$fallResult = result;
        return result;
    }
}
