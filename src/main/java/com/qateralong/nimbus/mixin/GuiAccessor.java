package com.qateralong.nimbus.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Gui.class)
public interface GuiAccessor {
    @Invoker("renderCameraOverlays")
    void nimbus$cameraOverlays(GuiGraphics graphics, DeltaTracker delta);

    @Invoker("renderCrosshair")
    void nimbus$crosshair(GuiGraphics graphics, DeltaTracker delta);
}
