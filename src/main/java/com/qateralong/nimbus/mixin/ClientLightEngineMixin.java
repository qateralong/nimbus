package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.light.ClientLight;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientChunkCache.class)
public abstract class ClientLightEngineMixin {
    @Unique
    private static final boolean NIMBUS_FOREIGN = FabricLoader.getInstance().isModLoaded("scalablelux")
            || FabricLoader.getInstance().isModLoaded("starlight") || FabricLoader.getInstance().isModLoaded("moonrise");

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/chunk/LightChunkGetter;ZZ)Lnet/minecraft/world/level/lighting/LevelLightEngine;"))
    private LevelLightEngine nimbus$engine(LightChunkGetter chunks, boolean block, boolean sky) {
        NimbusConfig config = NimbusConfig.get();
        if (config.enabled && config.lightEngine && !NIMBUS_FOREIGN) {
            return new ClientLight(chunks, sky);
        }
        return new LevelLightEngine(chunks, block, sky);
    }
}
