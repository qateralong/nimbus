package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.IdleTicker;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// An empty campfire does nothing when lit and only cools leftover progress when unlit.
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireIdleMixin implements IdleTicker {
    @Shadow
    @Final
    private NonNullList<ItemStack> items;
    @Shadow
    @Final
    private int[] cookingProgress;

    @Override
    public boolean nimbus$idleTick() {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        // the client ticker makes smoke even over an empty campfire
        if (level == null || level.isClientSide || self.getClass() != CampfireBlockEntity.class) {
            return false;
        }
        boolean cooling = false;
        for (int i = 0; i < this.items.size(); i++) {
            if (!this.items.get(i).isEmpty()) {
                return false;
            }
            cooling |= this.cookingProgress[i] > 0;
        }
        if (!cooling) {
            return true;
        }
        // the ticker follows the chunk's state, which the block entity's cached one can lag
        BlockState state = level.getBlockState(self.getBlockPos());
        return state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
    }
}
