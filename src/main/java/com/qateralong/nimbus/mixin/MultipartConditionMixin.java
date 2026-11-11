package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.MultipartShare;
import java.util.function.Predicate;
import net.minecraft.client.renderer.block.model.multipart.AndCondition;
import net.minecraft.client.renderer.block.model.multipart.Condition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = AndCondition.class, priority = 900)
public abstract class MultipartConditionMixin {
    @Shadow
    @Final
    private Iterable<? extends Condition> conditions;

    /**
     * @author qateralong
     * @reason share equal combined predicates, loop instead of streams
     */
    @Overwrite
    public Predicate<BlockState> getPredicate(StateDefinition<Block, BlockState> definition) {
        return MultipartShare.all(this.conditions, definition);
    }
}
