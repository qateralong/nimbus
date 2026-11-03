package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Deque;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Every model part pushes a pose; vanilla allocates a Matrix4f and a Matrix3f each time. Popped poses are kept per stack.
@Mixin(value = PoseStack.class, priority = 900)
public abstract class PoseStackMixin {
    @Shadow
    @Final
    private Deque<PoseStack.Pose> poseStack;

    @Unique
    private ArrayDeque<PoseStack.Pose> nimbus$free;

    /**
     * @author qateralong
     * @reason reuse popped poses instead of allocating two matrices per push
     */
    @Overwrite
    public void pushPose() {
        PoseStack.Pose last = this.poseStack.getLast();
        ArrayDeque<PoseStack.Pose> free = this.nimbus$free;
        PoseStack.Pose pose = free == null ? null : free.pollLast();
        if (pose == null) {
            pose = new PoseStack.Pose(last);
        } else {
            pose.pose().set(last.pose());
            pose.normal().set(last.normal());
            pose.trustedNormals = last.trustedNormals;
        }
        this.poseStack.addLast(pose);
    }

    /**
     * @author qateralong
     * @reason the popped pose goes back to this stack's pool
     */
    @Overwrite
    public void popPose() {
        PoseStack.Pose pose = this.poseStack.removeLast();
        ArrayDeque<PoseStack.Pose> free = this.nimbus$free;
        if (free == null) {
            free = this.nimbus$free = new ArrayDeque<>(8);
        }
        if (free.size() < 64) {
            free.addLast(pose);
        }
    }
}
