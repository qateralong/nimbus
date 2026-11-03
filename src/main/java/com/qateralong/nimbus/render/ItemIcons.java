package com.qateralong.nimbus.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.mixin.ItemRendererAccessor;
import java.util.Arrays;
import java.util.IdentityHashMap;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

// GUI item icons are rendered once into an atlas, keyed by model, item and tint colours, and then drawn as one quad
// each. Glinting, custom-rendered, FRAPI-emitting and animated-texture items keep the normal path. Queued icons are
// drawn before anything that comes after them, the way vanilla draws an item right away.
public final class ItemIcons {
    private static final int COLUMNS = 32;
    private static final int RESET_FRAMES = 600;
    private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
    private static final ByteBufferBuilder MEMORY = new ByteBufferBuilder(1 << 14);
    private static final IdentityHashMap<BakedModel, Info> INFOS = new IdentityHashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int[] VIEWPORT = new int[4];
    private static final int[] SCISSOR = new int[4];
    private static final float[] COLOR = new float[4];
    private static final float[] SAVED_COLOR = new float[4];
    private static MultiBufferSource.BufferSource cellBuffers;
    private static TextureTarget atlas;
    private static Object models;
    private static int scale;
    private static int cellPx;
    private static int next;
    private static boolean full;
    private static int fullFrames;
    private static BufferBuilder builder;
    private static int queued;
    private static boolean busy;
    private static int[] colors = new int[4];
    public static int cells;

    private ItemIcons() {
    }

    private static final class Info {
        final boolean cacheable;
        final int[] tints;
        Entry entries;

        Info(boolean cacheable, int[] tints) {
            this.cacheable = cacheable;
            this.tints = tints;
        }
    }

    private record Entry(Item item, int[] colors, int cell, Entry next) {
    }

    public static boolean pending() {
        return queued > 0;
    }

    // Called with the pose renderItem set up (translate to the slot centre, scale 16, -16, 16).
    public static boolean draw(ItemStack stack, BakedModel model, PoseStack pose) {
        if (busy) {
            return false;
        }
        NimbusConfig config = NimbusConfig.get();
        if (!config.enabled || !config.itemIconCache) {
            return false;
        }
        Matrix4f m = pose.last().pose();
        if (m.m00() != 16.0F || m.m11() != -16.0F || m.m01() != 0.0F || m.m10() != 0.0F || m.m02() != 0.0F || m.m12() != 0.0F
                || m.m20() != 0.0F || m.m21() != 0.0F || m.m03() != 0.0F || m.m13() != 0.0F) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = minecraft.getWindow().getGuiScale();
        int wanted = (int) guiScale;
        if (wanted != guiScale || wanted < 1 || stack.hasFoil()) {
            return false;
        }
        if (models != minecraft.getModelManager().getMissingModel() || scale != wanted) {
            reset(minecraft.getModelManager().getMissingModel(), wanted);
        }
        Info info = INFOS.get(model);
        if (info == null) {
            info = inspect(model);
            INFOS.put(model, info);
        }
        if (!info.cacheable) {
            return false;
        }
        int count = info.tints.length;
        if (colors.length < count) {
            colors = new int[count];
        }
        if (count > 0) {
            ItemColors itemColors = ((ItemRendererAccessor) minecraft.getItemRenderer()).nimbus$itemColors();
            for (int i = 0; i < count; i++) {
                colors[i] = itemColors.getColor(stack, info.tints[i]);
            }
        }
        Item item = stack.getItem();
        Entry entry = info.entries;
        while (entry != null && (entry.item != item || !Arrays.equals(entry.colors, 0, count, colors, 0, count))) {
            entry = entry.next;
        }
        int cell;
        if (entry != null) {
            cell = entry.cell;
        } else {
            if (full || atlas == null && !create()) {
                return false;
            }
            if (next >= COLUMNS * COLUMNS) {
                full = true;
                return false;
            }
            cell = next++;
            if (!render(minecraft, stack, model, cell)) {
                return false;
            }
            info.entries = new Entry(item, Arrays.copyOf(colors, count), cell, info.entries);
            cells = next;
        }
        queue(cell, m);
        return true;
    }

