package com.qateralong.nimbus.mixin;

import java.util.List;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DebugScreenOverlay.class)
public interface DebugOverlayAccessor {
    @Invoker("getGameInformation")
    List<String> nimbus$game();

    @Invoker("getSystemInformation")
    List<String> nimbus$system();
}
