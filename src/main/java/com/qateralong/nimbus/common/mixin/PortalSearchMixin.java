package com.qateralong.nimbus.common.mixin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

// Vanilla reads the block (loading its chunk if needed) under every portal POI in range and then takes the nearest.
// Sorting the candidates with the same comparator and checking them in order stops at that same nearest one.
@Mixin(PortalForcer.class)
public abstract class PortalSearchMixin {
    @Shadow
    @Final
    private ServerLevel level;

    /**
     * @author qateralong
     * @reason nearest first, block checks stop at the first valid portal; same result as filter then min
     */
    @Overwrite
    public Optional<BlockPos> findClosestPortalPosition(BlockPos exitPos, boolean isNether, WorldBorder worldBorder) {
        PoiManager poiManager = this.level.getPoiManager();
        int radius = isNether ? 16 : 128;
        poiManager.ensureLoadedAndValid(this.level, exitPos, radius);
        List<BlockPos> candidates = new ArrayList<>();
        poiManager.getInSquare(holder -> holder.is(PoiTypes.NETHER_PORTAL), exitPos, radius, PoiManager.Occupancy.ANY).forEach(record -> {
            BlockPos pos = record.getPos();
            if (worldBorder.isWithinBounds(pos)) {
                candidates.add(pos);
            }
        });
        candidates.sort(Comparator.<BlockPos>comparingDouble(pos -> pos.distSqr(exitPos)).thenComparingInt(Vec3i::getY));
        for (BlockPos pos : candidates) {
            if (this.level.getBlockState(pos).hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }
}
