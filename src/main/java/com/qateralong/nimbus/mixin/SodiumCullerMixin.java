package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import java.util.ArrayList;
import java.util.List;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.RenderSectionVisitor;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import net.minecraft.core.SectionPos;
import org.joml.FrustumIntersection;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = OcclusionCuller.class, remap = false)
public abstract class SodiumCullerMixin {
    @Unique
    private static final Frustum NIMBUS_EVERYTHING = new Frustum() {
        @Override
        public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            return true;
        }

        @Override
        public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            return FrustumIntersection.INSIDE;
        }

        @Override
        public boolean testSection(float x, float y, float z) {
            return true;
        }

        @Override
        public boolean testSectionExpanded(float x, float y, float z, float margin) {
            return true;
        }
    };

    @Unique
    private static final int STABLE_FRAMES = 5;

    @Unique
    private final List<RenderSection> nimbus$reachable = new ArrayList<>();

    @Unique
    private long nimbus$key = Long.MIN_VALUE;

    @Unique
    private int nimbus$version = -1;

    @Unique
    private boolean nimbus$recording;

    @Unique
    private long nimbus$seenKey = Long.MIN_VALUE;

    @Unique
    private int nimbus$seenVersion = -1;

    @Unique
    private int nimbus$stable;

    @Unique
    private boolean nimbus$valid;

    @Inject(method = "findVisible", at = @At("HEAD"), cancellable = true, require = 0)
    private void nimbus$cached(RenderSectionVisitor visitor, Viewport viewport, float distance, boolean occlusion, int frame, CallbackInfo ci) {
        if (this.nimbus$recording || !NimbusConfig.get().enabled || !NimbusConfig.get().sodiumGraphCache) {
            this.nimbus$valid = false;
            return;
        }
        SectionPos camera = viewport.getChunkCoord();
        long key = camera.asLong() * 31 + Float.floatToIntBits(distance) * 2L + (occlusion ? 1 : 0);
        int version = com.qateralong.nimbus.render.SodiumGraph.version;
        if (key != this.nimbus$seenKey || version != this.nimbus$seenVersion) {
            this.nimbus$seenKey = key;
            this.nimbus$seenVersion = version;
            this.nimbus$stable = 0;
        } else if (this.nimbus$stable < STABLE_FRAMES) {
            this.nimbus$stable++;
        }
        boolean current = this.nimbus$valid && key == this.nimbus$key && version == this.nimbus$version;
        if (!current) {
            this.nimbus$valid = false;
            if (this.nimbus$stable < STABLE_FRAMES) {
                return;
            }
            this.nimbus$reachable.clear();
            CameraTransform transform = viewport.getTransform();
            Viewport everything = new Viewport(NIMBUS_EVERYTHING, new Vector3d(transform.x, transform.y, transform.z));
            this.nimbus$recording = true;
            try {
                ((OcclusionCuller) (Object) this).findVisible(this.nimbus$reachable::add, everything, distance, occlusion, frame);
            } finally {
                this.nimbus$recording = false;
            }
            this.nimbus$key = key;
            this.nimbus$version = version;
            this.nimbus$valid = true;
        }
        CameraTransform transform = viewport.getTransform();
        int cx = camera.x();
        int cy = camera.y();
        int cz = camera.z();
        for (int i = 0, size = this.nimbus$reachable.size(); i < size; i++) {
            RenderSection section = this.nimbus$reachable.get(i);
            if (!SodiumCullerAccessor.nimbus$withinDistance(transform, section, distance)) {
                continue;
            }
            boolean near = Math.abs(section.getChunkX() - cx) <= 1 && Math.abs(section.getChunkY() - cy) <= 1 && Math.abs(section.getChunkZ() - cz) <= 1;
            if (near ? OcclusionCuller.isWithinNearbySectionFrustum(viewport, section) : OcclusionCuller.isWithinFrustum(viewport, section)) {
                section.setLastVisibleFrame(frame);
                visitor.visit(section);
            }
        }
        ci.cancel();
    }
}
