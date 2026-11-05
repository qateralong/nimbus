package com.qateralong.nimbus.common;

import com.qateralong.nimbus.common.mixin.LevelEntitiesAccessor;
import com.qateralong.nimbus.common.mixin.LevelEntityGetterAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

// Entity kinds counted per entity section, so queries that only care about them skip sections without any.
// Class-based: collidable = overrides canBeCollidedWith, container = implements Container, item = ItemEntity; mod entities
// included.
public final class EntityKinds {
    public static final int COLLIDABLE = 0;
    public static final int CONTAINER = 1;
    public static final int ITEM = 2;
    // bumped whenever an item or container entity enters an entity section anywhere
    public static volatile int arrivals;
    private static final String BE_COLLIDED;
    private static final String COLLIDE;

    static {
        MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();
        BE_COLLIDED = mappings.mapMethodName("intermediary", "net.minecraft.class_1297", "method_30948", "()Z");
        COLLIDE = mappings.mapMethodName("intermediary", "net.minecraft.class_1297", "method_30949", "(Lnet/minecraft/class_1297;)Z");
    }

    // bit 0 collidable, bit 1 container, bit 2 item
    private static final ClassValue<Integer> KINDS = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            int kinds = 0;
            if (overrides(type, BE_COLLIDED)) {
                kinds |= 1 << COLLIDABLE;
            }
            if (Container.class.isAssignableFrom(type)) {
                kinds |= 1 << CONTAINER;
            }
            if (net.minecraft.world.entity.item.ItemEntity.class.isAssignableFrom(type)) {
                kinds |= 1 << ITEM;
            }
            return kinds;
        }
    };
    private static final ClassValue<Boolean> CUSTOM = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return overrides(type, COLLIDE, Entity.class);
        }
    };

    private EntityKinds() {
    }

    public static int of(Object entity) {
        return entity instanceof Entity ? KINDS.get(entity.getClass()) : 0;
    }

    // decides collisions itself (boats, minecarts): every nearby entity may matter
    public static boolean customCollider(Entity entity) {
        return CUSTOM.get(entity.getClass());
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static EntitySectionStorage<Entity> sections(Level level) {
        return ((LevelEntitiesAccessor) level).nimbus$entities() instanceof LevelEntityGetterAccessor adapter
                ? (EntitySectionStorage<Entity>) adapter.nimbus$sections() : null;
    }

    // false only when no section a vanilla query over box would visit holds an entity of this kind
    public static boolean maybeAny(Level level, AABB box, int kind) {
        return maybeAnyOf(level, box, 1 << kind);
    }

    // same for any of the kinds in the mask (1 << kind)
    public static boolean maybeAnyOf(Level level, AABB box, int mask) {
        EntitySectionStorage<Entity> storage = sections(level);
        if (storage == null) {
            return true;
        }
        boolean[] found = new boolean[1];
        storage.forEachAccessibleNonEmptySection(box, section -> {
            SectionKinds kinds = (SectionKinds) section;
            if ((mask & 1) != 0 && kinds.nimbus$count(COLLIDABLE) > 0 || (mask & 2) != 0 && kinds.nimbus$count(CONTAINER) > 0
                    || (mask & 4) != 0 && kinds.nimbus$count(ITEM) > 0) {
                found[0] = true;
                return AbortableIterationConsumer.Continuation.ABORT;
            }
            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
        return found[0];
    }

    private static boolean overrides(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> c = type; c != null && c != Entity.class; c = c.getSuperclass()) {
            try {
                c.getDeclaredMethod(name, parameters);
                return true;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                return true;
            }
        }
        return false;
    }
}
