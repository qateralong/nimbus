package com.qateralong.nimbus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class NimbusConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nimbus.json");
    private static NimbusConfig instance;

    public boolean enabled = true;
    public boolean sectionCulling = true;
    public boolean occlusionCulling = true;
    public int itemDistance = 0;
    public int experienceDistance = 0;
    public int frameDistance = 0;
    public int armorStandDistance = 0;
    public int paintingDistance = 0;
    public int projectileDistance = 0;
    public int vehicleDistance = 0;
    public int mobDistance = 0;
    public int otherDistance = 0;
    public int detailDistance = 0;
    public int shadowDistance = 0;
    public int nameTagDistance = 0;
    public int blockEntityDistance = 64;
    public Map<String, Integer> blockEntityDistances = new LinkedHashMap<>(Map.of(
            "minecraft:beacon", 0, "minecraft:end_gateway", 0, "minecraft:end_portal", 0));
    public int signTextDistance = 0;
    public int frameContentDistance = 0;
    public boolean particleCulling = true;
    public int particleDistance = 0;
    public int maxParticles = 0;
    public int blockEntityTickDistance = 64;
    public List<String> alwaysTick = new ArrayList<>(List.of("minecraft:beacon", "minecraft:end_gateway", "minecraft:jukebox"));
    public int entityTickDistance = 48;
    public int targetFps = 0;
    public boolean adaptiveResolution = true;
    public int renderScale = 100;
    public boolean sharpUpscale = false;
    public int sharpness = 0;
    public boolean hiddenTickLod = true;
    public boolean borderlessFullscreen = true;
    public boolean streamUploads = true;
    public boolean guiBatching = true;
    public boolean sodiumGraphCache = true;
    public boolean biomeCache = true;
    public boolean fastBiomeBlend = true;
    public boolean fastClouds = true;
    public boolean ownTranslucentSort = true;
    public boolean terrainRenderer = true;
    public boolean ownTraversal = true;
    public boolean nimbusMesher = true;
    public boolean gpuOcclusion = false;
    public boolean gpuFrustum = true;
    public boolean signTextCache = true;
    public boolean guiItemBatching = true;
    public boolean itemIconCache = true;
    public boolean hudCache = true;
    public boolean lightEngine = true;
    public boolean chunkDecode = true;
    public int hudFps = 60;
    public boolean sectionLightCache = true;
    public boolean entityLightCache = true;
    public boolean entityGrouping = true;
    public boolean entityBatching = true;
    public boolean mapAtlas = true;
    public boolean particleLightCache = true;
    public boolean particleInstancing = true;
    public boolean entityInstancing = true;
    public boolean lod = false;
    // file or folder name in .minecraft/shaderpacks, empty = off
    public String shaderPack = "";
    public int lodDistance = 48;
    public boolean weatherCache = true;
    public boolean fluidCache = true;
    public boolean bedModels = true;
    public boolean shulkerModels = true;
    public boolean signModels = true;
    public boolean bellModels = true;
    public boolean potModels = true;
    public boolean sky = true;
    public boolean fog = true;
    public boolean weather = true;
    public boolean vignette = true;
    public boolean beaconBeams = true;
    public boolean enchantingBook = true;
    public boolean pistonAnimation = true;
    public boolean instantSneak = false;
    public boolean animatedTextures = true;
    public boolean blockBreakParticles = true;
    public int renderAhead = 0;
    public String preset = "none";
    public boolean sunMoon = true;
    public boolean stars = true;
    public int cloudHeight = 0;
    public int fogDistance = 100;
    public boolean renderFrames = true;
    public boolean renderArmorStands = true;
    public boolean renderPaintings = true;
    public boolean toasts = true;
    public boolean fastQuit = true;
    public boolean quietReload = true;
    public boolean steadyDebug = true;
    public int fpsOverlay = 0;
    public boolean fastStates = true;
    public int backgroundFps = 30;
    public int minimizedFps = 1;
    public int afkMinutes = 0;
    public int afkFps = 10;
    public int backgroundVolume = 100;
    public boolean chestModels = true;
    public int leavesCulling = 0;
    public boolean beaconCulling = true;
    public boolean signBackCulling = true;
    public boolean dedupModels = true;
    public boolean mipFill = false;
    public boolean lazySearch = true;
    public List<String> disabledParticles = new ArrayList<>();
    public List<String> disabledAnimations = new ArrayList<>();
    public boolean debugLine = false;
    public int configVersion = 0;

    public static NimbusConfig get() {
        if (instance == null) {
            instance = new NimbusConfig();
            try {
                if (Files.exists(FILE)) {
                    try (Reader reader = Files.newBufferedReader(FILE)) {
                        NimbusConfig loaded = GSON.fromJson(reader, NimbusConfig.class);
                        if (loaded != null) {
                            instance = loaded;
                        }
                    }
                }
            } catch (Exception e) {
                Nimbus.LOGGER.warn("Failed to read nimbus.json", e);
            }
            instance.migrate();
            instance.save();
            Cull.reload();
        }
        return instance;
    }

    private static final int CONFIG_VERSION = 3;

    // 2: own terrain engine on by default (it falls back to vanilla on its own when unsupported or Sodium is present)
    private void migrate() {
        if (this.configVersion < 2) {
            this.terrainRenderer = true;
        }
        if (this.configVersion < 3) {
            this.invisibleDefaults();
        }
        this.configVersion = CONFIG_VERSION;
    }

    // 3: defaults change nothing visible; values still at the old defaults move to the new ones
    private void invisibleDefaults() {
        NimbusConfig d = new NimbusConfig();
        if (this.itemDistance == 40) this.itemDistance = d.itemDistance;
        if (this.experienceDistance == 32) this.experienceDistance = d.experienceDistance;
        if (this.frameDistance == 56) this.frameDistance = d.frameDistance;
        if (this.armorStandDistance == 64) this.armorStandDistance = d.armorStandDistance;
        if (this.paintingDistance == 96) this.paintingDistance = d.paintingDistance;
        if (this.projectileDistance == 64) this.projectileDistance = d.projectileDistance;
        if (this.vehicleDistance == 96) this.vehicleDistance = d.vehicleDistance;
        if (this.detailDistance == 40) this.detailDistance = d.detailDistance;
        if (this.shadowDistance == 24) this.shadowDistance = d.shadowDistance;
        if (this.nameTagDistance == 32) this.nameTagDistance = d.nameTagDistance;
        if (this.blockEntityDistance == 48) this.blockEntityDistance = d.blockEntityDistance;
        if (this.signTextDistance == 16) this.signTextDistance = d.signTextDistance;
        if (this.frameContentDistance == 32) this.frameContentDistance = d.frameContentDistance;
        if (this.particleDistance == 64) this.particleDistance = d.particleDistance;
        if (this.maxParticles == 6000) this.maxParticles = d.maxParticles;
        if (this.leavesCulling == 1) this.leavesCulling = d.leavesCulling;
        this.fog = true;
        this.lod = false;
        this.debugLine = false;
        Map<String, Integer> old = Map.of("minecraft:conduit", 64, "minecraft:banner", 64, "minecraft:lectern", 32, "minecraft:enchanting_table", 32,
                "minecraft:campfire", 32, "minecraft:soul_campfire", 32, "minecraft:brushable_block", 32);
        old.forEach((key, value) -> this.blockEntityDistances.remove(key, value));
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            Nimbus.LOGGER.warn("Failed to write nimbus.json", e);
        }
        Cull.reload();
    }
}
