package com.qateralong.nimbus;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;

public final class Cull {
    private static final Reference2IntOpenHashMap<EntityType<?>> ENTITY_LIMITS = new Reference2IntOpenHashMap<>();
    private static final Reference2IntOpenHashMap<BlockEntityType<?>> BLOCK_ENTITY_LIMITS = new Reference2IntOpenHashMap<>();
    private static final Set<BlockEntityType<?>> ALWAYS_TICK = new HashSet<>();
    private static final LongOpenHashSet VISIBLE = new LongOpenHashSet();
    private static List<SectionRenderDispatcher.RenderSection> sections;
    private static boolean sectionsDirty = true;
    private static boolean sectionsUsable;

    private static final boolean DEBUG_ALWAYS = System.getProperty("nimbus.bench") != null;
    public static boolean active;
    public static double camX;
    public static double camY;
    public static double camZ;
    public static Frustum frustum;

    public static int entitiesSeen;
    public static int entitiesCulled;
    public static int blockEntitiesSeen;
    public static int blockEntitiesCulled;
    public static int particlesSeen;
    public static int particlesCulled;
    public static int ticksSkipped;
    private static int ticksShown;
    public static int particleCount;
    public static String last = "";

    private Cull() {
    }

    static void reload() {
        ENTITY_LIMITS.clear();
        BLOCK_ENTITY_LIMITS.clear();
        ALWAYS_TICK.clear();
    }

    public static void beginFrame(double x, double y, double z, List<SectionRenderDispatcher.RenderSection> visible) {
        NimbusConfig config = NimbusConfig.get();
        active = config.enabled;
        it.unimi.dsi.fastutil.ints.IntOpenHashSet swap = hidden;
        hidden = hiddenNext;
        hiddenNext = swap;
        hiddenNext.clear();
        if (DEBUG_ALWAYS || Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
            last = (scale < 0.999 || resolution < 0.999 ? String.format("[дальность %.0f%%, разрешение %.0f%%] ", scale * 100, resolution * 100) : "") + String.format("Nimbus: сущн. %d/%d · БС %d/%d · частицы %d/%d · тиков %d · лучи %d/%d за %.1f мс",
                    entitiesCulled, entitiesSeen, blockEntitiesCulled, blockEntitiesSeen, particlesCulled, particlesSeen, ticksShown,
                    Occlusion.lastHidden, Occlusion.lastTargets, Occlusion.lastNanos / 1e6);
        }
        if (active) {
            Occlusion.frame(x, y, z);
        }
        entitiesSeen = entitiesCulled = blockEntitiesSeen = blockEntitiesCulled = particlesSeen = particlesCulled = 0;
        camX = x;
        camY = y;
        camZ = z;
        sections = visible;
        sectionsDirty = true;
    }

    public static void endTick() {
        ticksShown = ticksSkipped;
        ticksSkipped = 0;
    }

