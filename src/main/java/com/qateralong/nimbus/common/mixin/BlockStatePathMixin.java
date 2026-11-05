package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.PathCache;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStatePathMixin implements PathCache.State {
    @Unique
    private byte nimbus$pathType;

    @Unique
    private int nimbus$pathGeneration;

    @Override
    public byte nimbus$pathType() {
        return this.nimbus$pathType;
    }

    @Override
    public int nimbus$pathGeneration() {
        return this.nimbus$pathGeneration;
    }

    @Override
    public void nimbus$setPathType(byte type, int generation) {
        this.nimbus$pathGeneration = generation;
        this.nimbus$pathType = type;
    }
}
