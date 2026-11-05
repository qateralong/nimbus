package com.qateralong.nimbus.common.mixin;

import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Heightmap.class)
public interface HeightmapAccessor {
    @Accessor("isOpaque")
    Predicate<BlockState> nimbus$opaque();

    @Invoker("setHeight")
    void nimbus$setHeight(int x, int z, int value);
}
