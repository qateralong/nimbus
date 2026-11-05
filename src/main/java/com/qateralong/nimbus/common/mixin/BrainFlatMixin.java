package com.qateralong.nimbus.common.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The behaviour tree (priority -> activity -> behaviours) flattened into arrays in the same iteration order, rebuilt
// whenever behaviours are added or removed; saves three levels of map iteration per mob per tick.
@Mixin(Brain.class)
public abstract class BrainFlatMixin<E extends LivingEntity> {
    @Shadow
    @Final
    private Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority;

    @Shadow
    @Final
    private Set<Activity> activeActivities;

    @Unique
    private Activity[] nimbus$groupActivity;
    @Unique
    private BehaviorControl<? super E>[][] nimbus$groups;
    @Unique
    private BehaviorControl<? super E>[] nimbus$all;
    @Unique
    private BehaviorControl<? super E>[] nimbus$running;

    @Inject(method = {"addActivityAndRemoveMemoriesWhenStopped", "removeAllBehaviors"}, at = @At("RETURN"))
    private void nimbus$changed(CallbackInfo ci) {
        this.nimbus$groups = null;
    }

    @Unique
    @SuppressWarnings("unchecked")
    private void nimbus$build() {
        List<Activity> activities = new ArrayList<>();
        List<BehaviorControl<? super E>[]> groups = new ArrayList<>();
        List<BehaviorControl<? super E>> all = new ArrayList<>();
        for (Map<Activity, Set<BehaviorControl<? super E>>> map : this.availableBehaviorsByPriority.values()) {
            for (Map.Entry<Activity, Set<BehaviorControl<? super E>>> entry : map.entrySet()) {
                BehaviorControl<? super E>[] group = entry.getValue().toArray(new BehaviorControl[0]);
                activities.add(entry.getKey());
                groups.add(group);
                java.util.Collections.addAll(all, group);
            }
        }
        this.nimbus$groupActivity = activities.toArray(new Activity[0]);
        this.nimbus$all = all.toArray(new BehaviorControl[0]);
        this.nimbus$running = new BehaviorControl[this.nimbus$all.length];
        this.nimbus$groups = groups.toArray(new BehaviorControl[0][]);
    }

    /**
     * @author qateralong
     * @reason flattened arrays instead of nested map iteration, same order
     */
    @Overwrite
    private void startEachNonRunningBehavior(ServerLevel level, E entity) {
        long time = level.getGameTime();
        if (this.nimbus$groups == null) {
            this.nimbus$build();
        }
        BehaviorControl<? super E>[][] groups = this.nimbus$groups;
        Activity[] activities = this.nimbus$groupActivity;
        for (int g = 0; g < groups.length; g++) {
            if (this.activeActivities.contains(activities[g])) {
                for (BehaviorControl<? super E> behavior : groups[g]) {
                    if (behavior.getStatus() == Behavior.Status.STOPPED) {
                        behavior.tryStart(level, entity, time);
                    }
                }
            }
        }
    }

    /**
     * @author qateralong
     * @reason running behaviours collected into a reused array; still collected before any of them ticks
     */
    @Overwrite
    private void tickEachRunningBehavior(ServerLevel level, E entity) {
        long time = level.getGameTime();
        if (this.nimbus$groups == null) {
            this.nimbus$build();
        }
        BehaviorControl<? super E>[] all = this.nimbus$all;
        BehaviorControl<? super E>[] running = this.nimbus$running;
        int count = 0;
        for (BehaviorControl<? super E> behavior : all) {
            if (behavior.getStatus() == Behavior.Status.RUNNING) {
                running[count++] = behavior;
            }
        }
        for (int i = 0; i < count; i++) {
            BehaviorControl<? super E> behavior = running[i];
            running[i] = null;
            behavior.tickOrStop(level, entity, time);
        }
    }
}
