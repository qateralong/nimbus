package com.qateralong.nimbus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Same traversal as vanilla, with indexed loops instead of an iterator over children and cubes for every part drawn.
@Mixin(value = ModelPart.class, priority = 900)
public abstract class ModelPartMixin {
    @Unique
    private static final ModelPart[] NIMBUS_NONE = new ModelPart[0];

    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;

    @Shadow
    @Final
    private Map<String, ModelPart> children;

    @Shadow
    public boolean visible;

    @Shadow
    public boolean skipDraw;

    @Unique
    private ModelPart[] nimbus$children;

    @Shadow
    public abstract void translateAndRotate(PoseStack poseStack);

    /**
     * @author qateralong
     * @reason no iterator allocation per drawn part
     */
    @Overwrite
    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        if (!this.visible) {
            return;
        }
        ModelPart[] children = this.nimbus$children();
        if (this.cubes.isEmpty() && children.length == 0) {
            return;
        }
        poseStack.pushPose();
        this.translateAndRotate(poseStack);
        if (!this.skipDraw) {
            this.compile(poseStack.last(), consumer, light, overlay, color);
        }
        for (ModelPart child : children) {
            child.render(poseStack, consumer, light, overlay, color);
        }
        poseStack.popPose();
    }

    /**
     * @author qateralong
     * @reason no iterator allocation per drawn part
     */
    @Overwrite
    private void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        List<ModelPart.Cube> cubes = this.cubes;
        if (cubes instanceof java.util.RandomAccess) {
            for (int i = 0, n = cubes.size(); i < n; i++) {
                cubes.get(i).compile(pose, consumer, light, overlay, color);
            }
        } else {
            for (ModelPart.Cube cube : cubes) {
                cube.compile(pose, consumer, light, overlay, color);
            }
        }
    }

    // the map is fixed after baking; a size check still catches mods that edit it later
    @Unique
    private ModelPart[] nimbus$children() {
        ModelPart[] cached = this.nimbus$children;
        int size = this.children.size();
        if (cached == null || cached.length != size) {
            cached = size == 0 ? NIMBUS_NONE : this.children.values().toArray(new ModelPart[0]);
            this.nimbus$children = cached;
        }
        return cached;
    }
}
