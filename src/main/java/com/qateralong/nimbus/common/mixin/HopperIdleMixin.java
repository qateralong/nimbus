package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.ContentVersion;
import com.qateralong.nimbus.common.EntityKinds;
import com.qateralong.nimbus.common.IdleTicker;
import com.qateralong.nimbus.common.SectionFlags;
import com.qateralong.nimbus.common.Watchers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Hoppers retry every tick. A failed push or pull is remembered with what decided it: the section of the block in
// front or above, the hopper's own contents and those of the trusted vanilla container it faced. Until one of them
// changes the attempt fails again, so it is skipped; a hopper with nothing to do then only stamps its tick time.
// A pull from no block at all also needs no item or container entity around.
@Mixin(HopperBlockEntity.class)
public abstract class HopperIdleMixin implements IdleTicker, Watchers.Sleeper {
    @Shadow
    private int cooldownTime;
    @Shadow
    private long tickedGameTime;

    @Shadow
    private boolean inventoryFull() {
        throw new AssertionError();
    }

    // container found by the attempt running now (server thread)
    @Unique
    private static Container nimbus$found;

    @Unique
    private LevelChunk nimbus$chunk;

    // pull: 0 unknown, 1 nothing above, 2 a trusted container above that gave nothing
    @Unique
    private int nimbus$pull;
    @Unique
    private LevelChunkSection nimbus$pullSection;
    @Unique
    private int nimbus$pullSectionVersion;
    @Unique
    private BlockEntity nimbus$pullA;
    @Unique
    private BlockEntity nimbus$pullB;
    @Unique
    private int nimbus$pullVersionA;
    @Unique
    private int nimbus$pullVersionB;
    @Unique
    private int nimbus$pullSelf;

    @Unique
    private boolean nimbus$push;
    @Unique
    private LevelChunkSection nimbus$pushSection;
    @Unique
    private int nimbus$pushSectionVersion;
    @Unique
    private BlockEntity nimbus$pushA;
    @Unique
    private BlockEntity nimbus$pushB;
    @Unique
    private int nimbus$pushVersionA;
    @Unique
    private int nimbus$pushVersionB;
    @Unique
    private int nimbus$pushSelf;
    @Unique
    private BlockState nimbus$pushState;

    // asleep: registered on everything its idleness depends on, woken by any change there
    @Unique
    private boolean nimbus$asleep;
    @Unique
    private boolean nimbus$sleepOnEntities;
    @Unique
    private int nimbus$sleepArrivals;

    @Override
    public void nimbus$wake() {
        this.nimbus$asleep = false;
    }

    @Unique
    private void nimbus$sleep(LevelChunk chunk, BlockPos pos, boolean enabled, boolean empty, boolean full) {
        Watchers.Sleeper self = this;
        ((Watchers.Watched) this).nimbus$watch(self);
        int index = chunk.getSectionIndex(pos.getY());
        ((Watchers.Watched) chunk.getSections()[index]).nimbus$watch(self);
        this.nimbus$sleepOnEntities = false;
        if (enabled) {
            if (!empty) {
                ((Watchers.Watched) this.nimbus$pushSection).nimbus$watch(self);
                ((Watchers.Watched) this.nimbus$pushA).nimbus$watch(self);
                if (this.nimbus$pushB != null) {
                    ((Watchers.Watched) this.nimbus$pushB).nimbus$watch(self);
                }
            }
            if (!full) {
                ((Watchers.Watched) this.nimbus$pullSection).nimbus$watch(self);
                if (this.nimbus$pull == 2) {
                    ((Watchers.Watched) this.nimbus$pullA).nimbus$watch(self);
                    if (this.nimbus$pullB != null) {
                        ((Watchers.Watched) this.nimbus$pullB).nimbus$watch(self);
                    }
                } else {
                    this.nimbus$sleepOnEntities = true;
                    this.nimbus$sleepArrivals = EntityKinds.arrivals;
                }
            }
        }
        this.nimbus$asleep = true;
    }

