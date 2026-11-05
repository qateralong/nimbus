package com.qateralong.nimbus.common.mixin;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Open-addressing maps for compound tags: no node object per entry, same Map equals/hashCode contract.
@Mixin(CompoundTag.class)
public abstract class CompoundTagMapMixin {
    @Shadow
    @Final
    private Map<String, Tag> tags;

    @ModifyArg(method = "<init>()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;<init>(Ljava/util/Map;)V"))
    private static Map<String, Tag> nimbus$map(Map<String, Tag> tags) {
        return new Object2ObjectOpenHashMap<>();
    }

    /**
     * @author qateralong
     * @reason open-addressing map
     */
    @Overwrite
    public CompoundTag shallowCopy() {
        return CompoundTagAccessor.nimbus$create(new Object2ObjectOpenHashMap<>(this.tags));
    }

    /**
     * @author qateralong
     * @reason presized open-addressing map without the transformed view
     */
    @Overwrite
    public CompoundTag copy() {
        Object2ObjectOpenHashMap<String, Tag> copy = new Object2ObjectOpenHashMap<>(this.tags.size());
        for (Map.Entry<String, Tag> entry : this.tags.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }
        return CompoundTagAccessor.nimbus$create(copy);
    }
}
