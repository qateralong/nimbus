package com.qateralong.nimbus.pack;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

// Per-frame values of the OptiFine uniform set, uploaded once per frame to each program that asks for them.
public final class Uniforms {
    public final Matrix4f modelView = new Matrix4f();
    public final Matrix4f modelViewInverse = new Matrix4f();
    public final Matrix4f projection = new Matrix4f();
    public final Matrix4f projectionInverse = new Matrix4f();
    public final Matrix4f previousModelView = new Matrix4f();
    public final Matrix4f previousProjection = new Matrix4f();
    public final Matrix4f shadowModelView = new Matrix4f();
    public final Matrix4f shadowModelViewInverse = new Matrix4f();
    public final Matrix4f shadowProjection = new Matrix4f();
    public final Matrix4f shadowProjectionInverse = new Matrix4f();
    public final Vector3f camera = new Vector3f();
    public final Vector3f previousCamera = new Vector3f();
    private Vec3 cameraExact = Vec3.ZERO;
    private Vec3 previousExact = Vec3.ZERO;
    public final Vector3f sun = new Vector3f();
    public final Vector3f moon = new Vector3f();
    public final Vector3f up = new Vector3f();
    public final Vector3f shadowLight = new Vector3f();
    public final Vector3f fogColor = new Vector3f();
    public final Vector3f skyColor = new Vector3f();
    public int frame;
    public float frameTime;
    public float frameTimeCounter;
    private long lastNanos;
    public int worldTime;
    public int worldDay;
    public int moonPhase;
    public float sunAngle;
    public float shadowAngle;
    public float rain;
    public float wetness;
    public float thunder;
    public int eyeInWater;
    public float eyeSkyX;
    public float eyeBlockX;
    public float smoothSky;
    public float smoothBlock;
    public float nightVision;
    public float blindness;
    public float darkness;
    public float far;
    public float width;
    public float height;
    public float fogStart;
    public float fogEnd;
    public float shadowDistance = 160.0F;
    public float sunPathRotation;
    public int renderStage;
    public CustomUniforms custom;
    public final java.util.Map<String, double[]> builtins = new java.util.HashMap<>();
    private net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomes;

    private final int[] held = {-1, -1, 0, 0};

