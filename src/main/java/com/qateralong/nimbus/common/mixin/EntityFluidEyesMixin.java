package com.qateralong.nimbus.common.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.Set;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The fluid tags at eye level (zero to two of them) are cleared and refilled every tick; a small array set does that
// without hashing. Membership still goes by equals.
@Mixin(Entity.class)
public abstract class EntityFluidEyesMixin {
    @Shadow
    @Final
    @Mutable
    private Set<TagKey<Fluid>> fluidOnEyes;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nimbus$arraySet(CallbackInfo ci) {
        Set<TagKey<Fluid>> set = new ObjectArraySet<>(2);
        set.addAll(this.fluidOnEyes);
        this.fluidOnEyes = set;
    }
}
