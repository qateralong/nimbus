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
    private static KeyMapping shaderReload;
    private static KeyMapping shaderToggle;
    private static KeyMapping shaderScreen;

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
                            Component.translatable("nimbus.pack.chests"), net.fabricmc.fabric.api.resource.ResourcePackActivationType.ALWAYS_ENABLED));
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
        // the same keys as Iris; left unbound when Iris itself is installed
        boolean iris = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris");
        shaderReload = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nimbus.shader_reload", InputConstants.Type.KEYSYM, iris ? GLFW.GLFW_KEY_UNKNOWN : GLFW.GLFW_KEY_R, "key.categories.nimbus"));
        shaderToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nimbus.shader_toggle", InputConstants.Type.KEYSYM, iris ? GLFW.GLFW_KEY_UNKNOWN : GLFW.GLFW_KEY_K, "key.categories.nimbus"));
        shaderScreen = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nimbus.shader_screen", InputConstants.Type.KEYSYM, iris ? GLFW.GLFW_KEY_UNKNOWN : GLFW.GLFW_KEY_O, "key.categories.nimbus"));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                Startup.titleScreen();
            }
        });
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, delta) -> FpsOverlay.render(graphics));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> BugReport.joined(client));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            com.qateralong.nimbus.render.Terrain.init();
            com.qateralong.nimbus.pack.PackTest.runFromProperty(client.gameDirectory.toPath());
            com.qateralong.nimbus.pack.Pipeline.ensure();
        });
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
            if (com.qateralong.nimbus.pack.Pipeline.supported()) {
                NimbusConfig config = NimbusConfig.get();
                while (shaderReload.consumeClick()) {
                    if (config.shaders && com.qateralong.nimbus.pack.Pipeline.get() != null) {
                        com.qateralong.nimbus.pack.Pipeline.reload();
                        actionBar(client, Component.translatable("nimbus.shaders.reloaded"));
                    }
                }
                while (shaderToggle.consumeClick()) {
                    if (config.shaderPack == null || config.shaderPack.isEmpty()) {
                        actionBar(client, Component.translatable("nimbus.shaders.none"));
                        continue;
                    }
                    config.shaders = !config.shaders;
                    config.save();
                    com.qateralong.nimbus.pack.Pipeline.select(config.shaders ? config.shaderPack : "");
                    actionBar(client, Component.translatable(config.shaders ? "nimbus.shaders.on" : "nimbus.shaders.off", config.shaderPack.replace(".zip", "")));
                }
                while (shaderScreen.consumeClick()) {
                    client.setScreen(new com.qateralong.nimbus.pack.ShaderScreen(client.screen));
                }
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

    private static void actionBar(net.minecraft.client.Minecraft client, Component text) {
        if (client.player != null) {
            client.player.displayClientMessage(text, true);
        }
    }
}
