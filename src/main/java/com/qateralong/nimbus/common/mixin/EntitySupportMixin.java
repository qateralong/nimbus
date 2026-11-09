package com.qateralong.nimbus.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.common.RegionStamp;
import com.qateralong.nimbus.common.SupportCache;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// Every grounded entity looks for the block under its feet on each move, standing still included.
@Mixin(Entity.class)
public abstract class EntitySupportMixin {
    @Unique
    private SupportCache nimbus$support;

    @WrapOperation(method = "checkSupportingBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;findSupportingBlock(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/Optional;"))
    private Optional<BlockPos> nimbus$support(Level level, Entity entity, AABB box, Operation<Optional<BlockPos>> original) {
        Class<?> type = level.getClass();
        if (type != net.minecraft.server.level.ServerLevel.class && !RegionStamp.CLIENT_LEVEL.equals(type.getName())) {
            return original.call(level, entity, box);
        }
        SupportCache cache = this.nimbus$support;
        if (cache == null) {
            cache = this.nimbus$support = new SupportCache();
        }
        Optional<BlockPos> result = cache.find(level, entity, box);
        if (SupportCache.VERIFY) {
            Optional<BlockPos> expected = original.call(level, entity, box);
            SupportCache.check(expected, result, entity);
        }
        return result;
    }
}
