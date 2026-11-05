package com.qateralong.nimbus.common.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Every game event (a step, a block placed) asked each section around it for its listener registry, and the server
// created an empty one for every section ever touched. Sections without one have no listeners to visit.
@Mixin(GameEventDispatcher.class)
public abstract class GameEventDispatchMixin {
    @Redirect(method = "post", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getListenerRegistry(I)Lnet/minecraft/world/level/gameevent/GameEventListenerRegistry;"))
    private GameEventListenerRegistry nimbus$existing(ChunkAccess chunk, int sectionY) {
        if (chunk instanceof LevelChunk level) {
            GameEventListenerRegistry registry = ((LevelChunkListenersAccessor) level).nimbus$listenerSections().get(sectionY);
            return registry != null ? registry : GameEventListenerRegistry.NOOP;
        }
        return chunk.getListenerRegistry(sectionY);
    }
}
