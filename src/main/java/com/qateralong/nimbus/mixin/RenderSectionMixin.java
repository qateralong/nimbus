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
        com.qateralong.nimbus.render.SectionData.register(this.nimbus$id, (SectionRenderDispatcher.RenderSection) (Object) this);
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
        com.qateralong.nimbus.render.SectionData.setBlockEntities(this.nimbus$id, false);
        com.qateralong.nimbus.render.SectionData.setDirty(this.nimbus$id, false);
        com.qateralong.nimbus.render.SectionData.setVisibility(this.nimbus$id, null);
        com.qateralong.nimbus.render.SectionData.register(this.nimbus$id, null);
        com.qateralong.nimbus.render.Origins.release(this.nimbus$id);
    }

    @Shadow
    @Final
    private Map<RenderType, VertexBuffer> buffers;

    @Override
    public TerrainSlot[] nimbus$slots() {
        if (this.nimbus$slots != null && this.nimbus$slots.length > 0) {
            this.nimbus$follow();
        }
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

    // Other mods may swap a layer's VertexBuffer for a new one (VanillaBackport closes and replaces buffers of layers
    // that were empty before a move). Uploads then go to the new buffer, which must belong to this section too.
    @Unique
    private void nimbus$follow() {
        TerrainSlot[] slots = this.nimbus$slots;
        List<RenderType> layers = RenderType.chunkBufferLayers();
        for (int i = 0; i < slots.length && i < layers.size(); i++) {
            VertexBuffer current = this.buffers.get(layers.get(i));
            if (current != slots[i] && current instanceof TerrainSlot slot) {
                slot.nimbus$bind(this.nimbus$id, i);
                slots[i] = slot;
            }
        }
    }

    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void nimbus$current(RenderType layer, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<VertexBuffer> cir) {
        if (cir.getReturnValue() instanceof TerrainSlot slot && slot.nimbus$section() != this.nimbus$id && this.nimbus$slots != null) {
            this.nimbus$follow();
        }
    }

    @Inject(method = "setCompiled", at = @At("RETURN"))
    private void nimbus$compiled(SectionRenderDispatcher.CompiledSection compiled, CallbackInfo ci) {
        if (com.qateralong.nimbus.render.SectionData.later(() -> this.nimbus$compiledNow(((SectionRenderDispatcher.RenderSection) (Object) this).getCompiled()))) {
            return;
        }
        this.nimbus$compiledNow(compiled);
    }

    @Unique
    private void nimbus$compiledNow(SectionRenderDispatcher.CompiledSection compiled) {
        if (com.qateralong.nimbus.render.SectionData.sections.length <= this.nimbus$id
                || com.qateralong.nimbus.render.SectionData.sections[this.nimbus$id] != (Object) this) {
            return;
        }
        com.qateralong.nimbus.render.SectionData.setBlockEntities(this.nimbus$id, !compiled.getRenderableBlockEntities().isEmpty());
        com.qateralong.nimbus.render.SectionData.setVisibility(this.nimbus$id, compiled);
        int mask = 0;
        java.util.List<net.minecraft.client.renderer.RenderType> layers = net.minecraft.client.renderer.RenderType.chunkBufferLayers();
        for (int l = 0; l < layers.size(); l++) {
            if (!compiled.isEmpty(layers.get(l))) {
                mask |= 1 << l;
            }
        }
        com.qateralong.nimbus.render.SectionEvents.add(this.nimbus$id, com.qateralong.nimbus.render.SectionEvents.COMPILED, 0, mask);
        Terrain.compiled(this, compiled);
    }

    @Inject(method = "reset", at = @At("RETURN"))
    private void nimbus$reset(CallbackInfo ci) {
        com.qateralong.nimbus.render.SectionEvents.add(this.nimbus$id, com.qateralong.nimbus.render.SectionEvents.RESET, 0, 0);
        com.qateralong.nimbus.render.SectionData.setBlockEntities(this.nimbus$id, false);
        com.qateralong.nimbus.render.SectionData.setDirty(this.nimbus$id, true);
        com.qateralong.nimbus.render.SectionData.setVisibility(this.nimbus$id, null);
        Terrain.compiled(this, SectionRenderDispatcher.CompiledSection.UNCOMPILED);
    }

    @Inject(method = "setDirty", at = @At("RETURN"))
    private void nimbus$dirty(boolean player, CallbackInfo ci) {
        if (!com.qateralong.nimbus.render.SectionData.later(this::nimbus$syncDirty)) {
            com.qateralong.nimbus.render.SectionData.setDirty(this.nimbus$id, true);
        }
    }

    @Inject(method = "setNotDirty", at = @At("RETURN"))
    private void nimbus$clean(CallbackInfo ci) {
        if (!com.qateralong.nimbus.render.SectionData.later(this::nimbus$syncDirty)) {
            com.qateralong.nimbus.render.SectionData.setDirty(this.nimbus$id, false);
        }
    }

    @Unique
    private void nimbus$syncDirty() {
        if (com.qateralong.nimbus.render.SectionData.sections.length > this.nimbus$id
                && com.qateralong.nimbus.render.SectionData.sections[this.nimbus$id] == (Object) this) {
            com.qateralong.nimbus.render.SectionData.setDirty(this.nimbus$id, ((SectionRenderDispatcher.RenderSection) (Object) this).isDirty());
        }
    }

    @Override
    public int nimbus$visibleFrame() {
        int[] frames = com.qateralong.nimbus.render.SectionData.visibleFrame;
        return this.nimbus$id < frames.length ? frames[this.nimbus$id] : 0;
    }

    @Override
    public void nimbus$setVisibleFrame(int frame) {
        com.qateralong.nimbus.render.SectionData.visibleFrame[this.nimbus$id] = frame;
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
