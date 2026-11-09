package com.qateralong.nimbus.common.mixin;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import net.minecraft.core.IdMap;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Saving a section built a hash palette sized by the section's storage bits, remapped every cell through it and walked
// its whole id table for the entry list. The same packed data is built straight: entries in first-seen order, each
// cell's new index, the bit count vanilla's strategy picks for that many entries.
@Mixin(PalettedContainer.class)
public abstract class PalettePackMixin<T> {
    @Shadow
    private void acquire() {
        throw new AssertionError();
    }

    @Shadow
    private void release() {
        throw new AssertionError();
    }

    @Inject(method = "pack", at = @At("HEAD"), cancellable = true)
    private void nimbus$pack(IdMap<T> registry, PalettedContainer.Strategy strategy, CallbackInfoReturnable<PalettedContainerRO.PackedData<T>> cir) {
        this.acquire();
        try {
            PalettedContainer.Data<T> data = ((PalettedContainer<T>) (Object) this).data;
            Palette<T> palette = data.palette();
            int size = strategy.size();
            if (data.storage().getBits() == 0) {
                // one value everywhere (air above ground, a single biome): no cells to remap
                List<T> single = new ArrayList<>(1);
                single.add(palette.valueFor(0));
                int bits = ((StrategyAccessor) strategy).nimbus$bits(registry, 1);
                Optional<LongStream> stored = bits == 0 ? Optional.empty() : Optional.of(Arrays.stream(new SimpleBitStorage(bits, size, new int[size]).getRaw()));
                cir.setReturnValue(new PalettedContainerRO.PackedData<>(single, stored));
                return;
            }
            int[] cells = new int[size];
            data.storage().unpack(cells);
            List<T> entries = new ArrayList<>();
            int[] small = palette.getSize() <= 256 ? new int[256] : null;
            Int2IntOpenHashMap large = small == null ? new Int2IntOpenHashMap() : null;
            int previous = -1;
            int mapped = -1;
            for (int k = 0; k < size; k++) {
                int value = cells[k];
                if (value != previous) {
                    previous = value;
                    int known = small != null && value < 256 ? small[value] - 1 : small != null ? -2 : large.getOrDefault(value, -1);
                    if (known == -2) {
                        // an index past a small palette: let vanilla deal with it
                        return;
                    }
                    if (known < 0) {
                        known = entries.size();
                        entries.add(palette.valueFor(value));
                        if (small != null) {
                            small[value] = known + 1;
                        } else {
                            large.put(value, known);
                        }
                    }
                    mapped = known;
                }
                cells[k] = mapped;
            }
            int bits = ((StrategyAccessor) strategy).nimbus$bits(registry, entries.size());
            Optional<LongStream> stored = bits == 0 ? Optional.empty() : Optional.of(Arrays.stream(new SimpleBitStorage(bits, size, cells).getRaw()));
            cir.setReturnValue(new PalettedContainerRO.PackedData<>(entries, stored));
        } finally {
            this.release();
        }
    }
}
