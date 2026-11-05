package com.qateralong.nimbus.common.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Direction.values() clones its array on every call; redstone wire and pistons call it per update in read-only loops.
@Mixin({RedStoneWireBlock.class, PistonBaseBlock.class, PistonStructureResolver.class})
public abstract class DirectionValuesMixin {
    @Unique
    private static final Direction[] NIMBUS_DIRECTIONS = Direction.values();

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;"), require = 0)
    private Direction[] nimbus$shared() {
        return NIMBUS_DIRECTIONS;
    }
}
