package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.render.SectionStamp;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class CompileSectionsMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final long UPLOAD_BUDGET = 2_500_000L;

    @org.spongepowered.asm.mixin.Unique
    private static final int MIN_UPLOADS = 8;

    @org.spongepowered.asm.mixin.Unique
    private static final boolean NIMBUS_BENCH = (System.getProperty("nimbus.bench") != null && !"prod".equals(System.getProperty("nimbus.bench")));

    @org.spongepowered.asm.mixin.Unique
    private int nimbus$debugFrames;

    @org.spongepowered.asm.mixin.Unique
    private final List<SectionRenderDispatcher.RenderSection> nimbus$async = new ArrayList<>();

    @org.spongepowered.asm.mixin.Unique
    private long[] nimbus$picked = new long[256];

    @org.spongepowered.asm.mixin.Unique
    private int nimbus$workers;

    @org.spongepowered.asm.mixin.Unique
    private long nimbus$lastFrame;

    @org.spongepowered.asm.mixin.Unique
    private long nimbus$frameNanos;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private ClientLevel level;

    @Shadow
    @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Shadow
    private SectionRenderDispatcher sectionRenderDispatcher;

    @org.spongepowered.asm.mixin.Unique
    private int nimbus$auditFrames;

    @org.spongepowered.asm.mixin.Unique
    private long nimbus$auditLogged;

    // Safety net: a dirty section the fast dirty list lost would never be built again.
    @org.spongepowered.asm.mixin.Unique
    private void nimbus$audit() {
        int lost = 0;
        for (int i = 0, size = this.visibleSections.size(); i < size; i++) {
            SectionRenderDispatcher.RenderSection section = this.visibleSections.get(i);
            int id = ((SectionStamp) section).nimbus$id();
            if (section.isDirty() && !com.qateralong.nimbus.render.SectionData.tracked(id)) {
                com.qateralong.nimbus.render.SectionData.setDirty(id, true);
                lost++;
            }
        }
        if (lost > 0) {
            com.qateralong.nimbus.render.SectionData.recovered += lost;
            long now = System.currentTimeMillis();
            if (now - this.nimbus$auditLogged > 10_000L) {
                this.nimbus$auditLogged = now;
                Nimbus.LOGGER.warn("Nimbus: {} lost section updates recovered ({} total)", lost, com.qateralong.nimbus.render.SectionData.recovered);
            }
        }
    }

    @Inject(method = "compileSections", at = @At("HEAD"), cancellable = true)
    private void nimbus$compile(Camera camera, CallbackInfo ci) {
        com.qateralong.nimbus.render.SectionData.drain();
        if (!NimbusConfig.get().enabled) {
            return;
        }
        ci.cancel();
        if (++this.nimbus$auditFrames >= 20) {
            this.nimbus$auditFrames = 0;
            this.nimbus$audit();
        }
        var profiler = this.minecraft.getProfiler();
        profiler.push("populate_sections_to_compile");
        LevelLightEngine light = this.level.getLightEngine();
        RenderRegionCache cache = null;
        BlockPos cameraPos = camera.getBlockPosition();
        PrioritizeChunkUpdates priority = this.minecraft.options.prioritizeChunkUpdates().get();
        List<SectionRenderDispatcher.RenderSection> async = this.nimbus$async;
        async.clear();
        boolean forced = false;
        int frame = Nimbus.frames;
        boolean tracked = com.qateralong.nimbus.render.Visibility.valid && com.qateralong.nimbus.render.Visibility.allCount == this.visibleSections.size();
        int count = tracked ? com.qateralong.nimbus.render.Visibility.allCount : this.visibleSections.size();
        int[] ids = com.qateralong.nimbus.render.Visibility.allIds;
        SectionRenderDispatcher.RenderSection[] sections = com.qateralong.nimbus.render.Visibility.allSections;
        boolean[] dirtyIds = com.qateralong.nimbus.render.SectionData.dirty;
        long[] picked = null;
        if (tracked && com.qateralong.nimbus.render.SectionData.dirtyCount <= 2048) {
            com.qateralong.nimbus.render.SectionData.compactDirty();
            int listed = com.qateralong.nimbus.render.SectionData.dirtyListed;
            int[] listedIds = com.qateralong.nimbus.render.SectionData.dirtyIds;
            int[] order = com.qateralong.nimbus.render.SectionData.order;
            SectionRenderDispatcher.RenderSection[] byId = com.qateralong.nimbus.render.SectionData.sections;
            int stampNow = com.qateralong.nimbus.render.Visibility.stamp;
            int[] prefetch = com.qateralong.nimbus.render.SectionData.prefetchFrame;
            if (this.nimbus$picked.length < listed) {
                this.nimbus$picked = new long[Math.max(listed, this.nimbus$picked.length * 2)];
            }
            picked = this.nimbus$picked;
            int found = 0;
            for (int i = 0; i < listed; i++) {
                int id = listedIds[i];
                SectionRenderDispatcher.RenderSection section = byId[id];
                if (section == null) {
                    continue;
                }
                boolean shown = ((SectionStamp) section).nimbus$visibleFrame() == stampNow;
                if (!shown && (id >= prefetch.length || prefetch[id] != stampNow)) {
                    continue;
                }
                int index = order[id];
                if (shown ? index < count && sections[index] == section : (index & com.qateralong.nimbus.render.SectionData.PREFETCH) != 0) {
                    picked[found++] = (long) index << 32 | id;
                }
            }
            java.util.Arrays.sort(picked, 0, found);
            count = found;
        }
        for (int i = 0; i < count; i++) {
            SectionRenderDispatcher.RenderSection section;
            if (picked != null) {
                section = com.qateralong.nimbus.render.SectionData.sections[(int) picked[i]];
            } else {
                if (tracked) {
                    int id = ids[i];
                    if (id < dirtyIds.length && !dirtyIds[id]) {
                        continue;
                    }
                }
                section = tracked ? sections[i] : this.visibleSections.get(i);
            }
            if (!section.isDirty()) {
                if (picked != null) {
                    com.qateralong.nimbus.render.SectionData.setDirty(((SectionStamp) section).nimbus$id(), false);
                }
                continue;
            }
            SectionStamp stamp = (SectionStamp) section;
            if (stamp.nimbus$lightRetry() > frame) {
                continue;
            }
            if (!light.lightOnInSection(SectionPos.of(section.getOrigin()))) {
                stamp.nimbus$setLightRetry(frame + 20);
                continue;
            }
            boolean sync = false;
            if (priority == PrioritizeChunkUpdates.NEARBY) {
                BlockPos center = section.getOrigin().offset(8, 8, 8);
                sync = center.distSqr(cameraPos) < 768.0 || section.isDirtyFromPlayer();
            } else {
                // player edits show up in the same frame whatever the vanilla priority option says
                sync = section.isDirtyFromPlayer();
            }
            if (cache == null) {
                cache = new RenderRegionCache();
            }
            if (sync) {
                profiler.push("build_near_sync");
                this.sectionRenderDispatcher.rebuildSectionSync(section, cache);
                section.setNotDirty();
                forced = true;
                profiler.pop();
            } else {
                async.add(section);
            }
        }
        if (++nimbus$debugFrames % 300 == 0 && NIMBUS_BENCH) {
            int vanillaDirty = 0;
            int ourDirty = 0;
            for (int i = 0; i < this.visibleSections.size(); i++) {
                SectionRenderDispatcher.RenderSection section = this.visibleSections.get(i);
                if (section.isDirty()) {
                    vanillaDirty++;
                    int id = ((SectionStamp) section).nimbus$id();
                    if (id < dirtyIds.length && dirtyIds[id]) {
                        ourDirty++;
                    }
                }
            }
            Nimbus.LOGGER.info("Nimbus compile: tracked " + tracked + ", all " + com.qateralong.nimbus.render.Visibility.allCount + ", visible "
                    + this.visibleSections.size() + ", dirty " + vanillaDirty + "/" + ourDirty + ", scheduled " + async.size() + ", light " + light.getClass().getSimpleName());
        }
        profiler.popPush("upload");
        java.util.Queue<Runnable> uploads = ((com.qateralong.nimbus.render.UploadQueue) this.sectionRenderDispatcher).nimbus$uploads();
        long now = System.nanoTime();
        if (this.nimbus$lastFrame != 0L) {
            long frameNanos = Math.min(100_000_000L, now - this.nimbus$lastFrame);
            this.nimbus$frameNanos = this.nimbus$frameNanos == 0L ? frameNanos : (this.nimbus$frameNanos * 15 + frameNanos) / 16;
        }
        this.nimbus$lastFrame = now;
        // a quarter of the average frame, so uploads keep pace at low FPS without eating high-FPS frames
        long budget = this.nimbus$frameNanos == 0L ? UPLOAD_BUDGET : Math.max(1_000_000L, Math.min(4_000_000L, this.nimbus$frameNanos / 4));
        long deadline = now + budget;
        int uploaded = 0;
        for (Runnable task; (forced || uploaded < MIN_UPLOADS || System.nanoTime() < deadline) && (task = uploads.poll()) != null; uploaded++) {
            task.run();
        }
        profiler.popPush("schedule_async_compile");
        // Short queue re-picked every frame: vanilla snapshots blocks at scheduling time and sorts by stale distance.
        int free = this.sectionRenderDispatcher.getFreeBufferCount();
        if (free > this.nimbus$workers) {
            this.nimbus$workers = free;
        }
        int room = Math.max(4, this.nimbus$workers * 2) - this.sectionRenderDispatcher.getToBatchCount();
        int scheduled = 0;
        for (int pass = 0; pass < 2 && scheduled < room; pass++) {
            for (int i = 0, size = async.size(); i < size && scheduled < room; i++) {
                SectionRenderDispatcher.RenderSection section = async.get(i);
                if (section == null || section.isDirtyFromPlayer() != (pass == 0)) {
                    continue;
                }
                section.rebuildSectionAsync(this.sectionRenderDispatcher, cache);
                section.setNotDirty();
                async.set(i, null);
                scheduled++;
            }
        }
        async.clear();
        profiler.pop();
    }
}
