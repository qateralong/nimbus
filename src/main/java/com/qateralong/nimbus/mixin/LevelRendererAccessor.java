package com.qateralong.nimbus.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> nimbus$visibleSections();

    @Accessor("viewArea")
    net.minecraft.client.renderer.ViewArea nimbus$viewArea();

    @Accessor("sectionRenderDispatcher")
    SectionRenderDispatcher nimbus$sectionRenderDispatcher();

    @Accessor("xTransparentOld")
    double nimbus$xTransparentOld();

    @Accessor("yTransparentOld")
    double nimbus$yTransparentOld();

    @Accessor("zTransparentOld")
    double nimbus$zTransparentOld();

    @Accessor("xTransparentOld")
    void nimbus$setXTransparentOld(double value);

    @Accessor("yTransparentOld")
    void nimbus$setYTransparentOld(double value);

    @Accessor("zTransparentOld")
    void nimbus$setZTransparentOld(double value);
}
