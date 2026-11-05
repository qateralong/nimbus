package com.qateralong.nimbus.common.mixin;

import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Block counts of a loaded section (non-air, ticking blocks and fluids) went through an int hash map, one addTo per
// cell. Small palettes count into an array instead; the totals handed to the consumer are the same.
@Mixin(PalettedContainer.class)
public abstract class PaletteCountMixin<T> {
    @Inject(method = "count", at = @At("HEAD"), cancellable = true)
    private void nimbus$count(PalettedContainer.CountConsumer<T> consumer, CallbackInfo ci) {
        PalettedContainer.Data<T> data = ((PalettedContainer<T>) (Object) this).data;
        Palette<T> palette = data.palette();
        int size = palette.getSize();
        BitStorage storage = data.storage();
        if (size <= 1 || storage.getBits() > 8) {
            return;
        }
        int[] counts = new int[1 << storage.getBits()];
        storage.getAll(index -> counts[index]++);
        for (int k = size; k < counts.length; k++) {
            if (counts[k] != 0) {
                return;
            }
        }
        for (int k = 0; k < size; k++) {
            if (counts[k] != 0) {
                consumer.accept(palette.valueFor(k), counts[k]);
            }
        }
        ci.cancel();
    }
}
