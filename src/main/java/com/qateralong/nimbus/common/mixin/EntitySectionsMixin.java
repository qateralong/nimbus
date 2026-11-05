package com.qateralong.nimbus.common.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla walks every stored section of each x slice the box touches, whatever their z, so a small query in a large
// world scans hundreds of sections. Small boxes look up their few section keys directly, in vanilla's key order.
@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionsMixin<T extends EntityAccess> {
    @Unique
    private static final int NIMBUS_DIRECT = 64;

    @Shadow
    @Final
    private Long2ObjectMap<EntitySection<T>> sections;

    @Inject(method = "forEachAccessibleNonEmptySection", at = @At("HEAD"), cancellable = true)
    private void nimbus$direct(AABB box, AbortableIterationConsumer<EntitySection<T>> consumer, CallbackInfo ci) {
        int minX = SectionPos.posToSectionCoord(box.minX - 2.0);
        int minY = SectionPos.posToSectionCoord(box.minY - 4.0);
        int minZ = SectionPos.posToSectionCoord(box.minZ - 2.0);
        int maxX = SectionPos.posToSectionCoord(box.maxX + 2.0);
        int maxY = SectionPos.posToSectionCoord(box.maxY + 0.0);
        int maxZ = SectionPos.posToSectionCoord(box.maxZ + 2.0);
        long count = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (count > NIMBUS_DIRECT || count <= 0) {
            return;
        }
        ci.cancel();
        // keys sort by masked z then masked y, so negative values come after non-negative ones
        int zSplit = minZ < 0 && maxZ >= 0 ? 0 : minZ;
        int ySplit = minY < 0 && maxY >= 0 ? 0 : minY;
        for (int x = minX; x <= maxX; x++) {
            for (int zPass = 0; zPass < 2; zPass++) {
                int zFrom = zPass == 0 ? zSplit : minZ;
                int zTo = zPass == 0 ? maxZ : zSplit - 1;
                if (zPass == 1 && zSplit == minZ) {
                    break;
                }
                for (int z = zFrom; z <= zTo; z++) {
                    for (int yPass = 0; yPass < 2; yPass++) {
                        int yFrom = yPass == 0 ? ySplit : minY;
                        int yTo = yPass == 0 ? maxY : ySplit - 1;
                        if (yPass == 1 && ySplit == minY) {
                            break;
                        }
                        for (int y = yFrom; y <= yTo; y++) {
                            EntitySection<T> section = this.sections.get(SectionPos.asLong(x, y, z));
                            if (section != null && !section.isEmpty() && section.getStatus().isAccessible() && consumer.accept(section).shouldAbort()) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
