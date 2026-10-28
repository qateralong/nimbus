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
    private static KeyMapping toggle;
    private static KeyMapping settings;

    @Override
    public void onInitializeClient() {
        NimbusConfig.get();
        toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nimbus.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.nimbus"));
        settings = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nimbus.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.nimbus"));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof OptionsScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("Nimbus"), b -> client.setScreen(new NimbusScreen(screen)))
                        .bounds(width - 84, 6, 78, 20).build());
            }
        });
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, delta) -> FpsOverlay.render(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) {
                NimbusConfig config = NimbusConfig.get();
                config.enabled = !config.enabled;
                config.save();
                if (client.player != null) {
                    client.player.displayClientMessage(Component.translatable(config.enabled ? "nimbus.on" : "nimbus.off"), true);
                }
            }
            while (settings.consumeClick()) {
                client.setScreen(new NimbusScreen(client.screen));
            }
            Cull.adapt(client.getFps());
            Bench.tick(client);
        });
    }
}
