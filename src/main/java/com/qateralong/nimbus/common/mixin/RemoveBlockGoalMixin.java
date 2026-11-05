package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.SectionFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Zombies look for turtle eggs (and mods' goals for their block) block by block over a wide area; a section whose
// palette never held the block can't contain a target, so its positions are rejected without reading them.
@Mixin(RemoveBlockGoal.class)
public abstract class RemoveBlockGoalMixin {
    @Shadow
    @Final
    private Block blockToRemove;

    /**
     * @author qateralong
     * @reason skip sections that cannot hold the block; otherwise vanilla's check
     */
    @Overwrite
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        ChunkAccess chunk = level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, false);
        if (chunk == null) {
            return false;
        }
        if (chunk.getClass() == LevelChunk.class && !((LevelChunk) chunk).getLevel().isDebug()) {
            int index = chunk.getSectionIndex(pos.getY());
            LevelChunkSection[] sections = chunk.getSections();
            if (index >= 0 && index < sections.length && !this.nimbus$may(sections[index])) {
                return false;
            }
        }
        return chunk.getBlockState(pos).is(this.blockToRemove) && chunk.getBlockState(pos.above()).isAir() && chunk.getBlockState(pos.above(2)).isAir();
    }

    @Unique
    private boolean nimbus$may(LevelChunkSection section) {
        return section.hasOnlyAir() ? this.blockToRemove.defaultBlockState().isAir() : ((SectionFlags) section).nimbus$mayHave(this.blockToRemove);
    }
}
