package com.qateralong.nimbus.common.mixin;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// passenger walks without streams; same depth-first order as vanilla's flatMap
@Mixin(Entity.class)
public abstract class EntityPassengersMixin {
    @Shadow
    private ImmutableList<Entity> passengers;

    /**
     * @author qateralong
     * @reason recursion instead of nested streams on every tracker update
     */
    @Overwrite
    public Iterable<Entity> getIndirectPassengers() {
        if (this.passengers.isEmpty()) {
            return ImmutableList.of();
        }
        List<Entity> out = new ArrayList<>();
        nimbus$collect((Entity) (Object) this, out);
        return out;
    }

    /**
     * @author qateralong
     * @reason recursion instead of nested streams
     */
    @Overwrite
    public int countPlayerPassengers() {
        return this.passengers.isEmpty() ? 0 : nimbus$players((Entity) (Object) this);
    }

    @Unique
    private static void nimbus$collect(Entity vehicle, List<Entity> out) {
        for (Entity passenger : vehicle.getPassengers()) {
            out.add(passenger);
            nimbus$collect(passenger, out);
        }
    }

    @Unique
    private static int nimbus$players(Entity vehicle) {
        int count = 0;
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player) {
                count++;
            }
            count += nimbus$players(passenger);
        }
        return count;
    }
}
