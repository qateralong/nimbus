package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.LazyColliders;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Movement colliders gathered lazily: collideWithShapes stops reading an axis once it is blocked and skips axes without
// movement, so most of the swept box is never looked at for mobs standing or walking on flat ground.
@Mixin(Entity.class)
public abstract class EntityCollidersMixin {
    @Inject(method = "collectColliders", at = @At("HEAD"), cancellable = true)
    private static void nimbus$lazy(@Nullable Entity entity, Level level, List<VoxelShape> collisions, AABB boundingBox, CallbackInfoReturnable<List<VoxelShape>> cir) {
        WorldBorder border = level.getWorldBorder();
        VoxelShape wall = entity != null && border.isInsideCloseToBorder(entity, boundingBox) ? border.getCollisionShape() : null;
        cir.setReturnValue(new LazyColliders(collisions, wall, level.getBlockCollisions(entity, boundingBox)));
    }
}
