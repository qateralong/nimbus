package com.qateralong.nimbus.mixin;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Six neighbour origins were stored per section (~150 bytes, ~4 MB at 16 chunks); they are derived from the origin when asked
@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class SectionOriginsMixin {
    @Unique
    private static final BlockPos.MutableBlockPos[] NIMBUS$DISCARD = new BlockPos.MutableBlockPos[6];

    static {
        for (int i = 0; i < NIMBUS$DISCARD.length; i++) {
            NIMBUS$DISCARD[i] = new BlockPos.MutableBlockPos();
        }
    }

    @Shadow
    @Final
    BlockPos.MutableBlockPos origin;

    @Shadow
    public abstract double getDistToPlayerSqr();

    @Shadow
    protected abstract boolean doesChunkExistAt(BlockPos pos);

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;make(Ljava/lang/Object;Ljava/util/function/Consumer;)Ljava/lang/Object;"))
    private Object nimbus$noOrigins(Object array, Consumer<Object> fill) {
        return NIMBUS$DISCARD;
    }

    // setOrigin still writes them: into a shared array nobody reads (render thread only)
    @Redirect(method = "setOrigin", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;relativeOrigins:[Lnet/minecraft/core/BlockPos$MutableBlockPos;", opcode = Opcodes.GETFIELD))
    private BlockPos.MutableBlockPos[] nimbus$discard(SectionRenderDispatcher.RenderSection section) {
        return NIMBUS$DISCARD;
    }

    // also called from section build workers
    @Inject(method = "hasAllNeighbors", at = @At("HEAD"), cancellable = true)
    private void nimbus$neighbours(CallbackInfoReturnable<Boolean> cir) {
        if (this.getDistToPlayerSqr() <= 576.0) {
            cir.setReturnValue(true);
            return;
        }
        BlockPos origin = this.origin.immutable();
        cir.setReturnValue(this.doesChunkExistAt(origin.relative(Direction.WEST, 16)) && this.doesChunkExistAt(origin.relative(Direction.NORTH, 16))
                && this.doesChunkExistAt(origin.relative(Direction.EAST, 16)) && this.doesChunkExistAt(origin.relative(Direction.SOUTH, 16)));
    }

    @Inject(method = "getRelativeOrigin", at = @At("HEAD"), cancellable = true)
    private void nimbus$relative(Direction direction, CallbackInfoReturnable<BlockPos> cir) {
        cir.setReturnValue(this.origin.immutable().relative(direction, 16));
    }
}
