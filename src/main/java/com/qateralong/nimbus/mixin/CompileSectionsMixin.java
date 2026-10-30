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
    private static final boolean NIMBUS_BENCH = System.getProperty("nimbus.bench") != null;

    @org.spongepowered.asm.mixin.Unique
    private int nimbus$debugFrames;

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

    @Inject(method = "compileSections", at = @At("HEAD"), cancellable = true)
    private void nimbus$compile(Camera camera, CallbackInfo ci) {
        if (!NimbusConfig.get().enabled) {
            return;
        }
        ci.cancel();
        var profiler = this.minecraft.getProfiler();
        profiler.push("populate_sections_to_compile");
        LevelLightEngine light = this.level.getLightEngine();
        RenderRegionCache cache = null;
        BlockPos cameraPos = camera.getBlockPosition();
        PrioritizeChunkUpdates priority = this.minecraft.options.prioritizeChunkUpdates().get();
        List<SectionRenderDispatcher.RenderSection> async = new ArrayList<>();
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
            picked = new long[listed];
            int found = 0;
            for (int i = 0; i < listed; i++) {
                int id = listedIds[i];
                SectionRenderDispatcher.RenderSection section = byId[id];
                if (section == null || ((SectionStamp) section).nimbus$visibleFrame() != stampNow) {
                    continue;
                }
                int index = order[id];
                if (index < count && sections[index] == section) {
                    picked[found++] = (long) index << 32 | id;
                }
            }
            java.util.Arrays.sort(picked, 0, found);
            count = found;
        }
        for (int i = 0; i < count; i++) {
            SectionRenderDispatcher.RenderSection section;
            if (picked != null) {
                section = sections[(int) (picked[i] >>> 32)];
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
            } else if (priority == PrioritizeChunkUpdates.PLAYER_AFFECTED) {
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
        if (++nimbus$debugFrames % 300 == 0 && System.getProperty("nimbus.bench") != null) {
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
        long deadline = System.nanoTime() + UPLOAD_BUDGET;
        int uploaded = 0;
        for (Runnable task; (forced || uploaded < MIN_UPLOADS || System.nanoTime() < deadline) && (task = uploads.poll()) != null; uploaded++) {
            task.run();
        }
        profiler.popPush("schedule_async_compile");
        for (SectionRenderDispatcher.RenderSection section : async) {
            section.rebuildSectionAsync(this.sectionRenderDispatcher, cache);
            section.setNotDirty();
        }
        profiler.pop();
    }
}
