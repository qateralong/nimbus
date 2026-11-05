package com.qateralong.nimbus.common.mixin;

import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelEntityGetterAdapter.class)
public interface LevelEntityGetterAccessor {
    @Accessor("sectionStorage")
    EntitySectionStorage<?> nimbus$sections();
}
