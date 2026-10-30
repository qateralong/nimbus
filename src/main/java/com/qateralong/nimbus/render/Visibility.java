package com.qateralong.nimbus.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

public final class Visibility {
    public static final ObjectArrayList<SectionRenderDispatcher.RenderSection> WITH_BLOCK_ENTITIES = new ObjectArrayList<>();
    public static int stamp;
    public static int changes;
    private static int membership = -1;
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
        changes++;
        membership = SectionData.membership;
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
        if (id < SectionData.order.length) {
            SectionData.order[id] = allCount;
        }
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
        int size = sections.size();
        if (valid && allCount == size) {
            refresh();
            return;
        }
        if (valid && allCount > 0 && allCount < size && sections.get(allCount - 1) == allSections[allCount - 1] && sections.get(0) == allSections[0]) {
            for (int i = allCount; i < size; i++) {
                add(sections.get(i));
            }
            changes++;
            refresh();
            return;
        }
        begin();
        for (int i = 0; i < size; i++) {
            add(sections.get(i));
        }
    }

    public static void refresh() {
        int current = SectionData.membership;
        if (!valid || current == membership) {
            return;
        }
        membership = current;
        changes++;
        count = 0;
        WITH_BLOCK_ENTITIES.clear();
        for (int i = 0; i < allCount; i++) {
            int id = allIds[i];
            if (id >= SectionData.mask.length) {
                continue;
            }
            if (SectionData.mask[id] != 0) {
                if (count == ids.length) {
                    ids = java.util.Arrays.copyOf(ids, ids.length * 2);
                }
                ids[count++] = id;
            }
            if (SectionData.blockEntities[id]) {
                WITH_BLOCK_ENTITIES.add(allSections[i]);
            }
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