    public void begin(Minecraft mc, Camera cam, Matrix4f view, Matrix4f proj, float partial, int width, int height) {
        ClientLevel level = mc.level;
        this.previousModelView.set(this.modelView);
        this.previousProjection.set(this.projection);
        this.previousCamera.set(this.camera);
        this.previousExact = this.cameraExact;
        this.modelView.set(view);
        this.modelView.invert(this.modelViewInverse);
        this.projection.set(proj);
        this.projection.invert(this.projectionInverse);
        this.cameraExact = cam.getPosition();
        this.camera.set((float) this.cameraExact.x, (float) this.cameraExact.y, (float) this.cameraExact.z);
        this.width = width;
        this.height = height;
        long now = System.nanoTime();
        this.frameTime = this.lastNanos == 0 ? 0.0F : Math.min((now - this.lastNanos) / 1.0E9F, 1.0F);
        this.lastNanos = now;
        this.frameTimeCounter = (this.frameTimeCounter + this.frameTime) % 3600.0F;
        this.frame = (this.frame + 1) % 720720;
        long day = level.getDayTime();
        this.worldTime = (int) (day % 24000L);
        this.worldDay = (int) (day / 24000L);
        this.moonPhase = level.getMoonPhase();
        float skyAngle = level.getTimeOfDay(partial);
        this.skyAngle = skyAngle;
        this.sunAngle = skyAngle < 0.75F ? skyAngle + 0.25F : skyAngle - 0.75F;
        this.shadowAngle = this.sunAngle > 0.5F ? this.sunAngle - 0.5F : this.sunAngle;
        Matrix4f celestial = new Matrix4f(view).rotateY((float) Math.toRadians(-90.0)).rotateZ((float) Math.toRadians(this.sunPathRotation))
                .rotateX(skyAngle * (float) (Math.PI * 2.0));
        Vector4f v = celestial.transform(new Vector4f(0, 100, 0, 0));
        this.sun.set(v.x, v.y, v.z);
        v = celestial.transform(new Vector4f(0, -100, 0, 0));
        this.moon.set(v.x, v.y, v.z);
        v = new Matrix4f(view).transform(new Vector4f(0, 100, 0, 0));
        this.up.set(v.x, v.y, v.z);
        this.shadowLight.set(this.sunAngle <= 0.5F ? this.sun : this.moon);
        float[] fog = RenderSystem.getShaderFogColor();
        this.fogColor.set(fog[0], fog[1], fog[2]);
        Vec3 sky = level.getSkyColor(this.cameraExact, partial);
        this.skyColor.set((float) sky.x, (float) sky.y, (float) sky.z);
        this.rain = level.getRainLevel(partial);
        this.thunder = level.getThunderLevel(partial);
        this.wetness = approach(this.wetness, this.rain, this.frameTime, 30.0F);
        FogType fluid = cam.getFluidInCamera();
        this.eyeInWater = fluid == FogType.WATER ? 1 : fluid == FogType.LAVA ? 2 : fluid == FogType.POWDER_SNOW ? 3 : 0;
        BlockPos eye = BlockPos.containing(this.cameraExact);
        this.eyeBlockX = level.getBrightness(LightLayer.BLOCK, eye) * 16.0F;
        this.eyeSkyX = level.getBrightness(LightLayer.SKY, eye) * 16.0F;
        this.smoothBlock = approach(this.smoothBlock, this.eyeBlockX, this.frameTime, 0.5F);
        this.smoothSky = approach(this.smoothSky, this.eyeSkyX, this.frameTime, 0.5F);
        var player = mc.player;
        if (player != null) {
            this.held[0] = EntityTags.itemId(player.getMainHandItem());
            this.held[1] = EntityTags.itemId(player.getOffhandItem());
            this.held[2] = EntityTags.lightOf(player.getMainHandItem());
            this.held[3] = EntityTags.lightOf(player.getOffhandItem());
        }
        this.nightVision = player != null && player.hasEffect(MobEffects.NIGHT_VISION) ? 1.0F : 0.0F;
        this.blindness = player != null && player.hasEffect(MobEffects.BLINDNESS) ? 1.0F : 0.0F;
        this.darkness = player != null && player.hasEffect(MobEffects.DARKNESS) ? 1.0F : 0.0F;
        this.far = mc.options.getEffectiveRenderDistance() * 16.0F;
        this.fogStart = RenderSystem.getShaderFogStart();
        this.fogEnd = RenderSystem.getShaderFogEnd();
        this.shadowMatrices();
        if (this.custom != null && !this.custom.entries.isEmpty()) {
            this.fillBuiltins(mc, level, eye);
            this.custom.evaluate(this.builtins, this.frameTime);
            String watch = System.getProperty("nimbus.uniformDebug");
            if (watch != null && ++this.debugFrames % 200 == 0) {
                StringBuilder out = new StringBuilder();
                for (String name : watch.split(",")) {
                    out.append(name).append('=').append(java.util.Arrays.toString(this.custom.values.get(name))).append(' ');
                }
                com.qateralong.nimbus.Nimbus.LOGGER.info("Nimbus uniforms: {}", out);
            }
        }
    }

    private int debugFrames;

    private static double[] v(double... values) {
        return values;
    }

