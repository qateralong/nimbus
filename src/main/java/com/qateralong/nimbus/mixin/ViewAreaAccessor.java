package com.qateralong.nimbus.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewArea.class)
public interface ViewAreaAccessor {
    @Invoker("getRenderSectionAt")
    SectionRenderDispatcher.RenderSection nimbus$sectionAt(BlockPos pos);

    @Accessor("sectionGridSizeX")
    int nimbus$sizeX();

    @Accessor("sectionGridSizeY")
    int nimbus$sizeY();

    @Accessor("sectionGridSizeZ")
    int nimbus$sizeZ();
}
