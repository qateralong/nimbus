package com.qateralong.nimbus.mixin;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Each of the ~12k multipart models kept a 16-slot hash map of matched parts per state, most never asked. A
// ConcurrentHashMap allocates its table on first use, and the cache is also filled from several build threads at once.
@Mixin(MultiPartBakedModel.class)
public abstract class MultipartSelectorMixin {
    @Shadow
    @Final
    @Mutable
    private Map<BlockState, BitSet> selectorCache;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nimbus$lazyCache(List<Pair<Predicate<BlockState>, BakedModel>> selectors, CallbackInfo ci) {
        this.selectorCache = new ConcurrentHashMap<>();
    }
}
