package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.render.Origins;
import com.qateralong.nimbus.render.SectionStamp;
import com.qateralong.nimbus.render.Visibility;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionOcclusionGraph.class)
public abstract class FrustumCollectMixin {
    @Shadow
    @Final
    private AtomicReference<SectionOcclusionGraph.GraphState> currentGraph;

    @Unique
    private SectionOcclusionGraph.GraphStorage nimbus$storage;

    @Unique
    private int nimbus$size = -1;

    @Unique
    private int nimbus$moves = -1;

    @Unique
    private SectionRenderDispatcher.RenderSection[] nimbus$sections = new SectionRenderDispatcher.RenderSection[0];

    @Unique
    private int[] nimbus$ids = new int[0];

    @Unique
    private int[] nimbus$columnOf = new int[0];

    @Unique
    private int[] nimbus$columns = new int[0];

    @Unique
    private int[] nimbus$states = new int[0];

    @Unique
    private int nimbus$columnCount;

    @Unique
    private final Long2IntOpenHashMap nimbus$columnIndex = new Long2IntOpenHashMap();

    @Inject(method = "addSectionsInFrustum", at = @At("HEAD"), cancellable = true)
    private void nimbus$collect(Frustum frustum, List<SectionRenderDispatcher.RenderSection> output, CallbackInfo ci) {
        SectionOcclusionGraph.GraphStorage storage = this.currentGraph.get().storage();
        LinkedHashSet<SectionOcclusionGraph.Node> nodes = storage.renderSections;
        int size = nodes.size();
        int moves = Origins.moves;
        if (storage != this.nimbus$storage || size != this.nimbus$size || moves != this.nimbus$moves) {
            this.nimbus$rebuild(nodes, size);
            this.nimbus$storage = storage;
            this.nimbus$size = size;
            this.nimbus$moves = moves;
        }
        Visibility.begin();
        FrustumIntersection intersection = frustum.intersection;
        double cx = frustum.camX;
        double cy = frustum.camY;
        double cz = frustum.camZ;
        int[] columns = this.nimbus$columns;
        int[] states = this.nimbus$states;
        for (int c = 0, at = 0; c < this.nimbus$columnCount; c++, at += 4) {
            float x = (float) (columns[at] - cx);
            float z = (float) (columns[at + 2] - cz);
            states[c] = intersection.intersectAab(x, (float) (columns[at + 1] - cy), z, x + 16.0F, (float) (columns[at + 3] + 16 - cy), z + 16.0F);
        }
        SectionRenderDispatcher.RenderSection[] sections = this.nimbus$sections;
        int[] ids = this.nimbus$ids;
        int[] columnOf = this.nimbus$columnOf;
        int[] origins = Origins.values();
        for (int i = 0; i < size; i++) {
            int state = states[columnOf[i]];
            if (state >= 0) {
                continue;
            }
            if (state != FrustumIntersection.INSIDE) {
                int head = ids[i] * 4;
                float x = (float) (origins[head] - cx);
                float y = (float) (origins[head + 1] - cy);
                float z = (float) (origins[head + 2] - cz);
                if (!intersection.testAab(x, y, z, x + 16.0F, y + 16.0F, z + 16.0F)) {
                    continue;
                }
            }
            SectionRenderDispatcher.RenderSection section = sections[i];
            Visibility.add(section);
            output.add(section);
        }
        ci.cancel();
    }

    @Unique
    private void nimbus$rebuild(LinkedHashSet<SectionOcclusionGraph.Node> nodes, int size) {
        SectionRenderDispatcher.RenderSection[] sections = this.nimbus$sections;
        if (sections.length < size) {
            int capacity = Math.max(size, sections.length * 2);
            sections = new SectionRenderDispatcher.RenderSection[capacity];
            this.nimbus$ids = new int[capacity];
            this.nimbus$columnOf = new int[capacity];
        } else {
            java.util.Arrays.fill(sections, size, sections.length, null);
        }
        int[] ids = this.nimbus$ids;
        int[] columnOf = this.nimbus$columnOf;
        int[] origins = Origins.values();
        Long2IntOpenHashMap index = this.nimbus$columnIndex;
        index.clear();
        index.defaultReturnValue(-1);
        int[] columns = this.nimbus$columns;
        int count = 0;
        int i = 0;
        for (SectionOcclusionGraph.Node node : nodes) {
            SectionRenderDispatcher.RenderSection section = node.section;
            int id = ((SectionStamp) section).nimbus$id();
            int head = id * 4;
            int x = origins[head];
            int y = origins[head + 1];
            int z = origins[head + 2];
            long key = (long) (x >> 4) << 32 | (z >> 4) & 0xFFFFFFFFL;
            int column = index.get(key);
            if (column < 0) {
                column = count++;
                index.put(key, column);
                if (column * 4 + 4 > columns.length) {
                    columns = java.util.Arrays.copyOf(columns, Math.max(256, columns.length * 2));
                }
                columns[column * 4] = x;
                columns[column * 4 + 1] = y;
                columns[column * 4 + 2] = z;
                columns[column * 4 + 3] = y;
            } else {
                columns[column * 4 + 1] = Math.min(columns[column * 4 + 1], y);
                columns[column * 4 + 3] = Math.max(columns[column * 4 + 3], y);
            }
            sections[i] = section;
            ids[i] = id;
            columnOf[i] = column;
            i++;
        }
        this.nimbus$sections = sections;
        this.nimbus$columns = columns;
        this.nimbus$columnCount = count;
        if (this.nimbus$states.length < count) {
            this.nimbus$states = new int[Math.max(count, this.nimbus$states.length * 2)];
        }
    }
}
