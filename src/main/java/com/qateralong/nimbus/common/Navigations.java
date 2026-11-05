package com.qateralong.nimbus.common;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

// Navigations that currently hold a path. Only they can answer yes to "recompute for this block change?", so block
// updates ask them instead of every mob in the level.
public final class Navigations {
    public interface Level {
        ReferenceOpenHashSet<PathNavigation> nimbus$navigations();

        ReferenceOpenHashSet<Mob> nimbus$askMobs();
    }

    public interface Navigation {
        Mob nimbus$mob();

        net.minecraft.world.level.Level nimbus$level();

        boolean nimbus$hasPath();
    }

    private Navigations() {
    }

    public static void update(PathNavigation navigation) {
        Navigation nav = (Navigation) navigation;
        if (!(nav.nimbus$level() instanceof Level level)) {
            return;
        }
        if (nav.nimbus$hasPath()) {
            level.nimbus$navigations().add(navigation);
        } else {
            level.nimbus$navigations().remove(navigation);
        }
    }

    // the vanilla loop reads mob.getNavigation(), so a mob counts when its current navigation holds a path
    public static Set<Mob> mobs(Level level, Set<Mob> tracked) {
        ReferenceOpenHashSet<Mob> mobs = level.nimbus$askMobs();
        mobs.clear();
        var it = level.nimbus$navigations().iterator();
        while (it.hasNext()) {
            PathNavigation navigation = it.next();
            Navigation nav = (Navigation) navigation;
            Mob mob = nav.nimbus$mob();
            if (mob.isRemoved() || !nav.nimbus$hasPath()) {
                it.remove();
                continue;
            }
            if (mob.getNavigation() == navigation && tracked.contains(mob)) {
                mobs.add(mob);
            }
        }
        return mobs;
    }
}
