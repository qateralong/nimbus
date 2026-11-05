package com.qateralong.nimbus.mixin;

import com.google.gson.JsonObject;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Athena looks up a block's blockstate file again for every one of its states; the answer only depends on the block
// and the loader, so it is remembered until the next resource reload.
@Pseudo
@Mixin(targets = "earth.terrarium.athena.impl.loading.AthenaResourceLoader", remap = false)
public abstract class AthenaDataMixin {
    @Unique
    private record Key(ResourceLocation id, ResourceLocation loader) {
    }

    @Unique
    private static final ConcurrentHashMap<Key, Optional<JsonObject>> NIMBUS_CACHE = new ConcurrentHashMap<>();

    @Inject(method = {"getData(Lnet/minecraft/class_2960;Lnet/minecraft/class_2960;)Lcom/google/gson/JsonObject;",
            "getData(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)Lcom/google/gson/JsonObject;"},
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void nimbus$cached(ResourceLocation id, ResourceLocation loader, CallbackInfoReturnable<JsonObject> cir) {
        Optional<JsonObject> known = NIMBUS_CACHE.get(new Key(id, loader));
        if (known != null) {
            cir.setReturnValue(known.orElse(null));
        }
    }

    @Inject(method = {"getData(Lnet/minecraft/class_2960;Lnet/minecraft/class_2960;)Lcom/google/gson/JsonObject;",
            "getData(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)Lcom/google/gson/JsonObject;"},
            at = @At("RETURN"), require = 0)
    private static void nimbus$remember(ResourceLocation id, ResourceLocation loader, CallbackInfoReturnable<JsonObject> cir) {
        NIMBUS_CACHE.put(new Key(id, loader), Optional.ofNullable(cir.getReturnValue()));
    }

    @Inject(method = {"reload", "setGetter"}, at = @At("HEAD"), require = 0)
    private static void nimbus$forget(CallbackInfo ci) {
        NIMBUS_CACHE.clear();
    }
}
