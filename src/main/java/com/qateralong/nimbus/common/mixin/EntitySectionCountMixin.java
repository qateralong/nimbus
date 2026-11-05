package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.EntityKinds;
import com.qateralong.nimbus.common.SectionKinds;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySection.class)
public abstract class EntitySectionCountMixin<T extends EntityAccess> implements SectionKinds {
    @Unique
    private int nimbus$collidables;
    @Unique
    private int nimbus$containers;
    @Unique
    private int nimbus$items;

    @Inject(method = "add", at = @At("RETURN"))
    private void nimbus$added(T entity, CallbackInfo ci) {
        int kinds = EntityKinds.of(entity);
        if (kinds != 0) {
            this.nimbus$collidables += kinds & 1;
            this.nimbus$containers += kinds >> 1 & 1;
            this.nimbus$items += kinds >> 2 & 1;
            if ((kinds & 6) != 0) {
                EntityKinds.arrivals++;
            }
        }
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void nimbus$removed(T entity, CallbackInfoReturnable<Boolean> cir) {
        int kinds = EntityKinds.of(entity);
        if (kinds != 0 && cir.getReturnValueZ()) {
            this.nimbus$collidables -= kinds & 1;
            this.nimbus$containers -= kinds >> 1 & 1;
            this.nimbus$items -= kinds >> 2 & 1;
        }
    }

    @Override
    public int nimbus$count(int kind) {
        return switch (kind) {
            case EntityKinds.COLLIDABLE -> this.nimbus$collidables;
            case EntityKinds.CONTAINER -> this.nimbus$containers;
            default -> this.nimbus$items;
        };
    }
}
