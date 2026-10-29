package com.qateralong.nimbus.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public final class EntityOrder {
    private static final Reference2ObjectOpenHashMap<EntityType<?>, ObjectArrayList<Entity>> groups = new Reference2ObjectOpenHashMap<>();
    private static final ObjectArrayList<ObjectArrayList<Entity>> used = new ObjectArrayList<>();
    private static final ObjectArrayList<Entity> result = new ObjectArrayList<>();

    private EntityOrder() {
    }

    public static Iterable<Entity> group(Iterable<Entity> entities) {
        for (int i = 0; i < used.size(); i++) {
            used.get(i).clear();
        }
        used.clear();
        result.clear();
        int count = 0;
        for (Entity entity : entities) {
            ObjectArrayList<Entity> list = groups.get(entity.getType());
            if (list == null) {
                list = new ObjectArrayList<>();
                groups.put(entity.getType(), list);
            }
            if (list.isEmpty()) {
                used.add(list);
            }
            list.add(entity);
            count++;
        }
        if (used.size() <= 1) {
            return entities;
        }
        result.ensureCapacity(count);
        for (int i = 0; i < used.size(); i++) {
            result.addAll(used.get(i));
        }
        if (groups.size() > 256) {
            groups.clear();
        }
        return result;
    }
}
