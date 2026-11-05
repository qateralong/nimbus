package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.SectionFlags;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Twice a tick every entity scans the blocks it touches for water and lava; when none of the sections there can hold a
// fluid the answer is known: no fluid, height 0, no push.
@Mixin(Entity.class)
public abstract class EntityDryMixin {
    @Shadow
    @Final
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract boolean touchingUnloadedChunk();

    @Inject(method = "updateFluidHeightAndDoFluidPushing", at = @At("HEAD"), cancellable = true)
    private void nimbus$dry(TagKey<Fluid> fluidTag, double motionScale, CallbackInfoReturnable<Boolean> cir) {
        AABB box = this.getBoundingBox().deflate(0.001);
        int x0 = Mth.floor(box.minX);
        int x1 = Mth.ceil(box.maxX) - 1;
        int y0 = Mth.floor(box.minY);
        int y1 = Mth.ceil(box.maxY) - 1;
        int z0 = Mth.floor(box.minZ);
        int z1 = Mth.ceil(box.maxZ) - 1;
        if (x1 < x0 || y1 < y0 || z1 < z0) {
            return;
        }
        Level level = this.level();
        if (level.isDebug()) {
            return;
        }
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                if (!(level.getChunkSource().getChunkNow(cx, cz) instanceof LevelChunk chunk) || chunk.getClass() != LevelChunk.class) {
                    return;
                }
                LevelChunkSection[] sections = chunk.getSections();
                int from = Math.max(chunk.getSectionIndex(y0), 0);
                int to = Math.min(chunk.getSectionIndex(y1), sections.length - 1);
                for (int i = from; i <= to; i++) {
                    LevelChunkSection section = sections[i];
                    if (!section.hasOnlyAir() && (((SectionFlags) section).nimbus$flags() & SectionFlags.FLUID) != 0) {
                        return;
                    }
                }
            }
        }
        if (!this.touchingUnloadedChunk()) {
            this.fluidHeight.put(fluidTag, 0.0);
        }
        cir.setReturnValue(false);
    }
}
