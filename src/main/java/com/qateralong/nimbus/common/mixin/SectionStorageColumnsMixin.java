package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.PoiColumns;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Optional;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Every section stored is also filed under its chunk column, so a column is one lookup instead of one per section.
// Sections are only ever put, never removed, in this storage.
@Mixin(SectionStorage.class)
public abstract class SectionStorageColumnsMixin<R> implements PoiColumns {
    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Unique
    private final Long2ObjectOpenHashMap<Optional<?>[]> nimbus$columns = new Long2ObjectOpenHashMap<>();

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;put(JLjava/lang/Object;)Ljava/lang/Object;", remap = false))
    private Object nimbus$put(Long2ObjectMap<Object> storage, long key, Object value) {
        Object previous = storage.put(key, value);
        int min = this.levelHeightAccessor.getMinSection();
        int count = this.levelHeightAccessor.getMaxSection() - min;
        int y = SectionPos.y(key) - min;
        if (y >= 0 && y < count) {
            long chunk = ChunkPos.asLong(SectionPos.x(key), SectionPos.z(key));
            Optional<?>[] column = this.nimbus$columns.get(chunk);
            if (column == null) {
                column = new Optional<?>[count];
                this.nimbus$columns.put(chunk, column);
            }
            column[y] = (Optional<?>) value;
        }
        return previous;
    }

    @Override
    public Optional<?>[] nimbus$column(long chunk) {
        return this.nimbus$columns.get(chunk);
    }
}
