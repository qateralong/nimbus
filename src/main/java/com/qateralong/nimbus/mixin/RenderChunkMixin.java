package com.qateralong.nimbus.mixin;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.chunk.RenderChunk")
public abstract class RenderChunkMixin implements com.qateralong.nimbus.render.ChunkSections {
    @Unique
    private static final BlockState NIMBUS_AIR = Blocks.AIR.defaultBlockState();

    @Shadow
    @Final
    private List<PalettedContainer<BlockState>> sections;

    @Shadow
    @Final
    private boolean debug;

    @Unique
    private PalettedContainer<BlockState>[] nimbus$sections;

    @Unique
    private int nimbus$minSection;

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableMap;copyOf(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;"))
    private ImmutableMap<?, ?> nimbus$snapshot(java.util.Map<?, ?> map, com.llamalad7.mixinextras.injector.wrapoperation.Operation<ImmutableMap<?, ?>> original,
                                              @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) LevelChunk chunk) {
        if (com.qateralong.nimbus.NimbusConfig.get().enabled && RenderSystem.isOnRenderThread()) {
            return (ImmutableMap<?, ?>) ((com.qateralong.nimbus.render.BlockEntitySnapshot) chunk).nimbus$snapshot();
        }
        return original.call(map);
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/PalettedContainer;copy()Lnet/minecraft/world/level/chunk/PalettedContainer;"))
    private PalettedContainer<?> nimbus$copy(PalettedContainer<?> states, com.llamalad7.mixinextras.injector.wrapoperation.Operation<PalettedContainer<?>> original) {
        if (com.qateralong.nimbus.NimbusConfig.get().enabled && RenderSystem.isOnRenderThread()) {
            PalettedContainer<?> kept = com.qateralong.nimbus.render.StateCopies.cached(states);
            return kept != null ? kept : com.qateralong.nimbus.render.StateCopies.keep(states, original.call(states));
        }
        return original.call(states);
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void nimbus$init(LevelChunk chunk, CallbackInfo ci) {
        if (!this.debug && this.sections != null) {
            this.nimbus$sections = this.sections.toArray(new PalettedContainer[0]);
            this.nimbus$minSection = chunk.getMinSection();
        }
    }

    @Shadow
    @Final
    private LevelChunk wrapped;

    /**
     * @author qateralong
     * @reason called for every block of every compile window; a cancellable inject allocated a callback per call
     */
    @org.spongepowered.asm.mixin.Overwrite
    public BlockState getBlockState(BlockPos pos) {
        int y = pos.getY();
        PalettedContainer<BlockState>[] array = this.nimbus$sections;
        if (array != null) {
            int index = (y >> 4) - this.nimbus$minSection;
            if (index < 0 || index >= array.length) {
                return NIMBUS_AIR;
            }
            PalettedContainer<BlockState> section = array[index];
            return section == null ? NIMBUS_AIR : section.get(pos.getX() & 15, y & 15, pos.getZ() & 15);
        }
        if (this.debug) {
            BlockState state = null;
            if (y == 60) {
                state = Blocks.BARRIER.defaultBlockState();
            }
            if (y == 70) {
                state = net.minecraft.world.level.levelgen.DebugLevelSource.getBlockStateFor(pos.getX(), pos.getZ());
            }
            return state == null ? NIMBUS_AIR : state;
        }
        if (this.sections == null) {
            return NIMBUS_AIR;
        }
        int index = this.wrapped.getSectionIndex(y);
        if (index < 0 || index >= this.sections.size()) {
            return NIMBUS_AIR;
        }
        PalettedContainer<BlockState> section = this.sections.get(index);
        return section == null ? NIMBUS_AIR : section.get(pos.getX() & 15, y & 15, pos.getZ() & 15);
    }

    @Override
    public PalettedContainer<BlockState>[] nimbus$containers() {
        return this.nimbus$sections;
    }

    @Override
    public int nimbus$minSection() {
        return this.nimbus$minSection;
    }
}
