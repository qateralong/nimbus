package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import com.qateralong.nimbus.render.SectionStamp;
import com.qateralong.nimbus.render.Terrain;
import com.qateralong.nimbus.render.TerrainSlot;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class RenderSectionMixin implements SectionStamp {
    @Unique
    private int nimbus$visibleFrame = -1;

    @Unique
    private int nimbus$lightRetry;

    @Unique
    private TerrainSlot[] nimbus$slots;

    @Unique
    private int nimbus$id = com.qateralong.nimbus.render.Origins.acquire();

    @Override
    public int nimbus$id() {
        return this.nimbus$id;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nimbus$init(CallbackInfo ci) {
        com.qateralong.nimbus.render.Visibility.DIRTY.add((SectionRenderDispatcher.RenderSection) (Object) this);
        com.qateralong.nimbus.render.SectionData.setDirty(this.nimbus$id, true);
        if (Terrain.supported()) {
            this.nimbus$slots();
        }
    }

    @Inject(method = "setOrigin", at = @At("RETURN"))
    private void nimbus$origin(int x, int y, int z, CallbackInfo ci) {
        com.qateralong.nimbus.render.Origins.set(this.nimbus$id, x, y, z);
    }

    @Inject(method = "releaseBuffers", at = @At("RETURN"))
    private void nimbus$releaseId(CallbackInfo ci) {
        com.qateralong.nimbus.render.Visibility.DIRTY.remove((SectionRenderDispatcher.RenderSection) (Object) this);
        com.qateralong.nimbus.render.SectionData.setBlockEntities(this.nimbus$id, false);
        com.qateralong.nimbus.render.SectionData.setDirty(this.nimbus$id, false);
        com.qateralong.nimbus.render.Origins.release(this.nimbus$id);
    }

    @Shadow
    @Final
    private Map<RenderType, VertexBuffer> buffers;

    @Override
    public TerrainSlot[] nimbus$slots() {
        if (this.nimbus$slots == null) {
            if (!Terrain.supported()) {
                this.nimbus$slots = new TerrainSlot[0];
                return this.nimbus$slots;
            }
            List<RenderType> layers = RenderType.chunkBufferLayers();
            TerrainSlot[] slots = new TerrainSlot[layers.size()];
            for (int i = 0; i < slots.length; i++) {
                slots[i] = (TerrainSlot) this.buffers.get(layers.get(i));
                if (slots[i] != null) {
                    slots[i].nimbus$bind(this.nimbus$id, i);
                }
            }
            this.nimbus$slots = slots;
        }
        return this.nimbus$slots;
    }

    @Inject(method = "setCompiled", at = @At("RETURN"))
    private void nimbus$compiled(SectionRenderDispatcher.CompiledSection compiled, CallbackInfo ci) {
        com.qateralong.nimbus.render.SectionData.setBlockEntities(this.nimbus$id, !compiled.getRenderableBlockEntities().isEmpty());
        Terrain.compiled(this, compiled);
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void nimbus$reset(CallbackInfo ci) {
        com.qateralong.nimbus.render.SectionData.setBlockEntities(this.nimbus$id, false);
        com.qateralong.nimbus.render.Visibility.DIRTY.add((SectionRenderDispatcher.RenderSection) (Object) this);
        com.qateralong.nimbus.render.SectionData.setDirty(this.nimbus$id, true);
        Terrain.compiled(this, SectionRenderDispatcher.CompiledSection.UNCOMPILED);
    }

    @Inject(method = "setDirty", at = @At("RETURN"))
    private void nimbus$dirty(boolean player, CallbackInfo ci) {
        com.qateralong.nimbus.render.Visibility.DIRTY.add((SectionRenderDispatcher.RenderSection) (Object) this);
        com.qateralong.nimbus.render.SectionData.setDirty(this.nimbus$id, true);
    }

    @Inject(method = "setNotDirty", at = @At("RETURN"))
    private void nimbus$clean(CallbackInfo ci) {
        com.qateralong.nimbus.render.Visibility.DIRTY.remove((SectionRenderDispatcher.RenderSection) (Object) this);
        com.qateralong.nimbus.render.SectionData.setDirty(this.nimbus$id, false);
    }

    @Override
    public int nimbus$visibleFrame() {
        return this.nimbus$visibleFrame;
    }

    @Override
    public void nimbus$setVisibleFrame(int frame) {
        this.nimbus$visibleFrame = frame;
    }

    @Override
    public int nimbus$lightRetry() {
        return this.nimbus$lightRetry;
    }

    @Override
    public void nimbus$setLightRetry(int frame) {
        this.nimbus$lightRetry = frame;
    }
}
