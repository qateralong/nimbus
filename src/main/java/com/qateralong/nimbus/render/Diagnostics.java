package com.qateralong.nimbus.render;

import com.qateralong.nimbus.mixin.LevelRendererAccessor;
import com.qateralong.nimbus.mixin.ViewAreaAccessor;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

// Engine state for bug reports: counters, sections around the camera and consistency checks between
// vanilla's view of a section and what the engine stores and draws.
public final class Diagnostics {
    private static final int MAX_PROBLEMS = 200;

    private Diagnostics() {
    }

    public static String report(Minecraft minecraft) {
        StringBuilder out = new StringBuilder(64 * 1024);
        line(out, "active %s, meshing %s, traversal %s, gpu frustum %s", Terrain.active(), Terrain.meshing(), Traversal.enabled(), Terrain.gpuFrustum());
        String terrain = Terrain.debug();
        if (terrain != null) {
            line(out, "%s", terrain);
        }
        line(out, "visibility valid %s, stamp %d, changes %d, all %d, with geometry %d", Visibility.valid, Visibility.stamp, Visibility.changes,
                Visibility.allCount, Visibility.count);
        line(out, "dirty %d listed %d, recovered updates %d, rebuilt lost geometry %d, graph %d changed %d gains %d, version %d membership %d", SectionData.dirtyCount,
                SectionData.dirtyListed, SectionData.recovered, SectionData.repaired, SectionData.graph, SectionData.changed, SectionData.gains, SectionData.version,
                SectionData.membership);
        line(out, "%s", com.qateralong.nimbus.lod.LodRenderer.debug());
        line(out, "%s, particle instances drawn since start %d", EntityInstances.debug(), ParticleInstances.drawn);
        if (minecraft.levelRenderer == null || minecraft.level == null) {
            line(out, "no level");
            return out.toString();
        }
        LevelRendererAccessor renderer = (LevelRendererAccessor) minecraft.levelRenderer;
        List<SectionRenderDispatcher.RenderSection> visible = renderer.nimbus$visibleSections();
        line(out, "vanilla visible list %d, uploads queued %d, builds queued %d", visible.size(),
                ((UploadQueue) renderer.nimbus$sectionRenderDispatcher()).nimbus$uploads().size(), renderer.nimbus$sectionRenderDispatcher().getToBatchCount());
        Commands[] lists = Terrain.lists();
        List<RenderType> layers = RenderType.chunkBufferLayers();
        for (int l = 0; l < lists.length && l < layers.size(); l++) {
            line(out, "list %s: %d draws, %d quads", name(layers.get(l)), lists[l].count, lists[l].quads);
        }
        out.append('\n');
        problems(out, visible, lists, layers);
        out.append('\n');
        nearby(out, minecraft, renderer, lists, layers);
        out.append('\n');
        light(out, minecraft);
        return out.toString();
    }

    private static void problems(StringBuilder out, List<SectionRenderDispatcher.RenderSection> visible, Commands[] lists, List<RenderType> layers) {
        line(out, "== consistency of visible sections ==");
        int problems = 0;
        int[] drawn = drawnQuads(lists);
        for (SectionRenderDispatcher.RenderSection section : visible) {
            String issue = issue(section, drawn, layers);
            if (issue != null) {
                if (problems++ < MAX_PROBLEMS) {
                    BlockPos origin = section.getOrigin();
                    String history = problems <= 40 ? SectionEvents.history(((SectionStamp) section).nimbus$id(), layers) : "";
                    line(out, "%d %d %d: %s%s", origin.getX(), origin.getY(), origin.getZ(), issue, history);
                }
            }
        }
        int stale = staleDraws(out, lists, layers);
        line(out, "%d sections with problems, %d draws outside their section's storage", problems, stale);
    }

    private static String issue(SectionRenderDispatcher.RenderSection section, int[] drawn, List<RenderType> layers) {
        if (!Terrain.active()) {
            return null;
        }
        int id = ((SectionStamp) section).nimbus$id();
        SectionRenderDispatcher.CompiledSection compiled = section.getCompiled();
        StringBuilder issue = new StringBuilder();
        if (section.isDirty() && !SectionData.tracked(id)) {
            issue.append("dirty but not tracked; ");
        }
        if (compiled != SectionRenderDispatcher.CompiledSection.UNCOMPILED) {
            TerrainSlot[] slots = ((SectionStamp) section).nimbus$slots();
            for (int l = 0; l < slots.length && l < layers.size(); l++) {
                boolean has = !compiled.isEmpty(layers.get(l));
                int stored = slots[l] == null ? 0 : slots[l].nimbus$count();
                if (has != stored > 0) {
                    issue.append(name(layers.get(l))).append(has ? " compiled but not stored; " : " stored but compiled empty; ");
                }
                boolean masked = id < SectionData.mask.length && (SectionData.mask[id] >>> l & 1) != 0;
                if (masked != stored > 0) {
                    issue.append(name(layers.get(l))).append(" mask ").append(masked).append(" vs stored ").append(stored).append("; ");
                }
            }
            long expected = visibilityMask(compiled);
            if (id < SectionData.visibility.length && SectionData.visibility[id] != expected) {
                issue.append(String.format(Locale.ROOT, "visibility %x vs compiled %x; ", SectionData.visibility[id], expected));
            }
        }
        if (Visibility.valid && id < SectionData.mask.length && SectionData.mask[id] != 0 && SectionData.visibleFrame[id] == Visibility.stamp
                && id < drawn.length && drawn[id] == 0 && Terrain.fogLimit() == Float.MAX_VALUE) {
            issue.append("has geometry but no draws (or every face turned away); ");
        }
        return issue.isEmpty() ? null : issue.toString();
    }

