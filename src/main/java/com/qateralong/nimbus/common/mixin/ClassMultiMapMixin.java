package com.qateralong.nimbus.common.mixin;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

// find() built a capturing lambda for computeIfAbsent on every call, even when the list for the class already exists.
@Mixin(ClassInstanceMultiMap.class)
public abstract class ClassMultiMapMixin<T> implements com.qateralong.nimbus.common.ClassLists<T> {
    @Shadow
    @Final
    private Map<Class<?>, List<T>> byClass;

    @Shadow
    @Final
    private Class<T> baseClass;

    @Shadow
    @Final
    private List<T> allInstances;

    /**
     * @author qateralong
     * @reason a plain lookup first; the class list is only built once, as before
     */
    @Overwrite
    @SuppressWarnings("unchecked")
    public <S> Collection<S> find(Class<S> type) {
        return Collections.unmodifiableCollection(this.nimbus$of(type));
    }

    @Override
    public List<T> nimbus$all() {
        return this.allInstances;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> List<S> nimbus$of(Class<S> type) {
        List<T> list = this.byClass.get(type);
        if (list == null) {
            if (!this.baseClass.isAssignableFrom(type)) {
                throw new IllegalArgumentException("Don't know how to search for " + type);
            }
            list = this.allInstances.stream().filter(type::isInstance).collect(Util.toMutableList());
            this.byClass.put(type, list);
        }
        return (List<S>) list;
    }
}