    private static double distanceSq(double x, double y, double z) {
        double dx = x - camX;
        double dy = y - camY;
        double dz = z - camZ;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double scale = 1.0;
    public static double resolution = 1.0;
    private static it.unimi.dsi.fastutil.ints.IntOpenHashSet hidden = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
    private static it.unimi.dsi.fastutil.ints.IntOpenHashSet hiddenNext = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
    private static long lastAdapt;

    private static int square(int blocks) {
        if (blocks <= 0) {
            return Integer.MAX_VALUE;
        }
        int scaled = Math.max(8, (int) Math.round(blocks * scale));
        return scaled * scaled;
    }

    public static void adapt(int fps) {
        int target = NimbusConfig.get().targetFps;
        long now = System.currentTimeMillis();
        if (now - lastAdapt < 1000) {
            return;
        }
        lastAdapt = now;
        double next = scale;
        double res = resolution;
        boolean adaptiveRes = NimbusConfig.get().adaptiveResolution;
        if (target <= 0 || !active) {
            next = 1.0;
            res = 1.0;
        } else if (fps < target * 0.95) {
            if (scale > 0.5 + 1e-6) {
                next = Math.max(0.5, scale - 0.1);
            } else if (adaptiveRes) {
                res = Math.max(0.6, resolution - 0.05);
            }
        } else if (fps > target * 1.25) {
            if (resolution < 0.999) {
                res = Math.min(1.0, resolution + 0.05);
            } else {
                next = Math.min(1.0, scale + 0.05);
            }
        }
        resolution = adaptiveRes ? res : 1.0;
        if (Math.abs(next - scale) > 1e-6) {
            scale = next;
            reload();
        }
    }

    private static int entityLimit(Entity entity) {
        EntityType<?> type = entity.getType();
        int limit = ENTITY_LIMITS.getOrDefault(type, -1);
        if (limit >= 0) {
            return limit;
        }
        NimbusConfig config = NimbusConfig.get();
        int blocks;
        if (entity instanceof Player || entity instanceof EnderDragon || entity instanceof WitherBoss) {
            blocks = 0;
        } else if (entity instanceof ItemEntity) {
            blocks = config.itemDistance;
        } else if (entity instanceof ExperienceOrb) {
            blocks = config.experienceDistance;
        } else if (entity instanceof ItemFrame) {
            blocks = config.frameDistance;
        } else if (entity instanceof ArmorStand) {
            blocks = config.armorStandDistance;
        } else if (entity instanceof Painting) {
            blocks = config.paintingDistance;
        } else if (entity instanceof Projectile) {
            blocks = config.projectileDistance;
        } else if (entity instanceof VehicleEntity) {
            blocks = config.vehicleDistance;
        } else if (entity instanceof Mob) {
            blocks = config.mobDistance;
        } else {
            blocks = config.otherDistance;
        }
        limit = square(blocks);
        ENTITY_LIMITS.put(type, limit);
        return limit;
    }

    private static boolean exempt(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity == minecraft.getCameraEntity() || entity == minecraft.player || entity.isCurrentlyGlowing() || entity.isVehicle()
                || entity.isPassenger() || entity.noCulling;
    }

    public static boolean entityVisible(Entity entity) {
        if (!active) {
            return true;
        }
        entitiesSeen++;
        if (exempt(entity)) {
            return true;
        }
        NimbusConfig config = NimbusConfig.get();
        if (!config.renderFrames && entity instanceof ItemFrame || !config.renderArmorStands && entity instanceof ArmorStand
                || !config.renderPaintings && entity instanceof Painting) {
            entitiesCulled++;
            hiddenNext.add(entity.getId());
            return false;
        }
        int limit = entityLimit(entity);
        if (limit != Integer.MAX_VALUE && distanceSq(entity.getX(), entity.getY(), entity.getZ()) > limit) {
            entitiesCulled++;
            hiddenNext.add(entity.getId());
            return false;
        }
        AABB box = entity.getBoundingBox();
        if (NimbusConfig.get().sectionCulling && !sectionVisible(box)) {
            entitiesCulled++;
            hiddenNext.add(entity.getId());
            return false;
        }
        if (box.getSize() < 24 && Occlusion.entityHidden(entity.getId(), box)) {
            entitiesCulled++;
            hiddenNext.add(entity.getId());
            return false;
        }
        return true;
    }

    private static boolean sectionVisible(AABB box) {
        if (sectionsDirty) {
            sectionsDirty = false;
            VISIBLE.clear();
            sectionsUsable = sections != null && !sections.isEmpty();
            if (sectionsUsable) {
                for (SectionRenderDispatcher.RenderSection section : sections) {
                    BlockPos origin = section.getOrigin();
                    VISIBLE.add(SectionPos.asLong(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4));
                }
            }
        }
        if (!sectionsUsable || box.getXsize() > 24 || box.getYsize() > 24 || box.getZsize() > 24) {
            return true;
        }
        int x1 = SectionPos.blockToSectionCoord(box.minX - 0.5);
        int y1 = SectionPos.blockToSectionCoord(box.minY - 0.5);
        int z1 = SectionPos.blockToSectionCoord(box.minZ - 0.5);
        int x2 = SectionPos.blockToSectionCoord(box.maxX + 0.5);
        int y2 = SectionPos.blockToSectionCoord(box.maxY + 0.5);
        int z2 = SectionPos.blockToSectionCoord(box.maxZ + 0.5);
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    if (VISIBLE.contains(SectionPos.asLong(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean detailVisible(Entity entity) {
        if (!active || entity == Minecraft.getInstance().getCameraEntity()) {
            return true;
        }
        int blocks = NimbusConfig.get().detailDistance * (entity instanceof Player ? 2 : 1);
        return blocks <= 0 || distanceSq(entity.getX(), entity.getY(), entity.getZ()) <= (double) blocks * blocks;
    }

    public static boolean shadowVisible(Entity entity) {
        int blocks = NimbusConfig.get().shadowDistance;
        return !active || blocks <= 0 || distanceSq(entity.getX(), entity.getY(), entity.getZ()) <= (double) blocks * blocks;
    }

    public static boolean nameTagVisible(Entity entity) {
        int blocks = NimbusConfig.get().nameTagDistance;
        return !active || blocks <= 0 || entity instanceof Player || distanceSq(entity.getX(), entity.getY(), entity.getZ()) <= (double) blocks * blocks;
    }

    private static int blockEntityLimit(BlockEntityType<?> type) {
        int limit = BLOCK_ENTITY_LIMITS.getOrDefault(type, -1);
        if (limit >= 0) {
            return limit;
        }
        NimbusConfig config = NimbusConfig.get();
        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        Integer blocks = key == null ? null : config.blockEntityDistances.get(key.toString());
        limit = square(blocks == null ? config.blockEntityDistance : blocks);
        BLOCK_ENTITY_LIMITS.put(type, limit);
        return limit;
    }

    public static boolean blockEntityVisible(BlockEntity blockEntity) {
        if (!active) {
            return true;
        }
        blockEntitiesSeen++;
        int limit = blockEntityLimit(blockEntity.getType());
        BlockPos pos = blockEntity.getBlockPos();
        if (limit != Integer.MAX_VALUE && distanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > limit) {
            blockEntitiesCulled++;
            return false;
        }
        if (limit == Integer.MAX_VALUE ? false : Occlusion.blockHidden(pos)) {
            blockEntitiesCulled++;
            return false;
        }
        return true;
    }

    public static boolean near(BlockPos pos, int blocks) {
        return !active || blocks <= 0 || distanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= (double) blocks * blocks;
    }

    public static boolean near(Entity entity, int blocks) {
        return !active || blocks <= 0 || distanceSq(entity.getX(), entity.getY(), entity.getZ()) <= (double) blocks * blocks;
    }

    public static boolean particleVisible(Particle particle) {
        if (!active || !NimbusConfig.get().particleCulling) {
            return true;
        }
        particlesSeen++;
        AABB box = particle.getBoundingBox();
        double x = (box.minX + box.maxX) * 0.5;
        double y = (box.minY + box.maxY) * 0.5;
        double z = (box.minZ + box.maxZ) * 0.5;
        int blocks = (int) Math.round(NimbusConfig.get().particleDistance * scale);
        if (blocks > 0 && distanceSq(x, y, z) > (double) blocks * blocks) {
            particlesCulled++;
            return false;
        }
        if (frustum != null && box.getSize() < 64 && !frustum.isVisible(box.inflate(0.5))) {
            particlesCulled++;
            return false;
        }
        return true;
    }

    public static boolean acceptParticle() {
        int max = NimbusConfig.get().maxParticles;
        if (!active || max <= 0) {
            return true;
        }
        if (particleCount >= max) {
            return false;
        }
        particleCount++;
        return true;
    }

    public static boolean tickBlockEntity(BlockEntity blockEntity) {
        if (!active || blockEntity.getLevel() == null || !blockEntity.getLevel().isClientSide()) {
            return true;
        }
        int blocks = NimbusConfig.get().blockEntityTickDistance;
        if (blocks <= 0) {
            return true;
        }
        if (ALWAYS_TICK.isEmpty()) {
            for (String id : NimbusConfig.get().alwaysTick) {
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(net.minecraft.resources.ResourceLocation.tryParse(id)).ifPresent(ALWAYS_TICK::add);
            }
            ALWAYS_TICK.add(null);
        }
        if (ALWAYS_TICK.contains(blockEntity.getType())) {
            return true;
        }
        BlockPos pos = blockEntity.getBlockPos();
        if (distanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > (double) blocks * blocks) {
            ticksSkipped++;
            return false;
        }
        return true;
    }

    public static boolean tickEntity(Entity entity) {
        if (!active || exempt(entity) || entity instanceof Player) {
            return true;
        }
        if (!(entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof ArmorStand)) {
            if (!NimbusConfig.get().hiddenTickLod || !hidden.contains(entity.getId())
                    || distanceSq(entity.getX(), entity.getY(), entity.getZ()) < 24 * 24 || (entity.getId() + entity.level().getGameTime()) % 3 == 0) {
                return true;
            }
            ticksSkipped++;
            return false;
        }
        int blocks = NimbusConfig.get().entityTickDistance;
        if (blocks <= 0 || distanceSq(entity.getX(), entity.getY(), entity.getZ()) <= (double) blocks * blocks) {
            return true;
        }
        if ((entity.getId() + entity.level().getGameTime()) % 4 == 0) {
            return true;
        }
        ticksSkipped++;
        return false;
    }
}