    private static long visibilityMask(SectionRenderDispatcher.CompiledSection compiled) {
        long mask = 0L;
        net.minecraft.core.Direction[] directions = net.minecraft.core.Direction.values();
        for (int leave = 0; leave < 6; leave++) {
            for (int entry = 0; entry < 6; entry++) {
                if (compiled.facesCanSeeEachother(directions[entry], directions[leave])) {
                    mask |= 1L << (leave * 6 + entry);
                }
            }
        }
        return mask;
    }

    private static int[] drawnQuads(Commands[] lists) {
        int[] drawn = new int[SectionData.mask.length];
        for (Commands list : lists) {
            for (int i = 0; i < list.count; i++) {
                int id = list.instance(i);
                if (id >= 0 && id < drawn.length) {
                    drawn[id] += list.quads(i);
                }
            }
        }
        return drawn;
    }

    private static int staleDraws(StringBuilder out, Commands[] lists, List<RenderType> layers) {
        int stale = 0;
        int[] records = SectionData.records;
        for (int l = 0; l < lists.length; l++) {
            Commands list = lists[l];
            for (int i = 0; i < list.count; i++) {
                int id = list.instance(i);
                int at = id * SectionData.ID_STRIDE + SectionData.HEADER + l * SectionData.STRIDE;
                if (id < 0 || at + SectionData.STRIDE > records.length) {
                    stale++;
                    continue;
                }
                int start = records[at + SectionData.START];
                int count = records[at + SectionData.COUNT];
                int first = list.first(i);
                if (first < start || first + list.quads(i) > start + count) {
                    if (stale++ < 20) {
                        line(out, "stale draw: %s id %d quads %d..%d, stored %d..%d", name(layers.get(l)), id, first, first + list.quads(i), start, start + count);
                    }
                }
            }
        }
        return stale;
    }

    private static void nearby(StringBuilder out, Minecraft minecraft, LevelRendererAccessor renderer, Commands[] lists, List<RenderType> layers) {
        line(out, "== sections around the camera (x y z: compiled, dirty, listed, layers stored/drawn, visibility) ==");
        var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        int cx = SectionPos.posToSectionCoord(camera.x);
        int cy = SectionPos.posToSectionCoord(camera.y);
        int cz = SectionPos.posToSectionCoord(camera.z);
        ViewAreaAccessor area = (ViewAreaAccessor) renderer.nimbus$viewArea();
        if (area == null) {
            return;
        }
        int[] drawn = drawnQuads(lists);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    SectionRenderDispatcher.RenderSection section = area.nimbus$sectionAt(pos.set((cx + dx) << 4, (cy + dy) << 4, (cz + dz) << 4));
                    if (section == null) {
                        continue;
                    }
                    int id = ((SectionStamp) section).nimbus$id();
                    SectionRenderDispatcher.CompiledSection compiled = section.getCompiled();
                    String state = compiled == SectionRenderDispatcher.CompiledSection.UNCOMPILED ? "uncompiled"
                            : compiled == SectionRenderDispatcher.CompiledSection.EMPTY ? "empty" : compiled.hasNoRenderableLayers() ? "no layers" : "built";
                    StringBuilder stored = new StringBuilder();
                    TerrainSlot[] slots = ((SectionStamp) section).nimbus$slots();
                    for (int l = 0; l < slots.length && l < layers.size(); l++) {
                        int count = slots[l] == null ? 0 : slots[l].nimbus$count();
                        if (count > 0 || !compiled.isEmpty(layers.get(l))) {
                            stored.append(name(layers.get(l))).append('=').append(count).append(compiled.isEmpty(layers.get(l)) ? "(empty)" : "").append(' ');
                        }
                    }
                    BlockPos origin = section.getOrigin();
                    line(out, "%d %d %d id %d: %s, dirty %s/%s, listed %s, stored [%s] drawn %d, vis %x, issue %s", origin.getX(), origin.getY(),
                            origin.getZ(), id, state, section.isDirty(), SectionData.tracked(id),
                            id < SectionData.visibleFrame.length && SectionData.visibleFrame[id] == Visibility.stamp, stored.toString().trim(),
                            id < drawn.length ? drawn[id] : -1, id < SectionData.visibility.length ? SectionData.visibility[id] : 0L,
                            issue(section, drawn, layers));
                }
            }
        }
    }

    private static void light(StringBuilder out, Minecraft minecraft) {
        if (!(minecraft.level.getLightEngine() instanceof com.qateralong.nimbus.light.ClientLight engine)) {
            line(out, "== light: vanilla engine %s ==", minecraft.level.getLightEngine().getClass().getSimpleName());
            return;
        }
        line(out, "== Nimbus light around the camera (section x y z: data, value range), stale sources rechecked %d, dark spots healed %d ==",
                com.qateralong.nimbus.light.ClientLight.repairedSources, com.qateralong.nimbus.light.ClientLight.healedSpots);
        var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        int cx = SectionPos.posToSectionCoord(camera.x);
        int cy = SectionPos.posToSectionCoord(camera.y);
        int cz = SectionPos.posToSectionCoord(camera.z);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    line(out, "%d %d %d: %s, too dark %d", (cx + dx) << 4, (cy + dy) << 4, (cz + dz) << 4, engine.describe(cx + dx, cy + dy, cz + dz),
                            engine.darkSpots(cx + dx, cy + dy, cz + dz, false));
                }
            }
        }
    }

    static String name(RenderType layer) {
        String text = layer.toString();
        int open = text.indexOf("RenderType[");
        if (open >= 0) {
            int end = text.indexOf(':', open);
            return text.substring(open + 11, end < 0 ? text.length() : end);
        }
        return text;
    }

    private static void line(StringBuilder out, String format, Object... args) {
        out.append(String.format(Locale.ROOT, format, args)).append('\n');
    }
}
