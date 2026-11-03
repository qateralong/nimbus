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
    private static int[] inIds = new int[4096];
    private static int idsRound = 1;
    public static int[] allIds = new int[4096];
    public static SectionRenderDispatcher.RenderSection[] allSections = new SectionRenderDispatcher.RenderSection[4096];
    public static int allCount;

    private Visibility() {
    }

    public static void begin() {
        SectionData.clearPending();
        stamp++;
        changes++;
        membership = SectionData.membership;
        count = 0;
        idsRound++;
        java.util.Arrays.fill(allSections, 0, allCount, null);
        allCount = 0;
        WITH_BLOCK_ENTITIES.clear();
        valid = true;
    }

    public static void add(SectionRenderDispatcher.RenderSection section) {
        add(section, ((SectionStamp) section).nimbus$id());
    }

    public static void add(SectionRenderDispatcher.RenderSection section, int id) {
        SectionData.visibleFrame[id] = stamp;
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
                push(id);
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
        SectionData.clearPending();
        changes++;
        count = 0;
        idsRound++;
        WITH_BLOCK_ENTITIES.clear();
        for (int i = 0; i < allCount; i++) {
            int id = allIds[i];
            if (id >= SectionData.mask.length) {
                continue;
            }
            if (SectionData.mask[id] != 0) {
                push(id);
            }
            if (SectionData.blockEntities[id]) {
                WITH_BLOCK_ENTITIES.add(allSections[i]);
            }
        }
    }

    // a section whose geometry went away keeps its place in ids, so it must not be appended a second time
    private static boolean push(int id) {
        if (id >= inIds.length) {
            inIds = java.util.Arrays.copyOf(inIds, Math.max(inIds.length * 2, id + 1));
        }
        if (inIds[id] == idsRound) {
            return false;
        }
        inIds[id] = idsRound;
        if (count == ids.length) {
            ids = java.util.Arrays.copyOf(ids, ids.length * 2);
        }
        ids[count++] = id;
        return true;
    }

    static void append(int id) {
        if (!push(id)) {
            return;
        }
        if (SectionData.blockEntities[id] && SectionData.sections[id] != null) {
            WITH_BLOCK_ENTITIES.add(SectionData.sections[id]);
        }
    }

    private static final ObjectArrayList<SectionRenderDispatcher.RenderSection> IN_VIEW = new ObjectArrayList<>();

    public static ObjectArrayList<SectionRenderDispatcher.RenderSection> blockEntitiesInView() {
        return IN_VIEW;
    }

    // With GPU frustum culling the shown set is the widened search, so block entities still need a CPU frustum test.
    static void cullBlockEntities(float[] planes, double cameraX, double cameraY, double cameraZ) {
        IN_VIEW.clear();
        for (int s = 0, size = WITH_BLOCK_ENTITIES.size(); s < size; s++) {
            SectionRenderDispatcher.RenderSection section = WITH_BLOCK_ENTITIES.get(s);
            net.minecraft.core.BlockPos origin = section.getOrigin();
            float lowX = (float) (origin.getX() - cameraX) - 2.0F;
            float lowY = (float) (origin.getY() - cameraY) - 2.0F;
            float lowZ = (float) (origin.getZ() - cameraZ) - 2.0F;
            float highX = lowX + 20.0F;
            float highY = lowY + 20.0F;
            float highZ = lowZ + 20.0F;
            boolean inside = true;
            for (int p = 0; p < 6 && inside; p++) {
                float a = planes[p * 4];
                float b = planes[p * 4 + 1];
                float c = planes[p * 4 + 2];
                inside = a * (a >= 0.0F ? highX : lowX) + b * (b >= 0.0F ? highY : lowY) + c * (c >= 0.0F ? highZ : lowZ) + planes[p * 4 + 3] >= 0.0F;
            }
            if (inside) {
                IN_VIEW.add(section);
            }
        }
    }

    public static void invalidate() {
        valid = false;
        WITH_BLOCK_ENTITIES.clear();
        count = 0;
        idsRound++;
        java.util.Arrays.fill(allSections, 0, allCount, null);
        allCount = 0;
    }
}
