package com.qateralong.nimbus.common.mixin;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Per-tick work of every living entity that has a cheap exact answer most of the time.
@Mixin(LivingEntity.class)
public abstract class LivingTickMixin extends Entity {
    @Shadow
    public boolean swinging;

    @Shadow
    public int swingTime;

    @Shadow
    public float attackAnim;

    @Shadow
    @Final
    private Map<Holder<MobEffect>, MobEffectInstance> activeEffects;

    protected LivingTickMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    // not swinging: vanilla ends with swingTime 0 and attackAnim 0 / duration, which is 0 unless an effect changes the duration
    @Inject(method = "updateSwingTime", at = @At("HEAD"), cancellable = true)
    private void nimbus$idleSwing(CallbackInfo ci) {
        if (!this.swinging && this.activeEffects.isEmpty()) {
            this.swingTime = 0;
            this.attackAnim = 0.0F;
            ci.cancel();
        }
    }

    // without the gliding flag vanilla only writes the same false back
    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void nimbus$notGliding(CallbackInfo ci) {
        if (!this.getSharedFlag(7)) {
            ci.cancel();
        }
    }

    // the slowdown is only added for frozen ticks; the block lookup before that check has no effect of its own
    @Inject(method = "tryAddFrost", at = @At("HEAD"), cancellable = true)
    private void nimbus$notFrozen(CallbackInfo ci) {
        if (this.getTicksFrozen() <= 0) {
            ci.cancel();
        }
    }
}
