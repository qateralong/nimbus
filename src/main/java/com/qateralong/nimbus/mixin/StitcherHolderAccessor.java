package com.qateralong.nimbus.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.texture.Stitcher$Holder")
public interface StitcherHolderAccessor {
    @Accessor("width")
    int nimbus$width();

    @Accessor("height")
    int nimbus$height();
}
