package com.qateralong.nimbus.render;

import com.qateralong.nimbus.Chests;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class BellModel extends ForwardingBakedModel {
    private final BakedModel body;

    public BellModel(BakedModel frame, BakedModel body) {
        this.wrapped = frame;
        this.body = body;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
        super.emitBlockQuads(level, state, pos, random, context);
        if (Chests.bellsEnabled() && !Chests.hiddenInMesh(pos)) {
            this.body.emitBlockQuads(level, state, pos, random, context);
        }
    }
}
