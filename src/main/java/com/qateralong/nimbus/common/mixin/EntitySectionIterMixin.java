package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.ClassLists;
import java.util.List;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

// Box queries walk the section's lists by index instead of through unmodifiable iterator wrappers.
@Mixin(EntitySection.class)
public abstract class EntitySectionIterMixin<T extends EntityAccess> {
    @Shadow
    @Final
    private ClassInstanceMultiMap<T> storage;

    /**
     * @author qateralong
     * @reason indexed walk, same order and checks
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public AbortableIterationConsumer.Continuation getEntities(AABB bounds, AbortableIterationConsumer<T> consumer) {
        List<T> all = ((ClassLists<T>) (Object) this.storage).nimbus$all();
        for (int i = 0, n = all.size(); i < n; i++) {
            T entity = all.get(i);
            if (entity.getBoundingBox().intersects(bounds) && consumer.accept(entity).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }
        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    /**
     * @author qateralong
     * @reason indexed walk, same order and checks
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public <U extends T> AbortableIterationConsumer.Continuation getEntities(EntityTypeTest<T, U> test, AABB bounds, AbortableIterationConsumer<? super U> consumer) {
        List<? extends T> list = ((ClassLists<T>) (Object) this.storage).nimbus$of(test.getBaseClass());
        for (int i = 0, n = list.size(); i < n; i++) {
            T entity = list.get(i);
            U cast = test.tryCast(entity);
            if (cast != null && entity.getBoundingBox().intersects(bounds) && consumer.accept(cast).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }
        return AbortableIterationConsumer.Continuation.CONTINUE;
    }
}
