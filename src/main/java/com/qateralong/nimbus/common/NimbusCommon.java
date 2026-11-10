package com.qateralong.nimbus.common;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Shared entrypoint, also on dedicated servers: the base for the server half of Nimbus (3.0).
public final class NimbusCommon implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("nimbus");

    @Override
    public void onInitialize() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lithium")) {
            LOGGER.info("Nimbus shared optimizations: Lithium is installed, only fastStates, voxelShapeCache and faceShapes stay on");
        } else {
            LOGGER.info("Nimbus shared optimizations: {} of {} on", CommonConfig.active(), CommonConfig.OPTIONS.size());
        }
        net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            PathCache.tagsChanged();
            RecipeEpoch.value++;
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> RecipeEpoch.value++);
    }
}
