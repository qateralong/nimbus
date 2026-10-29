package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.LightHolder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityLightHolderMixin implements LightHolder {
    @Unique
    private int nimbus$lightTick;

    @Unique
    private int nimbus$light;

    @Override
    public int nimbus$lightTick() {
        return this.nimbus$lightTick;
    }

    @Override
    public int nimbus$light() {
        return this.nimbus$light;
    }

    @Override
    public void nimbus$setLight(int tick, int light) {
        this.nimbus$lightTick = tick;
        this.nimbus$light = light;
    }
}
