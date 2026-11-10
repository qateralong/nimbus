package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.qateralong.nimbus.pack.BlockIds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Light coordinates of vertices written while a block is being meshed under a shader pack carry its block id.
@Mixin(BufferBuilder.class)
public abstract class PackLightTagMixin {
    @ModifyVariable(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int nimbus$packTagBulk(int packedLight) {
        return BlockIds.on ? BlockIds.tag(packedLight) : packedLight;
    }

    @ModifyVariable(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int nimbus$packAo(int color) {
        return BlockIds.on ? BlockIds.color(color) : color;
    }

    @ModifyVariable(method = "setLight", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int nimbus$packTag(int packedLight) {
        return BlockIds.on ? BlockIds.tag(packedLight) : packedLight;
    }
}
