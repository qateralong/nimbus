package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public abstract class MinecraftScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen nimbus$video(Screen screen) {
        if (screen instanceof VideoSettingsScreen video && !FabricLoader.getInstance().isModLoaded("sodium")) {
            return new NimbusScreen(((OptionsSubScreenAccessor) video).nimbus$lastScreen());
        }
        return screen;
    }
}
