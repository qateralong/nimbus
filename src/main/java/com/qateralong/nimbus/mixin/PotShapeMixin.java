package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Chests;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DecoratedPotBlock.class)
public abstract class PotShapeMixin extends BaseEntityBlock {
    protected PotShapeMixin(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return Chests.potsEnabled() ? RenderShape.MODEL : super.getRenderShape(state);
    }
}
