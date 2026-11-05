package com.qateralong.nimbus.common;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;

// PoiManager.getInSquare as one flat walk: chunks x fastest then z, each column's sections bottom up, each section's
// types and records in their map order, with the same filters; nothing is read before it is asked for.
public final class PoiSquare extends Spliterators.AbstractSpliterator<PoiRecord> {
    public interface Sections {
        // the column's present sections, bottom up, loaded like getInChunk loads them
        List<PoiSection> nimbus$sections(int chunkX, int chunkZ);
    }

    public interface Types {
        Map<Holder<PoiType>, Set<PoiRecord>> nimbus$byType();
    }

    private final Sections manager;
    private final Predicate<Holder<PoiType>> type;
    private final Predicate<? super PoiRecord> status;
    private final int px;
    private final int pz;
    private final int distance;
    private final int x0;
    private final int x1;
    private final int z1;
    private int cx;
    private int cz;
    private boolean started;
    private List<PoiSection> sections;
    private int section;
    private Iterator<Map.Entry<Holder<PoiType>, Set<PoiRecord>>> types;
    private Iterator<PoiRecord> records;

    public PoiSquare(Sections manager, Predicate<Holder<PoiType>> type, Predicate<? super PoiRecord> status, BlockPos pos, int distance) {
        super(Long.MAX_VALUE, 0);
        this.manager = manager;
        this.type = type;
        this.status = status;
        this.px = pos.getX();
        this.pz = pos.getZ();
        this.distance = distance;
        int radius = Math.floorDiv(distance, 16) + 1;
        int ccx = pos.getX() >> 4;
        int ccz = pos.getZ() >> 4;
        this.x0 = ccx - radius;
        this.x1 = ccx + radius;
        this.z1 = ccz + radius;
        this.cx = this.x0;
        this.cz = ccz - radius;
    }

    @Override
    public boolean tryAdvance(Consumer<? super PoiRecord> action) {
        while (true) {
            if (this.records != null) {
                while (this.records.hasNext()) {
                    PoiRecord record = this.records.next();
                    if (this.status.test(record)) {
                        BlockPos pos = record.getPos();
                        if (Math.abs(pos.getX() - this.px) <= this.distance && Math.abs(pos.getZ() - this.pz) <= this.distance) {
                            action.accept(record);
                            return true;
                        }
                    }
                }
                this.records = null;
            }
            if (this.types != null) {
                if (this.types.hasNext()) {
                    Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry = this.types.next();
                    if (this.type.test(entry.getKey())) {
                        this.records = entry.getValue().iterator();
                    }
                    continue;
                }
                this.types = null;
            }
            if (this.sections != null && this.section < this.sections.size()) {
                this.types = ((Types) this.sections.get(this.section++)).nimbus$byType().entrySet().iterator();
                continue;
            }
            if (this.started) {
                if (this.cx == this.x1) {
                    if (this.cz == this.z1) {
                        return false;
                    }
                    this.cx = this.x0;
                    this.cz++;
                } else {
                    this.cx++;
                }
            }
            this.started = true;
            this.sections = this.manager.nimbus$sections(this.cx, this.cz);
            this.section = 0;
        }
    }
}
