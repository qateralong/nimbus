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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.chunk.RenderChunk")
public abstract class RenderChunkMixin {
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

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void nimbus$init(LevelChunk chunk, CallbackInfo ci) {
        if (!this.debug && this.sections != null) {
            this.nimbus$sections = this.sections.toArray(new PalettedContainer[0]);
            this.nimbus$minSection = chunk.getMinSection();
        }
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void nimbus$fast(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        PalettedContainer<BlockState>[] array = this.nimbus$sections;
        if (array == null) {
            return;
        }
        int y = pos.getY();
        int index = (y >> 4) - this.nimbus$minSection;
        if (index < 0 || index >= array.length) {
            cir.setReturnValue(NIMBUS_AIR);
            return;
        }
        PalettedContainer<BlockState> section = array[index];
        cir.setReturnValue(section == null ? NIMBUS_AIR : section.get(pos.getX() & 15, y & 15, pos.getZ() & 15));
    }
}
