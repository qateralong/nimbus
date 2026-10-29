package com.qateralong.nimbus.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

public final class Visibility {
    public static final ObjectArrayList<SectionRenderDispatcher.RenderSection> WITH_BLOCK_ENTITIES = new ObjectArrayList<>();
    public static final Set<SectionRenderDispatcher.RenderSection> DIRTY = ConcurrentHashMap.newKeySet();
    public static int stamp;
    public static boolean valid;
    static int[] ids = new int[4096];
    static int count;
    public static int[] allIds = new int[4096];
    public static SectionRenderDispatcher.RenderSection[] allSections = new SectionRenderDispatcher.RenderSection[4096];
    public static int allCount;

    private Visibility() {
    }

    public static void begin() {
        stamp++;
        count = 0;
        java.util.Arrays.fill(allSections, 0, allCount, null);
        allCount = 0;
        WITH_BLOCK_ENTITIES.clear();
        valid = true;
    }

    public static void add(SectionRenderDispatcher.RenderSection section) {
        SectionStamp data = (SectionStamp) section;
        data.nimbus$setVisibleFrame(stamp);
        int id = data.nimbus$id();
        if (allCount == allIds.length) {
            allIds = java.util.Arrays.copyOf(allIds, allIds.length * 2);
            allSections = java.util.Arrays.copyOf(allSections, allSections.length * 2);
        }
        allIds[allCount] = id;
        allSections[allCount++] = section;
        if (id < SectionData.mask.length) {
            if (SectionData.mask[id] != 0) {
                if (count == ids.length) {
                    ids = java.util.Arrays.copyOf(ids, ids.length * 2);
                }
                ids[count++] = id;
            }
            if (SectionData.blockEntities[id]) {
                WITH_BLOCK_ENTITIES.add(section);
            }
        }
    }

    public static void sync(java.util.List<SectionRenderDispatcher.RenderSection> sections) {
        if (valid && allCount == sections.size()) {
            return;
        }
        begin();
        for (int i = 0, size = sections.size(); i < size; i++) {
            add(sections.get(i));
        }
    }

    public static void invalidate() {
        valid = false;
        WITH_BLOCK_ENTITIES.clear();
        count = 0;
        java.util.Arrays.fill(allSections, 0, allCount, null);
        allCount = 0;
    }
}
