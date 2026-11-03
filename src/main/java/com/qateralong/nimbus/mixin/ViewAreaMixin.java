package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.NimbusConfig;
import java.util.Arrays;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla walks every section on each camera section change; only columns whose wrapped x or z moved are touched.
@Mixin(ViewArea.class)
public abstract class ViewAreaMixin {
    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected int sectionGridSizeX;

    @Shadow
    protected int sectionGridSizeY;

    @Shadow
    protected int sectionGridSizeZ;

    @Shadow
    public SectionRenderDispatcher.RenderSection[] sections;

    @Unique
    private int[] nimbus$columnX;

    @Unique
    private int[] nimbus$columnZ;

    @Unique
    private SectionRenderDispatcher.RenderSection[] nimbus$owner;

    @Inject(method = "repositionCamera", at = @At("HEAD"), cancellable = true)
    private void nimbus$reposition(double cameraX, double cameraZ, CallbackInfo ci) {
        if (!NimbusConfig.get().enabled) {
            this.nimbus$owner = null;
            return;
        }
        ci.cancel();
        int sizeX = this.sectionGridSizeX;
        int sizeY = this.sectionGridSizeY;
        int sizeZ = this.sectionGridSizeZ;
        SectionRenderDispatcher.RenderSection[] grid = this.sections;
        if (this.nimbus$owner != grid || this.nimbus$columnX.length != sizeX || this.nimbus$columnZ.length != sizeZ) {
            this.nimbus$owner = grid;
            this.nimbus$columnX = new int[sizeX];
            this.nimbus$columnZ = new int[sizeZ];
            Arrays.fill(this.nimbus$columnX, Integer.MIN_VALUE);
            Arrays.fill(this.nimbus$columnZ, Integer.MIN_VALUE);
        }
        int[] columnX = this.nimbus$columnX;
        int[] columnZ = this.nimbus$columnZ;
        int spanX = sizeX * 16;
        int baseX = Mth.ceil(cameraX) - 8 - spanX / 2;
        int spanZ = sizeZ * 16;
        int baseZ = Mth.ceil(cameraZ) - 8 - spanZ / 2;
        int minY = this.level.getMinBuildHeight();
        for (int x = 0; x < sizeX; x++) {
            int originX = baseX + Math.floorMod(x * 16 - baseX, spanX);
            boolean movedX = originX != columnX[x];
            columnX[x] = originX;
            for (int z = 0; z < sizeZ; z++) {
                int originZ = baseZ + Math.floorMod(z * 16 - baseZ, spanZ);
                if (!movedX && originZ == columnZ[z]) {
                    continue;
                }
                for (int y = 0; y < sizeY; y++) {
                    SectionRenderDispatcher.RenderSection section = grid[(z * sizeY + y) * sizeX + x];
                    int originY = minY + y * 16;
                    BlockPos origin = section.getOrigin();
                    if (originX != origin.getX() || originY != origin.getY() || originZ != origin.getZ()) {
                        section.setOrigin(originX, originY, originZ);
                    }
                }
            }
        }
        for (int z = 0; z < sizeZ; z++) {
            columnZ[z] = baseZ + Math.floorMod(z * 16 - baseZ, spanZ);
        }
    }
}
