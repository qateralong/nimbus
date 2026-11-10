package com.qateralong.nimbus.common.mixin;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.minecraft.nbt.CompoundTag$1")
public abstract class CompoundTagLoadMixin {
    @ModifyVariable(method = "loadCompound", at = @At(value = "STORE", ordinal = 0))
    private static Map<String, Tag> nimbus$map(Map<String, Tag> tags) {
        // most tags hold a handful of entries: the default capacity would spend two 33-slot arrays on each
        return new Object2ObjectOpenHashMap<>(4);
    }
}
