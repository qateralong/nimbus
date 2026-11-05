package com.qateralong.nimbus.common.mixin;

import com.google.common.collect.ImmutableList;
import com.qateralong.nimbus.common.EntityKinds;
import com.qateralong.nimbus.common.SectionKinds;
import java.util.List;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Entity-to-entity collision boxes: for an entity with the default canCollideWith only entities whose class can be
// collided with matter, so sections without any are skipped instead of testing every mob in them. Same sections,
// same order, same checks as vanilla.
@Mixin(Level.class)
public abstract class LevelEntityCollisionsMixin implements EntityGetter {
    @Override
    @SuppressWarnings("unchecked")
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB collisionBox) {
        if (collisionBox.getSize() < 1.0E-7) {
            return List.of();
        }
        EntitySectionStorage<Entity> storage = entity == null || EntityKinds.customCollider(entity) ? null : EntityKinds.sections((Level) (Object) this);
        if (storage == null) {
            return this.nimbus$vanilla(entity, collisionBox);
        }
        AABB area = collisionBox.inflate(1.0E-7);
        ImmutableList.Builder<VoxelShape>[] found = new ImmutableList.Builder[1];
        storage.forEachAccessibleNonEmptySection(area, section -> {
            if (((SectionKinds) section).nimbus$count(EntityKinds.COLLIDABLE) == 0) {
                return AbortableIterationConsumer.Continuation.CONTINUE;
            }
            return section.getEntities(area, other -> {
                if (other != entity && !other.isSpectator() && entity.canCollideWith(other)) {
                    if (found[0] == null) {
                        found[0] = ImmutableList.builder();
                    }
                    found[0].add(Shapes.create(other.getBoundingBox()));
                }
                return AbortableIterationConsumer.Continuation.CONTINUE;
            });
        });
        return found[0] == null ? List.of() : found[0].build();
    }

    @Unique
    private List<VoxelShape> nimbus$vanilla(@Nullable Entity entity, AABB collisionBox) {
        java.util.function.Predicate<Entity> predicate = entity == null ? EntitySelector.CAN_BE_COLLIDED_WITH : EntitySelector.NO_SPECTATORS.and(entity::canCollideWith);
        List<Entity> list = this.getEntities(entity, collisionBox.inflate(1.0E-7), predicate);
        if (list.isEmpty()) {
            return List.of();
        }
        ImmutableList.Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(list.size());
        for (Entity other : list) {
            builder.add(Shapes.create(other.getBoundingBox()));
        }
        return builder.build();
    }
}
