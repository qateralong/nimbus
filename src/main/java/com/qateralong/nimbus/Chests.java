package com.qateralong.nimbus;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;

public final class Chests {
    private static final Set<Long> OPEN = ConcurrentHashMap.newKeySet();
    private static final Map<Long, Long> LINGER = new ConcurrentHashMap<>();
    public static volatile boolean available;

    private Chests() {
    }

    public static boolean enabled() {
        NimbusConfig config = NimbusConfig.get();
        return available && config.enabled && config.chestModels;
    }

    public static boolean isChest(BlockEntity blockEntity) {
        return blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity;
    }

    public static boolean hiddenInMesh(BlockPos pos) {
        return OPEN.contains(pos.asLong());
    }

    public static boolean drawEntity(BlockPos pos) {
        long key = pos.asLong();
        if (OPEN.contains(key)) {
            return true;
        }
        Long until = LINGER.get(key);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            LINGER.remove(key);
            return false;
        }
        return true;
    }

    public static void update(BlockEntity blockEntity, float openness) {
        if (!enabled() || blockEntity.getLevel() == null || !blockEntity.getLevel().isClientSide()) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        long key = pos.asLong();
        if (openness > 0.0F) {
            if (OPEN.add(key)) {
                LINGER.remove(key);
                dirty(pos);
            }
        } else if (OPEN.remove(key)) {
            LINGER.put(key, System.currentTimeMillis() + 400L);
            dirty(pos);
        }
    }

    private static void dirty(BlockPos pos) {
        Minecraft.getInstance().levelRenderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    public static void reset() {
        OPEN.clear();
        LINGER.clear();
    }
}
