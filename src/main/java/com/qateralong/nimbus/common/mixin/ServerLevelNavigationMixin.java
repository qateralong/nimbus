package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.Navigations;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelNavigationMixin implements Navigations.Level {
    @Unique
    private final ReferenceOpenHashSet<PathNavigation> nimbus$navigations = new ReferenceOpenHashSet<>();

    @Unique
    private final ReferenceOpenHashSet<Mob> nimbus$askMobs = new ReferenceOpenHashSet<>();

    @Override
    public ReferenceOpenHashSet<PathNavigation> nimbus$navigations() {
        return this.nimbus$navigations;
    }

    @Override
    public ReferenceOpenHashSet<Mob> nimbus$askMobs() {
        return this.nimbus$askMobs;
    }

    @Redirect(method = "sendBlockUpdated", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;navigatingMobs:Ljava/util/Set;"))
    private Set<Mob> nimbus$withPath(ServerLevel level) {
        return Navigations.mobs(this, ((ServerLevelAccessor) level).nimbus$navigatingMobs());
    }
}
