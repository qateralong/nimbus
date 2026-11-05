package com.qateralong.nimbus.common;

import java.util.Set;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import org.jetbrains.annotations.Nullable;

// Bumped on every content change of a block entity container (setChanged, setItem, removeItem, clear...). Only trusted
// for the vanilla container classes listed here, whose every change goes through one of those; mod containers may
// change their stacks in place and are never cached.
public interface ContentVersion {
    Set<Class<?>> TRUSTED = Set.of(ChestBlockEntity.class, TrappedChestBlockEntity.class, BarrelBlockEntity.class, ShulkerBoxBlockEntity.class,
            HopperBlockEntity.class, DispenserBlockEntity.class, DropperBlockEntity.class, FurnaceBlockEntity.class, BlastFurnaceBlockEntity.class,
            SmokerBlockEntity.class, BrewingStandBlockEntity.class);

    int nimbus$contentVersion();

    void nimbus$bump();

    static int of(BlockEntity entity) {
        return ((ContentVersion) entity).nimbus$contentVersion();
    }

    // the trusted block entities behind a container (two for a double chest), or null
    @Nullable
    static BlockEntity[] parts(Container container) {
        if (container instanceof BlockEntity entity) {
            return TRUSTED.contains(entity.getClass()) ? new BlockEntity[]{entity, null} : null;
        }
        if (container instanceof CompoundContainer compound) {
            Container first = ((com.qateralong.nimbus.common.mixin.CompoundContainerAccessor) compound).nimbus$first();
            Container second = ((com.qateralong.nimbus.common.mixin.CompoundContainerAccessor) compound).nimbus$second();
            if (first instanceof BlockEntity a && second instanceof BlockEntity b && TRUSTED.contains(a.getClass()) && TRUSTED.contains(b.getClass())) {
                return new BlockEntity[]{a, b};
            }
        }
        return null;
    }

    static boolean same(@Nullable BlockEntity entity, int version) {
        return entity == null || !entity.isRemoved() && of(entity) == version;
    }
}
