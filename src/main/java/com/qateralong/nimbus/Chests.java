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
    public static net.minecraft.world.level.block.Block currentSign;
    private static final boolean CHRISTMAS = christmas();

    private Chests() {
    }

    public static boolean enabled() {
        NimbusConfig config = NimbusConfig.get();
        return available && !CHRISTMAS && config.enabled && config.chestModels;
    }

    private static boolean christmas() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int day = calendar.get(java.util.Calendar.DATE);
        return calendar.get(java.util.Calendar.MONTH) == java.util.Calendar.DECEMBER && day >= 24 && day <= 26;
    }

    public static boolean isChest(BlockEntity blockEntity) {
        return blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity;
    }

    public static boolean bedsEnabled() {
        NimbusConfig config = NimbusConfig.get();
        return available && config.enabled && config.bedModels;
    }

    public static boolean shulkersEnabled() {
        NimbusConfig config = NimbusConfig.get();
        return available && config.enabled && config.shulkerModels;
    }

    public static boolean signsEnabled() {
        NimbusConfig config = NimbusConfig.get();
        return available && config.enabled && config.signModels;
    }

    public static boolean meshedSign(net.minecraft.world.level.block.Block block) {
        return signsEnabled() && com.qateralong.nimbus.render.EntityBlocks.SIGNS.contains(block);
    }

    private static final Map<Long, BlockEntity> WOBBLING = new ConcurrentHashMap<>();
    private static final Map<Long, Long> WOBBLE_END = new ConcurrentHashMap<>();

    public static boolean potsEnabled() {
        NimbusConfig config = NimbusConfig.get();
        return available && config.enabled && config.potModels;
    }

    public static void potChanged(BlockEntity blockEntity) {
        if (potsEnabled() && blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide()) {
            dirty(blockEntity.getBlockPos());
        }
    }

    public static void potWobble(BlockEntity blockEntity, int duration) {
        if (!potsEnabled() || blockEntity.getLevel() == null || !blockEntity.getLevel().isClientSide()) {
            return;
        }
        long key = blockEntity.getBlockPos().asLong();
        WOBBLING.put(key, blockEntity);
        WOBBLE_END.put(key, blockEntity.getLevel().getGameTime() + duration);
        update(blockEntity, 1.0F);
    }

    public static void tick() {
        if (WOBBLE_END.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Long> entry : WOBBLE_END.entrySet()) {
            BlockEntity blockEntity = WOBBLING.get(entry.getKey());
            if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() == null || blockEntity.getLevel().getGameTime() >= entry.getValue()) {
                WOBBLE_END.remove(entry.getKey());
                WOBBLING.remove(entry.getKey());
                if (blockEntity != null) {
                    update(blockEntity, 0.0F);
                }
            }
        }
    }

    public static boolean bellsEnabled() {
        NimbusConfig config = NimbusConfig.get();
        return available && config.enabled && config.bellModels;
    }

    public static boolean meshed(BlockEntity blockEntity) {
        if (blockEntity instanceof net.minecraft.world.level.block.entity.DecoratedPotBlockEntity) {
            return potsEnabled();
        }
        if (blockEntity instanceof net.minecraft.world.level.block.entity.BellBlockEntity) {
            return bellsEnabled();
        }
        if (blockEntity instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity) {
            return shulkersEnabled();
        }
        if (blockEntity instanceof net.minecraft.world.level.block.entity.BedBlockEntity) {
            return bedsEnabled();
        }
        return isChest(blockEntity) && enabled();
    }

    public static boolean animated(BlockEntity blockEntity) {
        return !(blockEntity instanceof net.minecraft.world.level.block.entity.BedBlockEntity);
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
        if (!meshed(blockEntity) || blockEntity.getLevel() == null || !blockEntity.getLevel().isClientSide()) {
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
        WOBBLING.clear();
        WOBBLE_END.clear();
        OPEN.clear();
        LINGER.clear();
    }
}
