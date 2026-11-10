package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.LazySearch;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// lazy search trees start building when a screen that can search opens, so they are usually ready before the first query
@Mixin({CreativeModeInventoryScreen.class, RecipeBookComponent.class})
public abstract class SearchWarmMixin {
    @Inject(method = {"init", "initVisuals"}, at = @At("HEAD"), require = 0)
    private void nimbus$warm(CallbackInfo ci) {
        LazySearch.warm();
    }
}
