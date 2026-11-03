package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.StateCopies;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin implements StateCopies.Source {
    @Unique
    private int nimbus$stamp;

    @Unique
    private PalettedContainer<?> nimbus$copy;

    @Unique
    private int nimbus$copyStamp;

    @Inject(method = "set(ILjava/lang/Object;)V", at = @At("HEAD"))
    private void nimbus$set(int index, Object value, CallbackInfo ci) {
        this.nimbus$stamp++;
    }

    @Inject(method = "getAndSet(ILjava/lang/Object;)Ljava/lang/Object;", at = @At("HEAD"))
    private void nimbus$getAndSet(int index, Object value, CallbackInfoReturnable<Object> cir) {
        this.nimbus$stamp++;
    }

    @Inject(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("HEAD"))
    private void nimbus$read(CallbackInfo ci) {
        this.nimbus$stamp++;
    }

    @Override
    public int nimbus$stamp() {
        return this.nimbus$stamp;
    }

    @Override
    public PalettedContainer<?> nimbus$copy() {
        return this.nimbus$copy;
    }

    @Override
    public int nimbus$copyStamp() {
        return this.nimbus$copyStamp;
    }

    @Override
    public void nimbus$keep(PalettedContainer<?> copy, int stamp) {
        this.nimbus$copy = copy;
        this.nimbus$copyStamp = stamp;
    }
}
