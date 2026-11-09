package com.qateralong.nimbus.common;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

// config/nimbus-common.properties: one switch per shared (client and server) optimization, read before mixins apply,
// so a misbehaving one can be turned off on a server without a client.
public final class CommonConfig {
    static final List<String> OPTIONS = List.of("fastStates", "voxelShapeCache", "pathfinding", "activeNavigations", "entityLookup", "livingTick", "sprintParticles", "directionValues", "blockEntityTickers", "biomeTemperature", "levelHeight", "entityByType", "fluidOnEyes", "chunkHeight", "gameEvents", "shapeCoords", "directionOpposite", "paletteLocks", "portalSearch", "redstoneWire", "fastCollide", "composterSlots", "nbtMaps", "trackerSets", "passengers", "entityCollisions", "blockCollisions", "hopperEntities", "dryEntities", "brainArrays", "poiColumns", "hopperIdle", "shapeOverlap", "blockTickChunks", "secondaryPoi", "blockSearch", "furnaceSleep", "idleBlockEntities", "entityIteration", "boatFriction", "fluidScan", "brainMaps", "lazyColliders", "explosionRays", "heightmaps", "fluidWalls", "memoryCounting", "unloadSpin", "chunkPacking", "chunkCodec");
    private static final Properties VALUES = new Properties();
    private static boolean loaded;

    private CommonConfig() {
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = FabricLoader.getInstance().getConfigDir().resolve("nimbus-common.properties");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                VALUES.load(reader);
            } catch (IOException ignored) {
            }
        }
        boolean missing = false;
        for (String option : OPTIONS) {
            if (!VALUES.containsKey(option)) {
                VALUES.setProperty(option, "true");
                missing = true;
            }
        }
        if (missing) {
            try {
                Files.createDirectories(file.getParent());
                try (Writer writer = Files.newBufferedWriter(file)) {
                    VALUES.store(writer, "Nimbus shared optimizations (client and server); false turns one off, restart needed");
                }
            } catch (IOException ignored) {
            }
        }
    }

    public static boolean enabled(String option) {
        load();
        return !"false".equalsIgnoreCase(VALUES.getProperty(option, "true").trim());
    }

    public static int active() {
        load();
        int count = 0;
        for (String option : OPTIONS) {
            if (enabled(option)) {
                count++;
            }
        }
        return count;
    }
}
