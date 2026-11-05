package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RenderSystem.class, remap = false)
public interface RenderSystemLightAccessor {
    @Accessor("shaderLightDirections")
    static Vector3f[] nimbus$lights() {
        throw new AssertionError();
    }
}