    // column-major, element [column * 4 + row]
    private static double[] m16(Matrix4f m) {
        double[] r = new double[16];
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                r[c * 4 + row] = m.get(c, row);
            }
        }
        return r;
    }

    private static double[] v3(Vector3f vec) {
        return new double[] {vec.x, vec.y, vec.z};
    }

    private void fillBuiltins(Minecraft mc, ClientLevel level, BlockPos eye) {
        var b = this.builtins;
        var biomeRegistry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        if (biomeRegistry != this.biomes) {
            this.biomes = biomeRegistry;
            for (var entry : biomeRegistry.entrySet()) {
                CustomUniforms.Constants.MAP.put("BIOME_" + entry.getKey().location().getPath().toUpperCase(java.util.Locale.ROOT),
                        v(biomeRegistry.getId(entry.getValue())));
            }
        }
        var biome = level.getBiome(eye);
        b.put("biome", v(biomeRegistry.getId(biome.value())));
        var precipitation = biome.value().getPrecipitationAt(eye);
        b.put("biome_precipitation", v(precipitation == net.minecraft.world.level.biome.Biome.Precipitation.NONE ? 0
                : precipitation == net.minecraft.world.level.biome.Biome.Precipitation.RAIN ? 1 : 2));
        b.put("temperature", v(biome.value().getBaseTemperature()));
        b.put("rainfall", v(0.5));
        b.put("gbufferModelView", m16(this.modelView));
        b.put("gbufferModelViewInverse", m16(this.modelViewInverse));
        b.put("gbufferProjection", m16(this.projection));
        b.put("gbufferProjectionInverse", m16(this.projectionInverse));
        b.put("gbufferPreviousModelView", m16(this.previousModelView));
        b.put("gbufferPreviousProjection", m16(this.previousProjection));
        b.put("shadowModelView", m16(this.shadowModelView));
        b.put("shadowModelViewInverse", m16(this.shadowModelViewInverse));
        b.put("shadowProjection", m16(this.shadowProjection));
        b.put("shadowProjectionInverse", m16(this.shadowProjectionInverse));
        b.put("frameCounter", v(this.frame));
        b.put("frameTime", v(this.frameTime));
        b.put("frameTimeCounter", v(this.frameTimeCounter));
        b.put("worldTime", v(this.worldTime));
        b.put("worldDay", v(this.worldDay));
        b.put("moonPhase", v(this.moonPhase));
        b.put("rainStrength", v(this.rain));
        b.put("wetness", v(this.wetness));
        b.put("thunderStrength", v(this.thunder));
        b.put("isEyeInWater", v(this.eyeInWater));
        b.put("eyeBrightness", v(this.eyeBlockX, this.eyeSkyX));
        b.put("eyeBrightnessSmooth", v(Math.round(this.smoothBlock), Math.round(this.smoothSky)));
        b.put("eyeAltitude", v(this.camera.y));
        b.put("cameraPosition", v3(this.camera));
        b.put("previousCameraPosition", v3(this.previousCamera));
        b.put("sunPosition", v3(this.sun));
        b.put("moonPosition", v3(this.moon));
        b.put("shadowLightPosition", v3(this.shadowLight));
        b.put("upPosition", v3(this.up));
        b.put("sunAngle", v(this.sunAngle));
        b.put("shadowAngle", v(this.shadowAngle));
        b.put("blindness", v(this.blindness));
        b.put("darknessFactor", v(this.darkness));
        b.put("nightVision", v(this.nightVision));
        b.put("screenBrightness", v(mc.options.gamma().get()));
        b.put("viewWidth", v(this.width));
        b.put("viewHeight", v(this.height));
        b.put("aspectRatio", v(this.width / Math.max(this.height, 1.0F)));
        b.put("near", v(0.05));
        b.put("far", v(this.far));
        b.put("fogColor", v3(this.fogColor));
        b.put("skyColor", v3(this.skyColor));
        var player = mc.player;
        b.put("is_sneaking", v(player != null && player.isShiftKeyDown() ? 1 : 0));
        b.put("is_sprinting", v(player != null && player.isSprinting() ? 1 : 0));
        b.put("is_hurt", v(player != null && player.hurtTime > 0 ? 1 : 0));
        b.put("is_invisible", v(player != null && player.isInvisible() ? 1 : 0));
        b.put("is_burning", v(player != null && player.isOnFire() ? 1 : 0));
        b.put("is_on_ground", v(player != null && player.onGround() ? 1 : 0));
        b.put("hideGUI", v(mc.options.hideGui ? 1 : 0));
        b.put("endFlashIntensity", v(0));
        b.put("dhRenderDistance", v(this.far));
        b.put("dhNearPlane", v(0.05));
        b.put("dhFarPlane", v(this.far * 4.0));
        b.put("lightningBoltPosition", v(0, 0, 0, 0));
        b.put("biome_category", v(-2));
        b.put("playerMood", v(0));
        b.put("heldItemId", v(this.held[0]));
        b.put("heldItemId2", v(this.held[1]));
        b.put("heldBlockLightValue", v(this.held[2]));
        b.put("heldBlockLightValue2", v(this.held[3]));
    }

    private void uploadCustom(PackProgram p) {
        if (this.custom == null) {
            return;
        }
        for (CustomUniforms.Entry e : this.custom.entries) {
            if (!e.uniform()) {
                continue;
            }
            int l = p.location(e.name());
            if (l < 0) {
                continue;
            }
            double[] val = this.custom.values.get(e.name());
            if (val == null) {
                continue;
            }
            switch (e.type()) {
                case "int", "bool" -> GL20.glUniform1i(l, (int) val[0]);
                case "vec2" -> GL20.glUniform2f(l, (float) val[0], (float) val[Math.min(1, val.length - 1)]);
                case "vec3" -> GL20.glUniform3f(l, (float) val[0], (float) val[Math.min(1, val.length - 1)], (float) val[Math.min(2, val.length - 1)]);
                case "vec4" -> GL20.glUniform4f(l, (float) val[0], (float) val[Math.min(1, val.length - 1)], (float) val[Math.min(2, val.length - 1)],
                        (float) val[Math.min(3, val.length - 1)]);
                default -> GL20.glUniform1f(l, (float) val[0]);
            }
        }
    }

    private static float approach(float current, float target, float dt, float halfLife) {
        if (dt <= 0.0F) {
            return current;
        }
        float k = (float) Math.pow(0.5, dt / halfLife);
        return target + (current - target) * k;
    }

    private float skyAngle;

    // light space: looking along the sun (moon at night) from 100 blocks back, the sun direction built exactly as
    // sunPosition is, so the shadow map and the pack's lighting agree; snapped to a grid against shimmering
    private void shadowMatrices() {
        Matrix4f celestial = new Matrix4f().rotateY((float) Math.toRadians(-90.0)).rotateZ((float) Math.toRadians(this.sunPathRotation))
                .rotateX(this.skyAngle * (float) (Math.PI * 2.0));
        Vector4f dir = celestial.transform(new Vector4f(0, 1, 0, 0));
        if (this.sunAngle > 0.5F) {
            dir.negate();
        }
        this.shadowModelView.identity().translate(0, 0, -100).lookAlong(-dir.x, -dir.y, -dir.z, 0.0F, 0.0F, 1.0F);
        float interval = 2.0F;
        this.shadowModelView.translate(this.camera.x % interval, this.camera.y % interval, this.camera.z % interval);
        this.shadowModelView.invert(this.shadowModelViewInverse);
        float half = this.shadowDistance;
        this.shadowProjection.setOrtho(-half, half, -half, half, 0.05F, 256.0F);
        this.shadowProjection.invert(this.shadowProjectionInverse);
    }

    public void upload(PackProgram p) {
        if (p.frame == this.frame) {
            return;
        }
        p.frame = this.frame;
        mat(p, "gbufferModelView", this.modelView);
        mat(p, "gbufferModelViewInverse", this.modelViewInverse);
        mat(p, "gbufferProjection", this.projection);
        mat(p, "gbufferProjectionInverse", this.projectionInverse);
        mat(p, "gbufferPreviousModelView", this.previousModelView);
        mat(p, "gbufferPreviousProjection", this.previousProjection);
        mat(p, "shadowModelView", this.shadowModelView);
        mat(p, "shadowModelViewInverse", this.shadowModelViewInverse);
        mat(p, "shadowProjection", this.shadowProjection);
        mat(p, "shadowProjectionInverse", this.shadowProjectionInverse);
        vec3(p, "cameraPosition", this.camera);
        vec3(p, "previousCameraPosition", this.previousCamera);
        ivec3(p, "cameraPositionInt", (int) Math.floor(this.cameraExact.x), (int) Math.floor(this.cameraExact.y), (int) Math.floor(this.cameraExact.z));
        vec3(p, "cameraPositionFract", (float) (this.cameraExact.x - Math.floor(this.cameraExact.x)), (float) (this.cameraExact.y - Math.floor(this.cameraExact.y)),
                (float) (this.cameraExact.z - Math.floor(this.cameraExact.z)));
        ivec3(p, "previousCameraPositionInt", (int) Math.floor(this.previousExact.x), (int) Math.floor(this.previousExact.y), (int) Math.floor(this.previousExact.z));
        vec3(p, "previousCameraPositionFract", (float) (this.previousExact.x - Math.floor(this.previousExact.x)),
                (float) (this.previousExact.y - Math.floor(this.previousExact.y)), (float) (this.previousExact.z - Math.floor(this.previousExact.z)));
        vec3(p, "eyePosition", this.camera);
        vec3(p, "relativeEyePosition", 0, 0, 0);
        float1(p, "eyeAltitude", this.camera.y);
        vec3(p, "sunPosition", this.sun);
        vec3(p, "moonPosition", this.moon);
        vec3(p, "upPosition", this.up);
        vec3(p, "shadowLightPosition", this.shadowLight);
        vec3(p, "fogColor", this.fogColor);
        vec3(p, "skyColor", this.skyColor);
        int1(p, "worldTime", this.worldTime);
        int1(p, "worldDay", this.worldDay);
        int1(p, "moonPhase", this.moonPhase);
        int1(p, "frameCounter", this.frame);
        float1(p, "frameTime", this.frameTime);
        float1(p, "frameTimeCounter", this.frameTimeCounter);
        float1(p, "sunAngle", this.sunAngle);
        float1(p, "shadowAngle", this.shadowAngle);
        float1(p, "rainStrength", this.rain);
        float1(p, "wetness", this.wetness);
        float1(p, "thunderStrength", this.thunder);
        int1(p, "isEyeInWater", this.eyeInWater);
        ivec2(p, "eyeBrightness", (int) this.eyeBlockX, (int) this.eyeSkyX);
        ivec2(p, "eyeBrightnessSmooth", Math.round(this.smoothBlock), Math.round(this.smoothSky));
        float1(p, "nightVision", this.nightVision);
        float1(p, "blindness", this.blindness);
        float1(p, "darknessFactor", this.darkness);
        float1(p, "darknessLightFactor", 0.0F);
        float1(p, "screenBrightness", Minecraft.getInstance().options.gamma().get().floatValue());
        float1(p, "viewWidth", this.width);
        float1(p, "viewHeight", this.height);
        float1(p, "aspectRatio", this.width / Math.max(this.height, 1.0F));
        float1(p, "near", 0.05F);
        float1(p, "far", this.far);
        float1(p, "fogStart", this.fogStart);
        float1(p, "fogEnd", this.fogEnd);
        int1(p, "fogMode", 9729);
        float1(p, "fogDensity", 0.0F);
        int1(p, "heldItemId", this.held[0]);
        int1(p, "heldItemId2", this.held[1]);
        int1(p, "heldBlockLightValue", this.held[2]);
        int1(p, "heldBlockLightValue2", this.held[3]);
        int1(p, "hideGUI", Minecraft.getInstance().options.hideGui ? 1 : 0);
        float1(p, "centerDepthSmooth", 0.99F);
        float1(p, "alphaTestRef", 0.1F);
        float1(p, "playerMood", 0.0F);
        vec4(p, "lightningBoltPosition", 0, 0, 0, 0);
        ivec2(p, "atlasSize", 1024, 1024);
        vec4(p, "entityColor", 0, 0, 0, 0);
        int1(p, "entityId", -1);
        int1(p, "blockEntityId", 0);
        int1(p, "renderStage", this.renderStage);
        // no Distant Horizons: its uniforms carry the vanilla values, as Iris does
        number(p, "dhRenderDistance", this.far);
        number(p, "dhNearPlane", 0.05F);
        number(p, "dhFarPlane", this.far * 4.0F);
        mat(p, "dhProjection", this.projection);
        mat(p, "dhProjectionInverse", this.projectionInverse);
        mat(p, "dhPreviousProjection", this.previousProjection);
        mat(p, "dhModelView", this.modelView);
        mat(p, "dhModelViewInverse", this.modelViewInverse);
        this.uploadCustom(p);
    }

    public double cameraX() {
        return this.cameraExact.x;
    }

    public double cameraY() {
        return this.cameraExact.y;
    }

    public double cameraZ() {
        return this.cameraExact.z;
    }

    static void mat(PackProgram p, String name, Matrix4f m) {
        int l = p.location(name);
        if (l >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GL20.glUniformMatrix4fv(l, false, m.get(stack.mallocFloat(16)));
            }
        }
    }

    static void vec3(PackProgram p, String name, Vector3f v) {
        vec3(p, name, v.x, v.y, v.z);
    }

    static void vec3(PackProgram p, String name, float x, float y, float z) {
        int l = p.location(name);
        if (l >= 0) {
            GL20.glUniform3f(l, x, y, z);
        }
    }

    static void vec4(PackProgram p, String name, float x, float y, float z, float w) {
        int l = p.location(name);
        if (l >= 0) {
            GL20.glUniform4f(l, x, y, z, w);
        }
    }

    static void float1(PackProgram p, String name, float x) {
        int l = p.location(name);
        if (l >= 0) {
            GL20.glUniform1f(l, x);
        }
    }

    // int or float, whichever the program declared
    static void number(PackProgram p, String name, float value) {
        int l = p.location(name);
        if (l < 0) {
            return;
        }
        int type = p.type(name);
        if (type == GL20.GL_INT || type == 0x8B56) {
            GL20.glUniform1i(l, Math.round(value));
        } else {
            GL20.glUniform1f(l, value);
        }
    }

    static void int1(PackProgram p, String name, int x) {
        int l = p.location(name);
        if (l >= 0) {
            GL20.glUniform1i(l, x);
        }
    }

    static void ivec2(PackProgram p, String name, int x, int y) {
        int l = p.location(name);
        if (l >= 0) {
            GL20.glUniform2i(l, x, y);
        }
    }

    static void ivec3(PackProgram p, String name, int x, int y, int z) {
        int l = p.location(name);
        if (l >= 0) {
            GL20.glUniform3i(l, x, y, z);
        }
    }
}
