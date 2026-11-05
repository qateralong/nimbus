package com.qateralong.nimbus.common.mixin;

import java.util.Map;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Brain.class)
public interface BrainMemoriesAccessor {
    @Accessor("memories")
    Map<?, ?> nimbus$memories();
}
