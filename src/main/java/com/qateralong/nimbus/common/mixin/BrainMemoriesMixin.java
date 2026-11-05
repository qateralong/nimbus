package com.qateralong.nimbus.common.mixin;

import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Memory and activity lookups happen dozens of times per mob per tick; the keys are singletons, so identity collections
// do them without hashing. (The hash sets' order already followed identity hashes, so nothing relied on it.)
@Mixin(Brain.class)
public abstract class BrainMemoriesMixin {
    @Shadow
    @Final
    @Mutable
    private Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memories;

    @Shadow
    @Final
    @Mutable
    private Set<Activity> activeActivities;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nimbus$identity(CallbackInfo ci) {
        this.memories = new com.qateralong.nimbus.common.PresenceMap<>(this.memories);
        // a handful of activities at most: a linear identity scan beats hashing
        this.activeActivities = new ReferenceArraySet<>(this.activeActivities);
    }
}
