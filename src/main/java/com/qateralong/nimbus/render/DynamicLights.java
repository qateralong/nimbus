package com.qateralong.nimbus.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.NimbusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL20;

// Light carried by entities (a torch in hand, a dropped lantern, a burning mob), recomputed every frame. Terrain gets
// it in the vertex shader (no chunk rebuilds), entities, block entities and particles through the vanilla light
// lookups. Like the block light it imitates: level minus distance, through walls.
public final class DynamicLights {
    public static final int MAX = 16;
    private static final double RANGE = 64.0;
    private static final float[] LIGHTS = new float[MAX * 4];
    private static final float[] DISTANCES = new float[MAX];
    private static int count;
    private static int frame = -1;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;

    private DynamicLights() {
    }

    // LambDynamicLights does the same its own way
    public static final boolean SUPPORTED = Terrain.supported() && !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lambdynlights");

    // terrain needs our engine and no shader pack, otherwise mobs would glow over unlit ground
    public static boolean active() {
        NimbusConfig config = NimbusConfig.get();
        return SUPPORTED && config.dynamicLights && Terrain.active() && !com.qateralong.nimbus.pack.Pipeline.active();
    }

    private static void update() {
        if (frame == Nimbus.frames) {
            return;
        }
        frame = Nimbus.frames;
        count = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !active()) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        cameraX = camera.x;
        cameraY = camera.y;
        cameraZ = camera.z;
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            int level = level(entity);
            if (level <= 0) {
                continue;
            }
            double x = net.minecraft.util.Mth.lerp(partial, entity.xOld, entity.getX());
            double y = net.minecraft.util.Mth.lerp(partial, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.6;
            double z = net.minecraft.util.Mth.lerp(partial, entity.zOld, entity.getZ());
            double dx = x - cameraX;
            double dy = y - cameraY;
            double dz = z - cameraZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance > RANGE * RANGE) {
                continue;
            }
            add((float) dx, (float) dy, (float) dz, level, (float) distance);
        }
    }

    // keeps the MAX nearest
    private static void add(float x, float y, float z, int level, float distance) {
        int at;
        if (count < MAX) {
            at = count++;
        } else {
            at = 0;
            for (int i = 1; i < MAX; i++) {
                if (DISTANCES[i] > DISTANCES[at]) {
                    at = i;
                }
            }
            if (DISTANCES[at] <= distance) {
                return;
            }
        }
        DISTANCES[at] = distance;
        LIGHTS[at * 4] = x;
        LIGHTS[at * 4 + 1] = y;
        LIGHTS[at * 4 + 2] = z;
        LIGHTS[at * 4 + 3] = level;
    }

    private static int level(Entity entity) {
        int level = 0;
        if (entity.displayFireAnimation()) {
            level = 15;
        }
        if (entity instanceof LivingEntity living) {
            level = Math.max(level, Math.max(item(living.getMainHandItem()), item(living.getOffhandItem())));
        } else if (entity instanceof ItemEntity item) {
            level = Math.max(level, item(item.getItem()));
        }
        return level;
    }

    private static int item(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.getItem() instanceof BlockItem block) {
            return block.getBlock().defaultBlockState().getLightEmission();
        }
        return stack.is(Items.LAVA_BUCKET) ? 15 : 0;
    }

    // terrain program uniforms; count 0 when off
    static void upload(int lights, int lightCount) {
        update();
        if (count > 0) {
            GL20.glUniform4fv(lights, LIGHTS);
        }
        GL20.glUniform1i(lightCount, count);
    }

    // block light at a world position, render thread only (chunk builders must never bake it in)
    public static int blockLight(double x, double y, double z) {
        if (!RenderSystem.isOnRenderThread()) {
            return 0;
        }
        update();
        float best = 0;
        for (int i = 0; i < count; i++) {
            double dx = x - cameraX - LIGHTS[i * 4];
            double dy = y - cameraY - LIGHTS[i * 4 + 1];
            double dz = z - cameraZ - LIGHTS[i * 4 + 2];
            best = Math.max(best, LIGHTS[i * 4 + 3] - (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return Math.min(15, (int) best);
    }

    // packed light with the dynamic block light folded in
    public static int apply(int packed, double x, double y, double z) {
        if (count == 0 && frame == Nimbus.frames) {
            return packed;
        }
        int dynamic = blockLight(x, y, z);
        int block = LightTexture.block(packed);
        return dynamic > block ? LightTexture.pack(dynamic, LightTexture.sky(packed)) : packed;
    }
}
