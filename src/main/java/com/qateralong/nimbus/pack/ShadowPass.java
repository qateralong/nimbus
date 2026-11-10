package com.qateralong.nimbus.pack;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.qateralong.nimbus.mixin.LevelRendererAccessor;
import com.qateralong.nimbus.mixin.ShaderInstanceAccessor;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

// The shadow map: compiled terrain sections inside the light's box drawn with the pack's shadow program into
// shadowtex0 / shadowcolor0-1; shadowtex1 keeps the depth before translucent layers.
public final class ShadowPass {
    private static final RenderType[] OPAQUE = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout()};
    public int resolution = 2048;
    public int depth0;
    public int depth1;
    public int color0;
    public int color1;
    private int framebuffer;
    private PackProgram program;
    private boolean missing;
    public int drawn;
    public boolean entities = true;
    public boolean player;
    // shadow.culling: 0 = cull to what the camera can see, voxelDistance when reversed, negative = off
    public float cullKeep;
    public boolean enabled = true;
    private int nonEmpty;
    private int culled;
    private long lastLog;
    // sections with geometry near the camera, refreshed every few frames instead of walking the whole view area each frame
    private final java.util.ArrayList<SectionRenderDispatcher.RenderSection> candidates = new java.util.ArrayList<>();
    private int refresh;

    public void free() {
        for (int id : new int[] {this.depth0, this.depth1, this.color0, this.color1}) {
            if (id != 0) {
                GlStateManager._deleteTexture(id);
            }
        }
        if (this.framebuffer != 0) {
            GlStateManager._glDeleteFramebuffers(this.framebuffer);
        }
        if (this.program != null) {
            this.program.delete();
        }
        this.depth0 = this.depth1 = this.color0 = this.color1 = this.framebuffer = 0;
        this.program = null;
        this.missing = false;
        this.candidates.clear();
    }

    void bindTarget() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        GlStateManager._viewport(0, 0, this.resolution, this.resolution);
    }

    private void entities(Pipeline pipeline, Minecraft mc, Uniforms u, Matrix4f light) {
        Vector4f v = new Vector4f();
        var level = mc.level;
        var camera = mc.gameRenderer.getMainCamera();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        var dispatcher = mc.getEntityRenderDispatcher();
        var buffers = mc.renderBuffers().bufferSource();
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        dispatcher.prepare(level, camera, mc.crosshairPickEntity);
        double reach = u.shadowDistance;
        pipeline.phase = Pipeline.Phase.SHADOW;
        for (net.minecraft.world.entity.Entity e : level.entitiesForRendering()) {
            if (e == camera.getEntity() && !this.player && !camera.isDetached()) {
                continue;
            }
            double x = net.minecraft.util.Mth.lerp(partial, e.xOld, e.getX()) - u.cameraX();
            double y = net.minecraft.util.Mth.lerp(partial, e.yOld, e.getY()) - u.cameraY();
            double z = net.minecraft.util.Mth.lerp(partial, e.zOld, e.getZ()) - u.cameraZ();
            if (Math.abs(x) > reach || Math.abs(z) > reach || e.isInvisible()) {
                continue;
            }
            float r = (float) e.getBoundingBox().getSize() + 2.0F;
            light.transform(v.set((float) x, (float) y + e.getBbHeight() * 0.5F, (float) z, 1.0F));
            if (Math.abs(v.x) > 1.0F + r / u.shadowDistance || Math.abs(v.y) > 1.0F + r / u.shadowDistance) {
                continue;
            }
            float yaw = net.minecraft.util.Mth.lerp(partial, e.yRotO, e.getYRot());
            dispatcher.render(e, x, y, z, yaw, partial, pose, buffers, dispatcher.getPackedLightCoords(e, partial));
        }
        buffers.endBatch();
        pipeline.phase = Pipeline.Phase.NONE;
    }

    private void log(Uniforms u, int candidates) {
        long now = System.currentTimeMillis();
        if (now - this.lastLog > 5000) {
            this.lastLog = now;
            com.qateralong.nimbus.Nimbus.LOGGER.info("Nimbus shadow map: {} draws of {} sections ({} with geometry, {} culled), {}px, distance {}, engine {}", this.drawn,
                    candidates, this.nonEmpty, this.culled, this.resolution, u.shadowDistance, com.qateralong.nimbus.render.Terrain.active());
        }
    }

    public boolean ready() {
        return this.depth0 != 0;
    }

    private void create() {
        this.depth0 = depth(this.resolution);
        this.depth1 = depth(this.resolution);
        this.color0 = color(this.resolution);
        this.color1 = color(this.resolution);
        this.framebuffer = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, this.depth0, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.color0, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, this.color1, 0);
        GL20.glDrawBuffers(new int[] {GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1});
    }

    private static int depth(int size) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, size, size, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return id;
    }

    private static int color(int size) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return id;
    }

    public void render(Pipeline pipeline, ShaderPack pack, PackConfig config, String dir, String defines) {
        if (this.missing || !this.enabled || Boolean.getBoolean("nimbus.noShadow")) {
            return;
        }
        if (this.program == null) {
            try {
                if (config.programEnabled(dir, "shadow")) {
                    this.program = PackProgram.build(pack, config, dir, "shadow", defines, DefaultVertexFormat.BLOCK.getElementAttributeNames(), true);
                }
            } catch (Exception e) {
                PackProgram.warn("shadow", e);
            }
            if (this.program == null) {
                this.missing = true;
                return;
            }
        }
        Minecraft mc = Minecraft.getInstance();
        ViewArea area = ((LevelRendererAccessor) mc.levelRenderer).nimbus$viewArea();
        if (area == null) {
            return;
        }
        if (this.depth0 == 0) {
            this.create();
        }
        Uniforms u = pipeline.uniforms;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        GlStateManager._viewport(0, 0, this.resolution, this.resolution);
        RenderSystem.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.clearDepth(1.0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        PackProgram p = this.program;
        GlStateManager._glUseProgram(p.id);
        pipeline.images.bindBuffers();
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
        u.renderStage = 0;
        u.upload(p);
        Uniforms.mat(p, "nimbus_ModelViewMatrix", u.shadowModelView);
        Uniforms.mat(p, "nimbus_ProjectionMatrix", u.shadowProjection);
        Uniforms.mat(p, "nimbus_ModelViewMatrixInverse", u.shadowModelViewInverse);
        Uniforms.mat(p, "nimbus_ProjectionMatrixInverse", u.shadowProjectionInverse);
        Uniforms.mat(p, "nimbus_TextureMatrix0", new Matrix4f());
        Uniforms.mat(p, "nimbus_TextureMatrix1", new Matrix4f().scale(1.0F / 256.0F).translate(8.0F, 8.0F, 8.0F));
        int atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getId();
        pipeline.bindForShadow(atlas, RenderSystem.getShaderTexture(2), p);
        Matrix4f light = new Matrix4f(u.shadowProjection).mul(u.shadowModelView);
        if (com.qateralong.nimbus.render.Terrain.active()) {
            // our engine holds the terrain: draw its geometry inside the light box with the engine variant of the shadow program
            this.drawn = 0;
            if (!Boolean.getBoolean("nimbus.shadowNoCull") && this.cullKeep >= 0.0F) {
                org.joml.Vector3f ray = u.shadowModelViewInverse.transformDirection(new org.joml.Vector3f(0.0F, 0.0F, -1.0F)).normalize();
                com.qateralong.nimbus.render.Terrain.prepareShadow(light, new Matrix4f(u.projection).mul(u.modelView), ray.x, ray.y, ray.z, u.cameraX(),
                        u.cameraY(), u.cameraZ(), u.shadowDistance, this.cullKeep);
            }
            pipeline.phase = Pipeline.Phase.SHADOW;
            for (RenderType layer : OPAQUE) {
                if (pipeline.engineProgram(layer, u.shadowModelView, u.shadowProjection, u.cameraX(), u.cameraY(), u.cameraZ()) != null) {
                    this.drawn += com.qateralong.nimbus.render.Terrain.drawShadow(layer, light, u.cameraX(), u.cameraY(), u.cameraZ(), u.shadowDistance);
                }
            }
            pipeline.phase = Pipeline.Phase.NONE;
            GpuTimes.mark("shadow terrain");
            if (this.entities) {
                this.entities(pipeline, mc, u, light);
                this.bindTarget();
            }
            GpuTimes.mark("shadow entities");
            GL43.glCopyImageSubData(this.depth0, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, this.depth1, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, this.resolution, this.resolution, 1);
            pipeline.phase = Pipeline.Phase.SHADOW;
            if (pipeline.engineProgram(RenderType.translucent(), u.shadowModelView, u.shadowProjection, u.cameraX(), u.cameraY(), u.cameraZ()) != null) {
                this.drawn += com.qateralong.nimbus.render.Terrain.drawShadow(RenderType.translucent(), light, u.cameraX(), u.cameraY(), u.cameraZ(), u.shadowDistance);
            }
            pipeline.phase = Pipeline.Phase.NONE;
            com.qateralong.nimbus.render.Terrain.shadowDone();
            GlStateManager._glUseProgram(0);
            RenderSystem.enableCull();
            this.log(u, 0);
            return;
        }
        if (this.refresh-- <= 0) {
            this.refresh = 20;
            this.candidates.clear();
            float reach = u.shadowDistance + 16.0F;
            for (SectionRenderDispatcher.RenderSection section : area.sections) {
                if (section.getCompiled().hasNoRenderableLayers()) {
                    continue;
                }
                BlockPos o = section.getOrigin();
                if (Math.abs(o.getX() + 8 - u.cameraX()) <= reach && Math.abs(o.getZ() + 8 - u.cameraZ()) <= reach) {
                    this.candidates.add(section);
                }
            }
        }
        this.drawn = 0;
        this.nonEmpty = 0;
        this.culled = 0;
        for (RenderType layer : OPAQUE) {
            this.draw(area, layer, light, u);
        }
        VertexBuffer.unbind();
        if (this.entities) {
            this.entities(pipeline, mc, u, light);
            this.bindTarget();
            GlStateManager._glUseProgram(p.id);
            pipeline.images.bindBuffers();
        pipeline.images.bindBuffers();
            ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
            ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
            pipeline.bindForShadow(atlas, RenderSystem.getShaderTexture(2), p);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
        }
        GL43.glCopyImageSubData(this.depth0, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, this.depth1, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, this.resolution, this.resolution, 1);
        this.draw(area, RenderType.translucent(), light, u);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        this.log(u, this.candidates.size());
    }

    private void draw(ViewArea area, RenderType layer, Matrix4f light, Uniforms u) {
        double cx = u.cameraX();
        double cy = u.cameraY();
        double cz = u.cameraZ();
        float limit = u.shadowDistance + 16.0F;
        Vector4f v = new Vector4f();
        int offset = this.program.location("nimbus_ChunkOffset");
        for (SectionRenderDispatcher.RenderSection section : this.candidates) {
            if (section.getCompiled().isEmpty(layer)) {
                continue;
            }
            this.nonEmpty++;
            BlockPos origin = section.getOrigin();
            float x = (float) (origin.getX() - cx);
            float y = (float) (origin.getY() - cy);
            float z = (float) (origin.getZ() - cz);
            if (Math.abs(x + 8) > limit || Math.abs(z + 8) > limit) {
                continue;
            }
            light.transform(v.set(x + 8, y + 8, z + 8, 1.0F));
            // 8√3 blocks of section radius in light clip space
            float pad = 14.0F / u.shadowDistance;
            if (Math.abs(v.x) > 1.0F + pad || Math.abs(v.y) > 1.0F + pad) {
                this.culled++;
                continue;
            }
            VertexBuffer buffer = section.getBuffer(layer);
            if (offset >= 0) {
                GL20.glUniform3f(offset, x, y, z);
            }
            buffer.bind();
            buffer.draw();
            this.drawn++;
        }
    }
}
