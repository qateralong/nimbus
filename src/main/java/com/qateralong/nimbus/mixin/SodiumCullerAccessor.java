package com.qateralong.nimbus.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(value = OcclusionCuller.class, remap = false)
public interface SodiumCullerAccessor {
    @Invoker(value = "isWithinRenderDistance", remap = false)
    static boolean nimbus$withinDistance(CameraTransform camera, RenderSection section, float distance) {
        throw new AssertionError();
    }
}
