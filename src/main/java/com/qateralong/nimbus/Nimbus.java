package com.qateralong.nimbus;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Nimbus implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("nimbus");
    public static int ticks = 1;
    public static int frames;
    private static KeyMapping toggle;
    private static KeyMapping settings;

    @Override
    public void onInitializeClient() {
        Startup.reloadBegin();
        NimbusConfig.get();
        if (Compat.SMOOTH_GUI) {
            LOGGER.info("Nimbus: smooth scrolling mod found, HUD cache, GUI batching and item icon cache are off");
        }
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("enhancedblockentities")) {
            net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("nimbus").ifPresent(container -> Chests.available =
                    net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("nimbus", "chests"), container,
                            Component.literal("Nimbus: сундуки"), net.fabricmc.fabric.api.resource.ResourcePackActivationType.ALWAYS_ENABLED));
            net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
                net.minecraft.client.resources.model.BakedModel entityBlock = com.qateralong.nimbus.render.EntityBlocks.bake(context.topLevelId(), model, context.textureGetter());
                if (entityBlock != null) {
                    return entityBlock;
                }
                var id = context.resourceId();
                return id != null && id.getNamespace().equals("nimbus") && id.getPath().startsWith("block/") && id.getPath().contains("chest_")
                        && !id.getPath().contains("template") ? new ChestModel(model) : model;
            }));
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Chests.reset());
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(net.minecraft.world.level.block.Blocks.SHULKER_BOX,
                    net.minecraft.client.renderer.RenderType.cutout());
            for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                if (block instanceof net.minecraft.world.level.block.SignBlock
                        && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")) {
                    net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(block, net.minecraft.client.renderer.RenderType.cutout());
                }
            }
            for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
                net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                        net.minecraft.world.level.block.ShulkerBoxBlock.getBlockByColor(color), net.minecraft.client.renderer.RenderType.cutout());
            }
        }
        com.qateralong.nimbus.api.NimbusOptionsApi.builtin(NimbusPages::register);
        toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nimbus.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.nimbus"));
        settings = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nimbus.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.nimbus"));
        BugReport.key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nimbus.bug", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.nimbus"));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                Startup.titleScreen();
            }
            if (screen instanceof OptionsScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("Nimbus"), b -> client.setScreen(new NimbusScreen(screen)))
                        .bounds(width - 84, 6, 78, 20).build());
            }
        });
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, delta) -> FpsOverlay.render(graphics));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BugReport.joined(client));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client -> com.qateralong.nimbus.render.Terrain.init());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            BiomeCache.tick();
            Chests.tick();
            while (toggle.consumeClick()) {
                NimbusConfig config = NimbusConfig.get();
                config.enabled = !config.enabled;
                config.save();
                com.qateralong.nimbus.render.Terrain.toggled();
                if (client.player != null) {
                    client.player.displayClientMessage(Component.translatable(config.enabled ? "nimbus.on" : "nimbus.off"), true);
                }
            }
            while (settings.consumeClick()) {
                client.setScreen(new NimbusScreen(client.screen));
            }
            if (client.level != null && client.level.getLightEngine() instanceof com.qateralong.nimbus.light.ClientLight light) {
                var camera = client.gameRenderer.getMainCamera().getPosition();
                light.healAround(camera.x, camera.y, camera.z);
            }
            Cull.adapt(client.getFps());
            Idle.tick(client);
            Bench.tick(client);
        });
    }
}
