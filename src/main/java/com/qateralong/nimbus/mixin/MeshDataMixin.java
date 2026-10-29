package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import com.qateralong.nimbus.render.Packed;
import com.qateralong.nimbus.render.PackedMesh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MeshData.class)
public abstract class MeshDataMixin implements PackedMesh {
    @Unique
    private Packed nimbus$packed;

    @Override
    public Packed nimbus$packed() {
        return this.nimbus$packed;
    }

    @Override
    public void nimbus$setPacked(Packed packed) {
        this.nimbus$packed = packed;
    }
}
