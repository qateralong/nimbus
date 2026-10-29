package com.qateralong.nimbus.mixin;

import com.google.common.collect.ImmutableMap;
import com.qateralong.nimbus.render.BlockEntitySnapshot;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class ChunkBlockEntityMixin implements BlockEntitySnapshot {
    @Unique
    private int nimbus$version;
    @Unique
    private int nimbus$snapshotVersion = -1;
    @Unique
    private ImmutableMap<BlockPos, BlockEntity> nimbus$snapshot;

    @Inject(method = {"setBlockEntity", "removeBlockEntity", "clearAllBlockEntities", "replaceWithPacketData", "registerAllBlockEntitiesAfterLevelLoad"}, at = @At("HEAD"))
    private void nimbus$changed(CallbackInfo ci) {
        this.nimbus$version++;
    }

    @Override
    public Map<BlockPos, BlockEntity> nimbus$snapshot() {
        Map<BlockPos, BlockEntity> live = ((LevelChunk) (Object) this).getBlockEntities();
        ImmutableMap<BlockPos, BlockEntity> snapshot = this.nimbus$snapshot;
        if (snapshot == null || this.nimbus$snapshotVersion != this.nimbus$version || snapshot.size() != live.size()) {
            snapshot = ImmutableMap.copyOf(live);
            this.nimbus$snapshot = snapshot;
            this.nimbus$snapshotVersion = this.nimbus$version;
        }
        return snapshot;
    }
}
