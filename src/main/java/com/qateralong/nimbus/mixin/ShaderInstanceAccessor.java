package com.qateralong.nimbus.mixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderInstance.class)
public interface ShaderInstanceAccessor {
    @Accessor("lastProgramId")
    static void nimbus$setLastProgramId(int id) {
        throw new AssertionError();
    }

    @Accessor("lastAppliedShader")
    static void nimbus$setLastAppliedShader(ShaderInstance shader) {
        throw new AssertionError();
    }
}