    @Unique
    private boolean nimbus$nothingToPull(Level level) {
        int pull = this.nimbus$pull;
        if (pull == 0 || ((SectionFlags) this.nimbus$pullSection).nimbus$version() != this.nimbus$pullSectionVersion) {
            return false;
        }
        if (pull == 2) {
            return ContentVersion.of((BlockEntity) (Object) this) == this.nimbus$pullSelf && ContentVersion.same(this.nimbus$pullA, this.nimbus$pullVersionA)
                    && ContentVersion.same(this.nimbus$pullB, this.nimbus$pullVersionB);
        }
        BlockPos pos = ((BlockEntity) (Object) this).getBlockPos();
        AABB area = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0);
        return !EntityKinds.maybeAnyOf(level, area, 1 << EntityKinds.ITEM | 1 << EntityKinds.CONTAINER);
    }

    @Unique
    private boolean nimbus$pushBlocked() {
        BlockEntity self = (BlockEntity) (Object) this;
        return this.nimbus$push && ((SectionFlags) this.nimbus$pushSection).nimbus$version() == this.nimbus$pushSectionVersion
                && self.getBlockState() == this.nimbus$pushState && ContentVersion.of(self) == this.nimbus$pushSelf
                && ContentVersion.same(this.nimbus$pushA, this.nimbus$pushVersionA) && ContentVersion.same(this.nimbus$pushB, this.nimbus$pushVersionB);
    }

    @Unique
    @Nullable
    private static LevelChunkSection nimbus$sectionAt(Level level, BlockPos pos) {
        if (!(level.getChunk(pos.getX() >> 4, pos.getZ() >> 4) instanceof LevelChunk chunk) || chunk.getClass() != LevelChunk.class) {
            return null;
        }
        int index = chunk.getSectionIndex(pos.getY());
        LevelChunkSection[] sections = chunk.getSections();
        return index >= 0 && index < sections.length ? sections[index] : null;
    }

    @Override
    public boolean nimbus$idleTick() {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (this.cooldownTime > 0 || level == null || level.isClientSide || self.isRemoved()) {
            return false;
        }
        BlockPos pos = self.getBlockPos();
        LevelChunk chunk = this.nimbus$chunk;
        if (chunk == null) {
            chunk = level.getChunkAt(pos);
            this.nimbus$chunk = chunk;
        }
        if (this.nimbus$asleep && this.nimbus$sleepOnEntities && EntityKinds.arrivals != this.nimbus$sleepArrivals) {
            if (this.nimbus$nothingToPull(level)) {
                this.nimbus$sleepArrivals = EntityKinds.arrivals;
            } else {
                this.nimbus$asleep = false;
            }
        }
        if (!this.nimbus$asleep) {
            // the ticker runs on the chunk's state; the block entity's copy can lag behind it (a hopper placed
            // powered by command is switched on during its own placement), so "off" is confirmed against the chunk
            boolean enabled = self.getBlockState().getValue(HopperBlock.ENABLED);
            if (!enabled) {
                BlockState actual = chunk.getBlockState(pos);
                if (!actual.hasProperty(HopperBlock.ENABLED)) {
                    return false;
                }
                enabled = actual.getValue(HopperBlock.ENABLED);
            }
            boolean empty = true;
            boolean full = false;
            if (enabled) {
                empty = ((HopperBlockEntity) self).isEmpty();
                full = this.inventoryFull();
                if (!(empty || this.nimbus$pushBlocked()) || !(full || this.nimbus$nothingToPull(level))) {
                    return false;
                }
            }
            this.nimbus$sleep(chunk, pos, enabled, empty, full);
        }
        if (!((LevelChunkTickingAccessor) chunk).nimbus$isTicking(pos)) {
            return false;
        }
        this.tickedGameTime = level.getGameTime();
        this.cooldownTime = 0;
        return true;
    }

    @Inject(method = "ejectItems", at = @At("HEAD"), cancellable = true)
    private static void nimbus$skipPush(Level level, BlockPos pos, HopperBlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        if (((HopperIdleMixin) (Object) blockEntity).nimbus$pushBlocked()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "ejectItems", at = @At("STORE"), ordinal = 0)
    private static Container nimbus$target(Container container) {
        nimbus$found = container;
        return container;
    }

    @Inject(method = "ejectItems", at = @At("RETURN"))
    private static void nimbus$learnPush(Level level, BlockPos pos, HopperBlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        HopperIdleMixin self = (HopperIdleMixin) (Object) blockEntity;
        Container target = nimbus$found;
        nimbus$found = null;
        self.nimbus$push = false;
        if (cir.getReturnValueZ() || target == null || level.isClientSide) {
            return;
        }
        BlockEntity[] parts = ContentVersion.parts(target);
        BlockState state = blockEntity.getBlockState();
        LevelChunkSection section = parts == null ? null : nimbus$sectionAt(level, pos.relative(state.getValue(HopperBlock.FACING)));
        if (section == null) {
            return;
        }
        self.nimbus$pushSection = section;
        self.nimbus$pushSectionVersion = ((SectionFlags) section).nimbus$version();
        self.nimbus$pushA = parts[0];
        self.nimbus$pushVersionA = ContentVersion.of(parts[0]);
        self.nimbus$pushB = parts[1];
        self.nimbus$pushVersionB = parts[1] == null ? 0 : ContentVersion.of(parts[1]);
        self.nimbus$pushSelf = ContentVersion.of(blockEntity);
        self.nimbus$pushState = state;
        self.nimbus$push = true;
    }

    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void nimbus$skipPull(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (hopper instanceof HopperIdleMixin idle && idle.nimbus$nothingToPull(level)) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(method = "suckInItems", at = @At("STORE"), ordinal = 0)
    private static Container nimbus$source(Container container) {
        nimbus$found = container;
        return container;
    }

    @Inject(method = "suckInItems", at = @At("RETURN"))
    private static void nimbus$learnPull(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        Container source = nimbus$found;
        nimbus$found = null;
        if (!(hopper instanceof HopperIdleMixin idle)) {
            return;
        }
        idle.nimbus$pull = 0;
        if (cir.getReturnValueZ() || level.isClientSide) {
            return;
        }
        BlockPos above = ((BlockEntity) (Object) idle).getBlockPos().above();
        LevelChunkSection section = nimbus$sectionAt(level, above);
        if (section == null) {
            return;
        }
        int version = ((SectionFlags) section).nimbus$version();
        if (source == null) {
            BlockState state = section.getBlockState(above.getX() & 15, above.getY() & 15, above.getZ() & 15);
            if (state.hasBlockEntity() || state.getBlock() instanceof WorldlyContainerHolder) {
                return;
            }
            idle.nimbus$pull = 1;
        } else {
            BlockEntity[] parts = ContentVersion.parts(source);
            if (parts == null) {
                return;
            }
            idle.nimbus$pullA = parts[0];
            idle.nimbus$pullVersionA = ContentVersion.of(parts[0]);
            idle.nimbus$pullB = parts[1];
            idle.nimbus$pullVersionB = parts[1] == null ? 0 : ContentVersion.of(parts[1]);
            idle.nimbus$pullSelf = ContentVersion.of((BlockEntity) (Object) idle);
            idle.nimbus$pull = 2;
        }
        idle.nimbus$pullSection = section;
        idle.nimbus$pullSectionVersion = version;
    }
}