    private static Info inspect(BakedModel model) {
        boolean cacheable = !model.isCustomRenderer() && ((FabricBakedModel) model).isVanillaAdapter();
        int[] tints = new int[0];
        if (cacheable) {
            for (Direction side : SIDES) {
                RANDOM.setSeed(42L);
                for (BakedQuad quad : model.getQuads(null, side, RANDOM)) {
                    if (quad.getSprite() == null || ((AnimatedIndex) quad.getSprite()).nimbus$animIndex() >= 0) {
                        cacheable = false;
                    }
                    if (quad.isTinted() && Arrays.stream(tints).noneMatch(t -> t == quad.getTintIndex())) {
                        tints = Arrays.copyOf(tints, tints.length + 1);
                        tints[tints.length - 1] = quad.getTintIndex();
                    }
                }
            }
        }
        return new Info(cacheable, tints);
    }

    private static void reset(Object owner, int guiScale) {
        drawPending();
        models = owner;
        scale = guiScale;
        INFOS.clear();
        next = 0;
        cells = 0;
        full = false;
        fullFrames = 0;
        if (atlas != null && cellPx != 16 * guiScale) {
            atlas.destroyBuffers();
            atlas = null;
        }
        cellPx = 16 * guiScale;
    }

    private static boolean create() {
        int size = cellPx * COLUMNS;
        int max = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (size > max) {
            return false;
        }
        atlas = new TextureTarget(size, size, true, Minecraft.ON_OSX);
        atlas.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        atlas.clear(Minecraft.ON_OSX);
        if (cellBuffers == null) {
            cellBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
        }
        return true;
    }

    // Once a frame: a full atlas is emptied now and then so new items get cells again.
    public static void endFrame() {
        if (queued > 0) {
            MeshData mesh = builder.build();
            if (mesh != null) {
                mesh.close();
            }
            builder = null;
            queued = 0;
        }
        if (full && ++fullFrames >= RESET_FRAMES) {
            Object owner = models;
            reset(owner, scale);
        }
    }

