package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.Cull;
import java.util.List;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void nimbus$line(CallbackInfoReturnable<List<String>> cir) {
        if (NimbusConfig.get().debugLine) {
            cir.getReturnValue().add(NimbusConfig.get().enabled ? Cull.last : "Nimbus: выключен");
            if (com.qateralong.nimbus.render.GpuTimer.frameMs > 0) {
                cir.getReturnValue().add(String.format("Nimbus GPU: кадр %.2f мс, ландшафт %.2f мс", com.qateralong.nimbus.render.GpuTimer.frameMs,
                        com.qateralong.nimbus.render.GpuTimer.terrainMs));
            }
            if (com.qateralong.nimbus.render.GuiBatch.merged > 0) {
                cir.getReturnValue().add("Nimbus GUI: склеено " + com.qateralong.nimbus.render.GuiBatch.lastMerged + " картинок за кадр");
            }
            String terrain = com.qateralong.nimbus.render.Terrain.debug();
            if (terrain != null) {
                cir.getReturnValue().add(terrain);
            }
        }
    }
}
