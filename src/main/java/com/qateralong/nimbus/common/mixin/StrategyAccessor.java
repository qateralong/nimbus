package com.qateralong.nimbus.common.mixin;

import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PalettedContainer.Strategy.class)
public interface StrategyAccessor {
    @Invoker("calculateBitsForSerialization")
    <A> int nimbus$bits(IdMap<A> registry, int size);
}
