package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.PoiColumns;
import com.qateralong.nimbus.common.PoiSquare;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// Villager job, bed and bell searches walk every section of every chunk in range; read a column at once instead.
// Same sections in the same order; sections not stored yet still go through getOrLoad.
@Mixin(PoiManager.class)
public abstract class PoiManagerChunkMixin extends SectionStorage<PoiSection> implements PoiSquare.Sections {
    private PoiManagerChunkMixin() {
        super(null, null, null, null, null, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PoiSection> nimbus$sections(int chunkX, int chunkZ) {
        int min = this.levelHeightAccessor.getMinSection();
        int max = this.levelHeightAccessor.getMaxSection();
        Optional<?>[] column = ((PoiColumns) this).nimbus$column(ChunkPos.asLong(chunkX, chunkZ));
        if (column != null && column.length != max - min) {
            column = null;
        }
        List<PoiSection> present = List.of();
        for (int y = min; y < max; y++) {
            Optional<PoiSection> section = column == null ? null : (Optional<PoiSection>) column[y - min];
            if (section == null) {
                section = this.getOrLoad(SectionPos.asLong(chunkX, y, chunkZ));
            }
            if (section.isPresent()) {
                if (present.isEmpty()) {
                    present = new ArrayList<>(4);
                }
                present.add(section.get());
            }
        }
        return present;
    }

    /**
     * @author qateralong
     * @reason one column lookup instead of one hash lookup per section
     */
    @Overwrite
    public Stream<PoiRecord> getInChunk(Predicate<Holder<PoiType>> typePredicate, ChunkPos posChunk, PoiManager.Occupancy status) {
        List<PoiSection> present = this.nimbus$sections(posChunk.x, posChunk.z);
        if (present.isEmpty()) {
            return Stream.empty();
        }
        return present.stream().flatMap(section -> section.getRecords(typePredicate, status));
    }

    /**
     * @author qateralong
     * @reason one flat lazy walk instead of a chunk stream with nested flatMaps; same records in the same order
     */
    @Overwrite
    public Stream<PoiRecord> getInSquare(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status) {
        return StreamSupport.stream(new PoiSquare(this, typePredicate, status.getTest(), pos, distance), false);
    }
}
