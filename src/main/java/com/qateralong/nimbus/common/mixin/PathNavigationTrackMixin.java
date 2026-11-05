package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.Navigations;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// every place vanilla assigns the path field
@Mixin(PathNavigation.class)
public abstract class PathNavigationTrackMixin implements Navigations.Navigation {
    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected Path path;

    @Override
    public Mob nimbus$mob() {
        return this.mob;
    }

    @Override
    public Level nimbus$level() {
        return this.level;
    }

    @Override
    public boolean nimbus$hasPath() {
        return this.path != null;
    }

    @Inject(method = {"recomputePath", "stop"}, at = @At("RETURN"))
    private void nimbus$changed(CallbackInfo ci) {
        Navigations.update((PathNavigation) (Object) this);
    }

    @Inject(method = "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z", at = @At("RETURN"))
    private void nimbus$moved(Path path, double speed, CallbackInfoReturnable<Boolean> cir) {
        Navigations.update((PathNavigation) (Object) this);
    }
}
