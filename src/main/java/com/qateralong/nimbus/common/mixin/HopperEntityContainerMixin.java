package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.EntityKinds;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Hoppers, droppers and hopper minecarts look for container entities (chest minecarts...) on every attempt; with none
// in the sections around, the scan over every other entity there is skipped. Same list otherwise, so the random pick
// is unchanged.
@Mixin(HopperBlockEntity.class)
public abstract class HopperEntityContainerMixin {
    @Redirect(method = "getEntityContainer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<Entity> nimbus$containers(Level level, Entity except, AABB box, Predicate<? super Entity> predicate) {
        return EntityKinds.maybeAny(level, box, EntityKinds.CONTAINER) ? level.getEntities(except, box, predicate) : List.of();
    }
}
