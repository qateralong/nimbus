package com.qateralong.nimbus.common;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class CommonMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        CommonConfig.load();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        String option = option(name);
        if (option != null && !CommonConfig.enabled(option)) {
            return false;
        }
        boolean lithium = FabricLoader.getInstance().isModLoaded("lithium");
        return switch (name) {
            case "StateHolderMixin" -> !FabricLoader.getInstance().isModLoaded("ferritecore") && clientFastStates();
            case "VoxelShapeMixin" -> true;
            // section counters serve both the collision and the hopper skip
            // content versions and the idle-tick bypass serve hoppers and furnaces
            case "LevelChunkTickingAccessor", "BlockEntityIdleTickMixin" -> !lithium && (CommonConfig.enabled("hopperIdle") || CommonConfig.enabled("furnaceSleep") || CommonConfig.enabled("idleBlockEntities"));
            case "CompoundContainerAccessor", "BlockEntityVersionMixin", "BaseContainerVersionMixin",
                 "HopperVersionMixin", "FurnaceVersionMixin", "DispenserVersionMixin" -> !lithium && (CommonConfig.enabled("hopperIdle") || CommonConfig.enabled("furnaceSleep"));
            case "VoxelShapeAccessor" -> !lithium && (CommonConfig.enabled("blockCollisions") || CommonConfig.enabled("shapeOverlap") || CommonConfig.enabled("boatFriction"));
            case "ChunkSectionFlagsMixin" -> !lithium && (CommonConfig.enabled("blockCollisions") || CommonConfig.enabled("dryEntities") || CommonConfig.enabled("hopperIdle") || CommonConfig.enabled("blockSearch"));
            case "EntitySectionCountMixin", "LevelEntityGetterAccessor", "LevelEntitiesAccessor" ->
                    !lithium && (CommonConfig.enabled("entityCollisions") || CommonConfig.enabled("hopperEntities") || CommonConfig.enabled("hopperIdle"));
            case "EntitySectionIterMixin" -> !lithium && CommonConfig.enabled("entityByType");
            // Lithium does its own version of the rest; two patches on the same methods are not worth the risk
            default -> !lithium;
        };
    }

    static String option(String mixin) {
        return switch (mixin) {
            case "StateHolderMixin" -> "fastStates";
            case "VoxelShapeMixin" -> "voxelShapeCache";
            case "BlockStatePathMixin", "ChunkSectionPathMixin", "WalkNodePathMixin" -> "pathfinding";
            case "PathNavigationTrackMixin", "ServerLevelNavigationMixin" -> "activeNavigations";
            case "EntitySectionsMixin" -> "entityLookup";
            case "LivingTickMixin" -> "livingTick";
            case "EntitySprintMixin" -> "sprintParticles";
            case "DirectionValuesMixin" -> "directionValues";
            case "BlockEntityTickersMixin" -> "blockEntityTickers";
            case "BiomeTemperatureMixin" -> "biomeTemperature";
            case "LevelHeightMixin" -> "levelHeight";
            case "ClassMultiMapMixin" -> "entityByType";
            case "EntityFluidEyesMixin" -> "fluidOnEyes";
            case "ChunkHeightMixin" -> "chunkHeight";
            case "GameEventDispatchMixin", "LevelChunkListenersAccessor" -> "gameEvents";
            case "CubeShapeCoordsMixin" -> "shapeCoords";
            case "DirectionOppositeMixin" -> "directionOpposite";
            case "PaletteLockMixin" -> "paletteLocks";
            case "PortalSearchMixin" -> "portalSearch";
            case "RedstoneWireMixin" -> "redstoneWire";
            case "VoxelCollideMixin" -> "fastCollide";
            case "ComposterEmptyMixin", "ComposterInputMixin", "ComposterOutputMixin" -> "composterSlots";
            case "CompoundTagAccessor", "CompoundTagMapMixin", "CompoundTagLoadMixin" -> "nbtMaps";
            case "TrackedEntitySeenMixin" -> "trackerSets";
            case "EntityPassengersMixin" -> "passengers";
            case "BlockCollisionsMixin" -> "blockCollisions";
            case "ShapesJoinMixin" -> "shapeOverlap";
            case "SecondaryPoiSensorMixin" -> "secondaryPoi";
            case "RemoveBlockGoalMixin" -> "blockSearch";
            case "FurnaceIdleMixin", "BrewingIdleMixin" -> "furnaceSleep";
            case "DistanceManagerEpochMixin", "LevelBlockTickCacheMixin" -> "blockTickChunks";
            case "LevelEntityCollisionsMixin" -> "entityCollisions";
            case "HopperEntityContainerMixin" -> "hopperEntities";
            case "EntityDryMixin" -> "dryEntities";
            case "BrainFlatMixin" -> "brainArrays";
            case "SectionStorageColumnsMixin", "PoiManagerChunkMixin", "PoiSectionTypesMixin" -> "poiColumns";
            case "HopperIdleMixin" -> "hopperIdle";
            case "EntitySectionIterMixin" -> "entityIteration";
            case "BoatFrictionMixin" -> "boatFriction";
            case "EntityFluidScanMixin" -> "fluidScan";
            case "BrainMemoriesMixin" -> "brainMaps";
            case "EntityCollidersMixin" -> "lazyColliders";
            case "ExplosionRaysMixin" -> "explosionRays";
            case "ChunkHeightmapsMixin", "HeightmapAccessor" -> "heightmaps";
            case "FluidWallMixin" -> "fluidWalls";
            case "BehaviorMemoryCacheMixin", "BrainMemoriesAccessor" -> "memoryCounting";
            case "ChunkUnloadSpinMixin" -> "unloadSpin";
            case "PalettePackMixin", "PaletteCountMixin" -> "chunkPacking";
            case "ShulkerIdleMixin", "CrafterIdleMixin", "CampfireIdleMixin" -> "idleBlockEntities";
            default -> null;
        };
    }

    // the client's own switch in nimbus.json still applies
    private static boolean clientFastStates() {
        java.nio.file.Path file = FabricLoader.getInstance().getConfigDir().resolve("nimbus.json");
        if (!java.nio.file.Files.exists(file)) {
            return true;
        }
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file)) {
            var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            return !json.has("fastStates") || json.get("fastStates").getAsBoolean();
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
