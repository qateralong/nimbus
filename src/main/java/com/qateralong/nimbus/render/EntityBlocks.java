package com.qateralong.nimbus.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qateralong.nimbus.ChestModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.model.ShulkerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BedRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

public final class EntityBlocks {
    private static final int LIGHT = 15728880;
    public static final java.util.Set<net.minecraft.world.level.block.Block> SIGNS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final Map<net.minecraft.world.level.block.Block, Map<ModelResourceLocation, BlockState>> STATES = new java.util.concurrent.ConcurrentHashMap<>();

    private EntityBlocks() {
    }

    private static Map<ModelResourceLocation, BlockState> states(net.minecraft.world.level.block.Block block) {
        Map<ModelResourceLocation, BlockState> states = new HashMap<>();
        for (BlockState candidate : block.getStateDefinition().getPossibleStates()) {
            states.put(net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(candidate), candidate);
        }
        return states;
    }

    public static BakedModel bake(ModelResourceLocation id, BakedModel original, Function<Material, TextureAtlasSprite> textures) {
        if (id == null || !id.id().getNamespace().equals("minecraft")) {
            return null;
        }
        String path = id.id().getPath();
        if (path.equals("decorated_pot")) {
            return new PotModel(original);
        }
        boolean bed = path.endsWith("_bed");
        boolean shulker = path.equals("shulker_box") || path.endsWith("_shulker_box");
        Map<String, String> variant = bed || shulker ? parse(id.variant()) : Map.of();
        if (bed && variant.containsKey("facing") && variant.containsKey("part")) {
            DyeColor color = DyeColor.byName(path.substring(0, path.length() - 4), null);
            Direction facing = Direction.byName(variant.get("facing"));
            if (color == null || facing == null) {
                return null;
            }
            TextureAtlasSprite sprite = textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("entity/bed/" + color.getName())));
            ModelPart part = ("head".equals(variant.get("part")) ? BedRenderer.createHeadLayer() : BedRenderer.createFootLayer()).bakeRoot();
            PoseStack pose = new PoseStack();
            pose.translate(0.0F, 0.5625F, 0.0F);
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            pose.translate(0.5F, 0.5F, 0.5F);
            pose.mulPose(Axis.ZP.rotationDegrees(180.0F + facing.toYRot()));
            pose.translate(-0.5F, -0.5F, -0.5F);
            ModelCapture capture = new ModelCapture(sprite);
            part.render(pose, capture, LIGHT, OverlayTexture.NO_OVERLAY);
            return new Static(capture.quads(), original.getParticleIcon());
        }
        net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id.id());
        if (block instanceof net.minecraft.world.level.block.BellBlock && id.id().getNamespace().equals("minecraft")) {
            TextureAtlasSprite sprite = textures.apply(net.minecraft.client.renderer.blockentity.BellRenderer.BELL_RESOURCE_LOCATION);
            ModelCapture capture = new ModelCapture(sprite);
            net.minecraft.client.renderer.blockentity.BellRenderer.createBodyLayer().bakeRoot().getChild("bell_body")
                    .render(new PoseStack(), capture, LIGHT, OverlayTexture.NO_OVERLAY);
            return new BellModel(original, new Static(capture.quads(), original.getParticleIcon()));
        }
        if (block instanceof net.minecraft.world.level.block.SignBlock sign) {
            BlockState state = STATES.computeIfAbsent(block, EntityBlocks::states).get(id);
            if (state == null) {
                return null;
            }
            boolean hanging = block instanceof net.minecraft.world.level.block.CeilingHangingSignBlock || block instanceof net.minecraft.world.level.block.WallHangingSignBlock;
            boolean standing = block instanceof net.minecraft.world.level.block.StandingSignBlock;
            if (!hanging && !standing && !(block instanceof net.minecraft.world.level.block.WallSignBlock)) {
                return null;
            }
            net.minecraft.world.level.block.state.properties.WoodType wood = net.minecraft.world.level.block.SignBlock.getWoodType(block);
            Material material = hanging ? net.minecraft.client.renderer.Sheets.getHangingSignMaterial(wood) : net.minecraft.client.renderer.Sheets.getSignMaterial(wood);
            TextureAtlasSprite sprite = textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS, material.texture()));
            if (sprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
                return null;
            }
            float degrees = sign.getYRotationDegrees(state);
            PoseStack pose = new PoseStack();
            ModelCapture capture = new ModelCapture(sprite);
            if (hanging) {
                pose.translate(0.5F, 0.9375F, 0.5F);
                pose.mulPose(Axis.YP.rotationDegrees(-degrees));
                pose.translate(0.0F, -0.3125F, 0.0F);
                pose.scale(1.0F, -1.0F, -1.0F);
                var model = new net.minecraft.client.renderer.blockentity.HangingSignRenderer.HangingSignModel(
                        net.minecraft.client.renderer.blockentity.HangingSignRenderer.createHangingSignLayer().bakeRoot());
                model.evaluateVisibleParts(state);
                model.root.render(pose, capture, LIGHT, OverlayTexture.NO_OVERLAY);
            } else {
                ModelPart root = net.minecraft.client.renderer.blockentity.SignRenderer.createSignLayer().bakeRoot();
                root.getChild("stick").visible = standing;
                pose.translate(0.5F, 0.5F, 0.5F);
                pose.mulPose(Axis.YP.rotationDegrees(-degrees));
                if (!standing) {
                    pose.translate(0.0F, -0.3125F, -0.4375F);
                }
                float scale = 0.6666667F;
                pose.scale(scale, -scale, -scale);
                root.render(pose, capture, LIGHT, OverlayTexture.NO_OVERLAY);
            }
            SIGNS.add(block);
            return new Static(capture.quads(), original.getParticleIcon());
        }
        if (shulker && variant.containsKey("facing")) {
            Direction facing = Direction.byName(variant.get("facing"));
            DyeColor color = path.equals("shulker_box") ? null : DyeColor.byName(path.substring(0, path.length() - 12), null);
            if (facing == null || !path.equals("shulker_box") && color == null) {
                return null;
            }
            String texture = color == null ? "entity/shulker/shulker" : "entity/shulker/shulker_" + color.getName();
            TextureAtlasSprite sprite = textures.apply(new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace(texture)));
            ShulkerModel<?> model = new ShulkerModel<>(ShulkerModel.createBodyLayer().bakeRoot());
            model.getLid().setPos(0.0F, 24.0F, 0.0F);
            model.getLid().yRot = 0.0F;
            PoseStack pose = new PoseStack();
            pose.translate(0.5F, 0.5F, 0.5F);
            pose.scale(0.9995F, 0.9995F, 0.9995F);
            pose.mulPose(facing.getRotation());
            pose.scale(1.0F, -1.0F, -1.0F);
            pose.translate(0.0F, -1.0F, 0.0F);
            ModelCapture capture = new ModelCapture(sprite);
            for (ModelPart part : model.parts()) {
                part.render(pose, capture, LIGHT, OverlayTexture.NO_OVERLAY);
            }
            return new ChestModel(new Static(capture.quads(), original.getParticleIcon()));
        }
        return null;
    }

    private static Map<String, String> parse(String variant) {
        Map<String, String> values = new HashMap<>();
        for (String pair : variant.split(",")) {
            int split = pair.indexOf('=');
            if (split > 0) {
                values.put(pair.substring(0, split), pair.substring(split + 1));
            }
        }
        return values;
    }

    private record Static(List<BakedQuad> quads, TextureAtlasSprite particle) implements BakedModel {
        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return side == null ? this.quads : List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return true;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return this.particle;
        }

        @Override
        public ItemTransforms getTransforms() {
            return ItemTransforms.NO_TRANSFORMS;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
