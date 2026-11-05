package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.PresenceMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A stopped behaviour checks its entry memories every tick; the answer only depends on which memories are present,
// so it is reused until the brain's presence count changes.
@Mixin(Behavior.class)
public abstract class BehaviorMemoryCacheMixin<E extends LivingEntity> {
    @Unique
    private static final boolean NIMBUS_VERIFY = Boolean.getBoolean("nimbus.verifyMemories");
    @Unique
    private static long nimbus$checked;
    @Unique
    private static long nimbus$wrong;
    @Unique
    private int nimbus$expect = -1;
    @Unique
    private Object nimbus$map;
    @Unique
    private int nimbus$version;
    @Unique
    private boolean nimbus$result;

    @Inject(method = "hasRequiredMemories", at = @At("HEAD"), cancellable = true)
    private void nimbus$cached(E owner, CallbackInfoReturnable<Boolean> cir) {
        Brain<?> brain = owner.getBrain();
        if (((BrainMemoriesAccessor) brain).nimbus$memories() instanceof PresenceMap<?, ?> map && map == this.nimbus$map && map.version == this.nimbus$version) {
            if (NIMBUS_VERIFY) {
                this.nimbus$expect = this.nimbus$result ? 1 : 0;
                return;
            }
            cir.setReturnValue(this.nimbus$result);
        }
    }

    @Inject(method = "hasRequiredMemories", at = @At("RETURN"))
    private void nimbus$remember(E owner, CallbackInfoReturnable<Boolean> cir) {
        if (this.nimbus$expect >= 0) {
            nimbus$checked++;
            if ((this.nimbus$expect == 1) != cir.getReturnValueZ()) {
                nimbus$wrong++;
            }
            this.nimbus$expect = -1;
            if ((nimbus$checked & 0xFFFFF) == 0) {
                com.qateralong.nimbus.common.NimbusCommon.LOGGER.info("Nimbus memory cache check: {} hits, {} wrong", nimbus$checked, nimbus$wrong);
            }
        }
        if (((BrainMemoriesAccessor) owner.getBrain()).nimbus$memories() instanceof PresenceMap<?, ?> map) {
            this.nimbus$map = map;
            this.nimbus$version = map.version;
            this.nimbus$result = cir.getReturnValueZ();
        }
    }
}
