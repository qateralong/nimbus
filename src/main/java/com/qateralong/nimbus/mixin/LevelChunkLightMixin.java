package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.qateralong.nimbus.light.ClientLight;
import com.qateralong.nimbus.light.LightColumn;
import com.qateralong.nimbus.light.LightHolder;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ClientLight keeps light on the chunk and has no use for the sky source heightmap, so it is never built.
@Mixin(LevelChunk.class)
public abstract class LevelChunkLightMixin implements LightHolder {
    @Shadow
    @Final
    Level level;

    @Unique
    private volatile LightColumn nimbus$light;

    @Override
    public LightColumn nimbus$light() {
        return this.nimbus$light;
    }

    @Override
    public void nimbus$setLight(LightColumn column) {
        this.nimbus$light = column;
    }

    @Unique
    private boolean nimbus$ownLight() {
        return this.level.isClientSide && this.level.getChunkSource().getLightEngine() instanceof ClientLight;
    }

    @Unique
    private boolean nimbus$decoded;

    @org.spongepowered.asm.mixin.injection.Inject(method = "replaceWithPacketData", at = @At("HEAD"))
    private void nimbus$decoded(CallbackInfo ci) {
        this.nimbus$decoded = com.qateralong.nimbus.ChunkPrep.apply(((LevelChunk) (Object) this).getSections());
    }

    @WrapOperation(method = "replaceWithPacketData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;read(Lnet/minecraft/network/FriendlyByteBuf;)V"))
    private void nimbus$read(net.minecraft.world.level.chunk.LevelChunkSection section, net.minecraft.network.FriendlyByteBuf buffer, Operation<Void> original) {
        if (!this.nimbus$decoded) {
            original.call(section, buffer);
        }
    }

    @WrapOperation(method = "replaceWithPacketData", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;initializeLightSources()V"))
    private void nimbus$sources(LevelChunk chunk, Operation<Void> original) {
        if (!this.nimbus$ownLight()) {
            original.call(chunk);
        }
    }

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/ChunkSkyLightSources;update(Lnet/minecraft/world/level/BlockGetter;III)Z"))
    private boolean nimbus$updateSources(ChunkSkyLightSources sources, BlockGetter level, int x, int y, int z, Operation<Boolean> original) {
        return !this.nimbus$ownLight() && original.call(sources, level, x, y, z);
    }
}
