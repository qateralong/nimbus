package com.qateralong.nimbus.common.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SecondaryPoiSensor;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Only farmers have secondary job blocks; for everyone else the 9x5x9 block scan can only end in erasing the memory.
@Mixin(SecondaryPoiSensor.class)
public abstract class SecondaryPoiSensorMixin {
    @Inject(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)V", at = @At("HEAD"), cancellable = true)
    private void nimbus$noBlocks(ServerLevel level, Villager entity, CallbackInfo ci) {
        if (entity.getVillagerData().getProfession().secondaryPoi().isEmpty()) {
            entity.getBrain().eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
            ci.cancel();
        }
    }
}