    private static boolean render(Minecraft minecraft, ItemStack stack, BakedModel model, int cell) {
        int x = cell % COLUMNS * cellPx;
        int y = cell / COLUMNS * cellPx;
        boolean quiet = GuiBatch.suspend();
        busy = true;
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, SCISSOR);
        boolean depth = GlMirror.depth;
        int depthFunc = GlMirror.depthFunc;
        boolean depthMask = GlMirror.depthMask;
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        System.arraycopy(RenderSystem.getShaderColor(), 0, SAVED_COLOR, 0, 4);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        boolean ok = true;
        boolean over = GlMirror.overAlpha;
        GlMirror.overAlpha = true;
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, atlas.frameBufferId);
            RenderSystem.viewport(x, y, cellPx, cellPx);
            RenderSystem.enableScissor(x, y, cellPx, cellPx);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            RenderSystem.clearDepth(1.0);
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, 16.0F, 16.0F, 0.0F, 1000.0F, 21000.0F), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.translation(0.0F, 0.0F, -11000.0F);
            RenderSystem.applyModelViewMatrix();
            PoseStack cellPose = new PoseStack();
            cellPose.translate(8.0F, 8.0F, 150.0F);
            cellPose.scale(16.0F, -16.0F, 16.0F);
            if (!model.usesBlockLight()) {
                Lighting.setupForFlatItems();
            } else {
                Lighting.setupFor3DItems();
            }
            minecraft.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, cellPose, cellBuffers, 15728880, OverlayTexture.NO_OVERLAY, model);
            cellBuffers.endBatch();
        } catch (RuntimeException e) {
            com.qateralong.nimbus.Nimbus.LOGGER.warn("Nimbus: item icon for {} failed", stack, e);
            ok = false;
        } finally {
            GlMirror.overAlpha = over;
            Lighting.setupFor3DItems();
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.setShaderColor(SAVED_COLOR[0], SAVED_COLOR[1], SAVED_COLOR[2], SAVED_COLOR[3]);
            if (scissor) {
                RenderSystem.enableScissor(SCISSOR[0], SCISSOR[1], SCISSOR[2], SCISSOR[3]);
            } else {
                RenderSystem.disableScissor();
            }
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunc);
            if (depth) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            RenderSystem.viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
            busy = false;
            GuiBatch.resume(quiet);
        }
        return ok;
    }

    private static void queue(int cell, Matrix4f m) {
        float[] shader = RenderSystem.getShaderColor();
        if (queued > 0 && (shader[0] != COLOR[0] || shader[1] != COLOR[1] || shader[2] != COLOR[2] || shader[3] != COLOR[3])) {
            drawPending();
        }
        if (queued == 0) {
            // blits queued before this icon go first
            GuiBatch.flush();
            System.arraycopy(shader, 0, COLOR, 0, 4);
            builder = new BufferBuilder(MEMORY, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        }
        float size = cellPx * COLUMNS;
        float u0 = (cell % COLUMNS) * cellPx / size;
        float u1 = u0 + cellPx / size;
        float v0 = (cell / COLUMNS) * cellPx / size;
        float v1 = v0 + cellPx / size;
        float x0 = m.m30() - 8.0F;
        float x1 = m.m30() + 8.0F;
        float y0 = m.m31() - 8.0F;
        float y1 = m.m31() + 8.0F;
        float z = m.m32();
        builder.addVertex(x0, y0, z).setUv(u0, v1);
        builder.addVertex(x0, y1, z).setUv(u0, v0);
        builder.addVertex(x1, y1, z).setUv(u1, v0);
        builder.addVertex(x1, y0, z).setUv(u1, v1);
        queued++;
    }

    // Draws queued icons now; leaves every piece of GL state it touches as it found it.
    public static void drawPending() {
        if (queued == 0 || busy) {
            return;
        }
        busy = true;
        BufferBuilder pendingBuilder = builder;
        builder = null;
        queued = 0;
        boolean quiet = GuiBatch.suspend();
        try {
            MeshData mesh = pendingBuilder.build();
            if (mesh == null || atlas == null) {
                if (mesh != null) {
                    mesh.close();
                }
                return;
            }
            ShaderInstance shader = RenderSystem.getShader();
            int texture = RenderSystem.getShaderTexture(0);
            System.arraycopy(RenderSystem.getShaderColor(), 0, SAVED_COLOR, 0, 4);
            boolean depth = GlMirror.depth;
            int depthFunc = GlMirror.depthFunc;
            boolean depthMask = GlMirror.depthMask;
            boolean blend = GlMirror.blend;
            int srcRgb = GlMirror.srcRgb;
            int dstRgb = GlMirror.dstRgb;
            int srcAlpha = GlMirror.srcAlpha;
            int dstAlpha = GlMirror.dstAlpha;
            RenderSystem.setShaderColor(COLOR[0], COLOR[1], COLOR[2], COLOR[3]);
            // items are depth tested and write depth, like the render types they replace
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            // the atlas holds premultiplied colour
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderTexture(0, atlas.getColorTextureId());
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            BufferUploader.drawWithShader(mesh);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (!blend) {
                RenderSystem.disableBlend();
            }
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunc);
            if (!depth) {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.setShaderColor(SAVED_COLOR[0], SAVED_COLOR[1], SAVED_COLOR[2], SAVED_COLOR[3]);
            RenderSystem.setShaderTexture(0, texture);
            if (shader != null) {
                RenderSystem.setShader(() -> shader);
            }
        } finally {
            busy = false;
            GuiBatch.resume(quiet);
        }
    }
}
