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
    public int itemDistance = 40;
    public int experienceDistance = 32;
    public int frameDistance = 56;
    public int armorStandDistance = 64;
    public int paintingDistance = 96;
    public int projectileDistance = 64;
    public int vehicleDistance = 96;
    public int mobDistance = 0;
    public int otherDistance = 0;
    public int detailDistance = 40;
    public int shadowDistance = 24;
    public int nameTagDistance = 32;
    public int blockEntityDistance = 48;
    public Map<String, Integer> blockEntityDistances = new LinkedHashMap<>(Map.of(
            "minecraft:beacon", 0, "minecraft:end_gateway", 0, "minecraft:end_portal", 0, "minecraft:conduit", 64,
            "minecraft:banner", 64, "minecraft:lectern", 32, "minecraft:enchanting_table", 32, "minecraft:campfire", 32,
            "minecraft:soul_campfire", 32, "minecraft:brushable_block", 32));
    public int signTextDistance = 16;
    public int frameContentDistance = 32;
    public boolean particleCulling = true;
    public int particleDistance = 64;
    public int maxParticles = 6000;
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
    public boolean sky = true;
    public boolean fog = true;
    public boolean weather = true;
    public boolean vignette = true;
    public boolean beaconBeams = true;
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
    public boolean steadyDebug = true;
    public int fpsOverlay = 0;
    public boolean fastStates = true;
    public int leavesCulling = 1;
    public boolean beaconCulling = true;
    public boolean signBackCulling = true;
    public boolean dedupModels = true;
    public List<String> disabledParticles = new ArrayList<>();
    public List<String> disabledAnimations = new ArrayList<>();
    public boolean debugLine = true;

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
            instance.save();
            Cull.reload();
        }
        return instance;
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
