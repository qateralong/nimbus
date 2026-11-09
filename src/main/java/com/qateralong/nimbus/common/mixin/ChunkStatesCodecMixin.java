package com.qateralong.nimbus.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

// A section's block states were written through the generic codec: a record codec around a list of dispatch codecs, one
// pass of DataFixerUpper machinery per palette entry. The same tag is built directly: each state's tag comes from the
// vanilla codec once and is copied after that, the packed data is the same pack() the codec would call.
@Mixin(ChunkSerializer.class)
public abstract class ChunkStatesCodecMixin {
    @Shadow
    @Final
    private static Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC;

    @Unique
    private static final ConcurrentHashMap<BlockState, Tag> NIMBUS_STATES = new ConcurrentHashMap<>();

    @WrapOperation(method = "write", at = @At(value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
    private static <T> DataResult<T> nimbus$states(Codec<Object> codec, DynamicOps<T> ops, Object value, Operation<DataResult<T>> original) {
        if (codec != (Object) BLOCK_STATE_CODEC || ops != NbtOps.INSTANCE || value.getClass() != PalettedContainer.class) {
            return original.call(codec, ops, value);
        }
        @SuppressWarnings("unchecked")
        PalettedContainer<BlockState> states = (PalettedContainer<BlockState>) value;
        PalettedContainerRO.PackedData<BlockState> packed = states.pack(Block.BLOCK_STATE_REGISTRY, PalettedContainer.Strategy.SECTION_STATES);
        ListTag palette = new ListTag();
        for (BlockState state : packed.paletteEntries()) {
            Tag tag = NIMBUS_STATES.get(state);
            if (tag == null) {
                DataResult<Tag> encoded = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state);
                if (encoded.isError()) {
                    return original.call(codec, ops, value);
                }
                tag = encoded.getOrThrow();
                NIMBUS_STATES.put(state, tag);
            }
            palette.add(tag.copy());
        }
        CompoundTag result = new CompoundTag();
        result.put("palette", palette);
        packed.storage().ifPresent(data -> result.put("data", new LongArrayTag(data.toArray())));
        @SuppressWarnings("unchecked")
        T tag = (T) result;
        return DataResult.success(tag);
    }
}
