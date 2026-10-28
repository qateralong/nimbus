package com.qateralong.nimbus;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
