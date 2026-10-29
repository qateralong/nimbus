package com.qateralong.nimbus.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qateralong.nimbus.Chests;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.DecoratedPotRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;

public final class PotModel extends ForwardingBakedModel {
    private static final int LIGHT = 15728880;
    private static final String[] SIDES = {"front", "back", "left", "right"};
    private static volatile net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial material;
    private final Map<Direction, List<BakedQuad>> bases = new ConcurrentHashMap<>();
    private final Map<String, List<BakedQuad>> sides = new ConcurrentHashMap<>();
    private final ModelPart base = DecoratedPotRenderer.createBaseLayer().bakeRoot();
    private final ModelPart sideParts = DecoratedPotRenderer.createSidesLayer().bakeRoot();

    public PotModel(BakedModel original) {
        this.wrapped = original;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    private static TextureAtlasSprite sprite(Material material) {
        return Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(material.texture());
    }

    private static PoseStack pose(Direction facing) {
        PoseStack pose = new PoseStack();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        pose.translate(-0.5F, 0.0F, -0.5F);
        return pose;
    }

    private List<BakedQuad> base(Direction facing) {
        return this.bases.computeIfAbsent(facing, key -> {
            ModelCapture capture = new ModelCapture(sprite(Sheets.DECORATED_POT_BASE));
            PoseStack pose = pose(key);
            synchronized (this.base) {
                this.base.getChild("neck").render(pose, capture, LIGHT, OverlayTexture.NO_OVERLAY);
                this.base.getChild("top").render(pose, capture, LIGHT, OverlayTexture.NO_OVERLAY);
                this.base.getChild("bottom").render(pose, capture, LIGHT, OverlayTexture.NO_OVERLAY);
            }
            return capture.quads();
        });
    }

    private static Material material(Optional<Item> item) {
        if (item.isPresent()) {
            ResourceKey<net.minecraft.world.level.block.entity.DecoratedPotPattern> pattern = DecoratedPotPatterns.getPatternFromItem(item.get());
            Material material = pattern == null ? null : Sheets.getDecoratedPotMaterial(pattern);
            if (material != null) {
                return material;
            }
        }
        return Sheets.getDecoratedPotMaterial(DecoratedPotPatterns.getPatternFromItem(Items.BRICK));
    }

    private List<BakedQuad> side(Direction facing, int index, Optional<Item> item) {
        Material material = material(item);
        String key = facing.ordinal() + ":" + index + ":" + material.texture();
        return this.sides.computeIfAbsent(key, ignored -> {
            ModelCapture capture = new ModelCapture(sprite(material));
            synchronized (this.sideParts) {
                this.sideParts.getChild(SIDES[index]).render(pose(facing), capture, LIGHT, OverlayTexture.NO_OVERLAY);
            }
            return capture.quads();
        });
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
        if (!Chests.potsEnabled() || Chests.hiddenInMesh(pos) || !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        PotDecorations decorations = level.getBlockEntityRenderData(pos) instanceof PotDecorations data ? data : PotDecorations.EMPTY;
        var emitter = context.getEmitter();
        if (material == null) {
            material = net.fabricmc.fabric.api.renderer.v1.RendererAccess.INSTANCE.getRenderer().materialFinder().find();
        }
        emit(emitter, base(facing));
        emit(emitter, side(facing, 0, decorations.front()));
        emit(emitter, side(facing, 1, decorations.back()));
        emit(emitter, side(facing, 2, decorations.left()));
        emit(emitter, side(facing, 3, decorations.right()));
    }

    private static void emit(net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter emitter, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            emitter.fromVanilla(quad, material, null);
            emitter.emit();
        }
    }
}
