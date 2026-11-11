package com.qateralong.nimbus;

import com.qateralong.nimbus.api.ModOptions;
import com.qateralong.nimbus.api.Option;
import com.qateralong.nimbus.api.OptionFlag;
import com.qateralong.nimbus.api.OptionGroup;
import com.qateralong.nimbus.api.OptionImpact;
import com.qateralong.nimbus.api.OptionPage;
import com.qateralong.nimbus.api.OptionRegistry;
import com.qateralong.nimbus.api.SliderOption;
import com.qateralong.nimbus.api.ToggleOption;
import com.qateralong.nimbus.api.VanillaOptions;
import com.qateralong.nimbus.render.Terrain;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class NimbusPages {
    private static final NimbusConfig DEFAULTS = new NimbusConfig();

    private NimbusPages() {
    }

    private static NimbusConfig config() {
        return NimbusConfig.get();
    }

    public static void register(OptionRegistry registry) {
        ModOptions mod = registry.mod("nimbus")
                .name(Component.literal("Nimbus"))
                .icon(ResourceLocation.fromNamespaceAndPath("nimbus", "icon.png"))
                .onSave(() -> config().save());
        Options o = Minecraft.getInstance().options;

        OptionPage general = page(mod, "general");
        OptionGroup main = general.group();
        List<String> presets = new ArrayList<>(List.of(Presets.NAMES));
        presets.add("custom");
        var preset = main.add(Option.<String>cycle("nimbus:preset").name(Component.translatable("nimbus.option.preset"))
                .tooltip(Component.translatable("nimbus.presets.tip"))
                .values(presets)
                .binding(() -> presets.contains(config().preset) ? config().preset : "custom", name -> {
                    if (!name.equals("custom")) {
                        Presets.apply(name);
                    }
                })
                .formatter(name -> name.equals("custom") && presets.contains(config().presetBase) && !config().presetBase.equals("custom")
                        ? Component.translatable("nimbus.preset.custom_from", Component.translatable("nimbus.preset." + config().presetBase))
                        : Component.translatable("nimbus.preset." + name))
                .flags(OptionFlag.RELOAD_CHUNKS));
        // suggests a profile from the GPU name; it only becomes pending, Apply or Undo as usual
        main.add(Option.action("nimbus:auto_profile").name(label("auto_profile")).tooltip(tip("auto_profile"))
                .button(Component.translatable("nimbus.auto.button"))
                .action(screen -> {
                    HardwareProfile.Pick pick = HardwareProfile.pick();
                    preset.set(pick.profile());
                    net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(),
                            net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.translatable("nimbus.auto.title", Component.translatable("nimbus.preset." + pick.profile())), pick.reason());
                }));
        vanilla(main, "render_distance", o.renderDistance()).impact(OptionImpact.HIGH);
        vanilla(main, "graphics_mode", o.graphicsMode()).impact(OptionImpact.HIGH);
        SliderOption renderScale = main.add(slider("render_scale", 25, 100, 5, c -> c.renderScale, (c, v) -> c.renderScale = v, "%").impact(OptionImpact.HIGH));
        vanilla(main, "framerate_limit", o.framerateLimit());
        toggle(main, "precise_limiter", c -> c.preciseLimiter, (c, v) -> c.preciseLimiter = v);
        vanilla(main, "vsync", o.enableVsync());
        vanilla(main, "simulation_distance", o.simulationDistance()).impact(OptionImpact.HIGH);
        OptionGroup display = group(general, "display");
        vanilla(display, "fullscreen", o.fullscreen());
        toggle(display, "borderless", c -> c.borderlessFullscreen, (c, v) -> c.borderlessFullscreen = v);
        vanilla(display, "gui_scale", o.guiScale()).afterApply(() -> {
            Minecraft.getInstance().options.save();
            Minecraft.getInstance().resizeDisplay();
        }).instant();
        vanilla(display, "gamma", o.gamma());
        vanilla(display, "fov", o.fov());
        vanilla(display, "bob_view", o.bobView());
        OptionGroup hud = group(general, "interface");
        var fpsOverlay = hud.add(Option.<Integer>cycle("nimbus:fps_overlay").name(label("fps_overlay")).tooltip(tip("fps_overlay"))
                .values(List.of(0, 1, 2))
                .binding(() -> config().fpsOverlay, v -> {
                    config().fpsOverlay = v;
                    custom();
                })
                .formatter(v -> Component.translatable("nimbus.corner." + v))
                .defaultValue(DEFAULTS.fpsOverlay));
        toggle(hud, "fps_graph", c -> c.fpsGraph, (c, v) -> c.fpsGraph = v).enabled(() -> !Integer.valueOf(0).equals(fpsOverlay.value()));
        toggle(hud, "toasts", c -> c.toasts, (c, v) -> c.toasts = v);
        toggle(hud, "menu_animations", c -> c.menuAnimations, (c, v) -> c.menuAnimations = v);
        toggle(hud, "fast_quit", c -> c.fastQuit, (c, v) -> c.fastQuit = v);
        toggle(hud, "quiet_reload", c -> c.quietReload, (c, v) -> c.quietReload = v);
        toggle(hud, "debug_line", c -> c.debugLine, (c, v) -> c.debugLine = v);
        toggle(hud, "steady_debug", c -> c.steadyDebug, (c, v) -> c.steadyDebug = v);

        OptionPage quality = page(mod, "quality");
        OptionGroup graphics = group(quality, "graphics");
        vanilla(graphics, "ambient_occlusion", o.ambientOcclusion()).impact(OptionImpact.LOW);
        vanilla(graphics, "biome_blend", o.biomeBlendRadius()).impact(OptionImpact.LOW);
        Option<?, ?> mipmaps = vanilla(graphics, "mipmap", o.mipmapLevels()).afterApply(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.updateMaxMipLevel(minecraft.options.mipmapLevels().get());
            minecraft.delayTextureReload();
        });
        graphics.add(Option.<Integer>cycle("nimbus:anisotropy").name(label("anisotropy")).tooltip(tip("anisotropy"))
                .values(List.of(0, 2, 4, 8, 16))
                .binding(() -> config().anisotropy, v -> {
                    config().anisotropy = v;
                    custom();
                })
                .formatter(v -> v == 0 ? Component.translatable("options.off") : Component.literal(v + "×"))
                .defaultValue(DEFAULTS.anisotropy)
                .enabled(() -> mipmaps.value() instanceof Integer levels && levels > 0));
        vanilla(graphics, "entity_shadows", o.entityShadows()).impact(OptionImpact.LOW);
        if (com.qateralong.nimbus.render.DynamicLights.SUPPORTED) {
            toggle(graphics, "dynamic_lights", c -> c.dynamicLights, (c, v) -> c.dynamicLights = v).impact(OptionImpact.LOW);
        }
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            toggle(graphics, "mip_fill", c -> c.mipFill, (c, v) -> c.mipFill = v).flags(OptionFlag.RELOAD_RESOURCES)
                    .enabled(() -> mipmaps.value() instanceof Integer levels && levels > 0);
        }
        graphics.add(slider("sharpness", 0, 100, 5, c -> c.sharpness, (c, v) -> c.sharpness = v, "%"));
        toggle(graphics, "sharp_upscale", c -> c.sharpUpscale, (c, v) -> c.sharpUpscale = v).enabled(() -> renderScale.value() < 100);
        OptionGroup world = group(quality, "world");
        ToggleOption lod = toggle(world, "lod", c -> c.lod, (c, v) -> c.lod = v);
        world.add(slider("lod_distance", 16, 128, 8, c -> c.lodDistance, (c, v) -> c.lodDistance = v, "")).enabled(() -> on(lod));
        ToggleOption fog = toggle(world, "fog", c -> c.fog, (c, v) -> c.fog = v);
        world.add(slider("fog_distance", 10, 300, 10, c -> c.fogDistance, (c, v) -> c.fogDistance = v, "%")).enabled(() -> on(fog));
        ToggleOption sky = toggle(world, "sky", c -> c.sky, (c, v) -> c.sky = v);
        toggle(world, "sun_moon", c -> c.sunMoon, (c, v) -> c.sunMoon = v).enabled(() -> on(sky));
        toggle(world, "stars", c -> c.stars, (c, v) -> c.stars = v).enabled(() -> on(sky));
        toggle(world, "weather", c -> c.weather, (c, v) -> c.weather = v);
        Option<?, ?> clouds = vanilla(world, "clouds", o.cloudStatus()).impact(OptionImpact.LOW);
        world.add(slider("cloud_height", 0, 320, 8, c -> c.cloudHeight, (c, v) -> c.cloudHeight = v, "blocks"))
                .enabled(() -> clouds.value() != net.minecraft.client.CloudStatus.OFF);
        OptionGroup effects = group(quality, "effects").collapsed(true);
        toggle(effects, "vignette", c -> c.vignette, (c, v) -> c.vignette = v);
        vanilla(effects, "screen_effects", o.screenEffectScale());
        vanilla(effects, "fov_effects", o.fovEffectScale());
        vanilla(effects, "glint_strength", o.glintStrength());
        toggle(effects, "beacon_beams", c -> c.beaconBeams, (c, v) -> c.beaconBeams = v);
        toggle(effects, "enchanting_book", c -> c.enchantingBook, (c, v) -> c.enchantingBook = v);
        toggle(effects, "piston_animation", c -> c.pistonAnimation, (c, v) -> c.pistonAnimation = v);
        toggle(effects, "instant_sneak", c -> c.instantSneak, (c, v) -> c.instantSneak = v);
        OptionGroup animations = group(quality, "animations").collapsed(true);
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            ToggleOption fade = toggle(animations, "chunk_fade", c -> c.chunkFade, (c, v) -> c.chunkFade = v);
            animations.add(slider("chunk_fade_time", 250, 2000, 250, c -> c.chunkFadeTime, (c, v) -> c.chunkFadeTime = v, "ms")).enabled(() -> on(fade));
        }
        ToggleOption animated = toggle(animations, "animated_textures", c -> c.animatedTextures, (c, v) -> c.animatedTextures = v);
        for (String kind : Animations.KINDS) {
            toggle(animations, "anim." + kind, c -> !c.disabledAnimations.contains(kind), (c, v) -> {
                c.disabledAnimations.remove(kind);
                if (!v) {
                    c.disabledAnimations.add(kind);
                }
            }).enabled(() -> on(animated));
        }

        OptionPage objects = page(mod, "objects");
        OptionGroup visibility = group(objects, "entities");
        vanilla(visibility, "entity_distance", o.entityDistanceScaling()).impact(OptionImpact.MEDIUM);
        toggle(visibility, "render_frames", c -> c.renderFrames, (c, v) -> c.renderFrames = v);
        toggle(visibility, "render_armor_stands", c -> c.renderArmorStands, (c, v) -> c.renderArmorStands = v);
        toggle(visibility, "render_paintings", c -> c.renderPaintings, (c, v) -> c.renderPaintings = v);
        OptionGroup models = group(objects, "models");
        toggle(models, "chest_models", c -> c.chestModels, (c, v) -> c.chestModels = v).afterApply(Chests::reset).flags(OptionFlag.RELOAD_CHUNKS).impact(OptionImpact.HIGH);
        toggle(models, "shulker_models", c -> c.shulkerModels, (c, v) -> c.shulkerModels = v).afterApply(Chests::reset).flags(OptionFlag.RELOAD_CHUNKS);
        toggle(models, "bed_models", c -> c.bedModels, (c, v) -> c.bedModels = v).flags(OptionFlag.RELOAD_CHUNKS);
        toggle(models, "sign_models", c -> c.signModels, (c, v) -> c.signModels = v).flags(OptionFlag.RELOAD_CHUNKS);
        toggle(models, "bell_models", c -> c.bellModels, (c, v) -> c.bellModels = v).afterApply(Chests::reset).flags(OptionFlag.RELOAD_CHUNKS);
        toggle(models, "pot_models", c -> c.potModels, (c, v) -> c.potModels = v).afterApply(Chests::reset).flags(OptionFlag.RELOAD_CHUNKS);
        OptionGroup distances = group(objects, "entity_distances").collapsed(true);
        distances.add(slider("mob", 0, 192, 8, c -> c.mobDistance, (c, v) -> c.mobDistance = v, "blocks"));
        distances.add(slider("item", 0, 128, 4, c -> c.itemDistance, (c, v) -> c.itemDistance = v, "blocks"));
        distances.add(slider("experience", 0, 128, 4, c -> c.experienceDistance, (c, v) -> c.experienceDistance = v, "blocks"));
        distances.add(slider("projectile", 0, 160, 4, c -> c.projectileDistance, (c, v) -> c.projectileDistance = v, "blocks"));
        distances.add(slider("vehicle", 0, 192, 8, c -> c.vehicleDistance, (c, v) -> c.vehicleDistance = v, "blocks"));
        distances.add(slider("frame", 0, 160, 4, c -> c.frameDistance, (c, v) -> c.frameDistance = v, "blocks"));
        distances.add(slider("armor_stand", 0, 160, 4, c -> c.armorStandDistance, (c, v) -> c.armorStandDistance = v, "blocks"));
        distances.add(slider("painting", 0, 192, 8, c -> c.paintingDistance, (c, v) -> c.paintingDistance = v, "blocks"));
        distances.add(slider("other", 0, 192, 8, c -> c.otherDistance, (c, v) -> c.otherDistance = v, "blocks"));
        distances.add(slider("detail", 0, 128, 4, c -> c.detailDistance, (c, v) -> c.detailDistance = v, "blocks"));
        distances.add(slider("shadow", 0, 64, 2, c -> c.shadowDistance, (c, v) -> c.shadowDistance = v, "blocks"));
        distances.add(slider("name_tag", 0, 64, 2, c -> c.nameTagDistance, (c, v) -> c.nameTagDistance = v, "blocks"));
        OptionGroup blockDistances = group(objects, "block_distances").collapsed(true);
        blockDistances.add(slider("block_entity", 0, 128, 4, c -> c.blockEntityDistance, (c, v) -> c.blockEntityDistance = v, "blocks").impact(OptionImpact.MEDIUM));
        blockDistances.add(slider("sign_text", 0, 64, 2, c -> c.signTextDistance, (c, v) -> c.signTextDistance = v, "blocks"));
        blockDistances.add(slider("frame_content", 0, 128, 4, c -> c.frameContentDistance, (c, v) -> c.frameContentDistance = v, "blocks"));
        blockDistances.add(slider("block_entity_tick", 0, 160, 4, c -> c.blockEntityTickDistance, (c, v) -> c.blockEntityTickDistance = v, "blocks"));

        OptionPage particles = page(mod, "particles");
        OptionGroup particleMain = group(particles, "particles");
        vanilla(particleMain, "particles", o.particles()).impact(OptionImpact.MEDIUM);
        particleMain.add(slider("particle", 0, 128, 4, c -> c.particleDistance, (c, v) -> c.particleDistance = v, "blocks"));
        particleMain.add(slider("max_particles", 0, 16000, 500, c -> c.maxParticles, (c, v) -> c.maxParticles = v, ""));
        toggle(particleMain, "block_break_particles", c -> c.blockBreakParticles, (c, v) -> c.blockBreakParticles = v);
        toggle(particleMain, "particle_culling", c -> c.particleCulling, (c, v) -> c.particleCulling = v);
        OptionGroup types = group(particles, "particle_types").collapsed(true);
        List<String> typeIds = new ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.keySet().forEach(key -> typeIds.add(key.toString()));
        typeIds.sort(null);
        for (String type : typeIds) {
            types.add(Option.toggle("nimbus:particle_type:" + type)
                    .name(Component.literal(type.startsWith("minecraft:") ? type.substring(10) : type))
                    .tooltip(Component.translatable("nimbus.option.particle_type.tip"))
                    .binding(() -> !config().disabledParticles.contains(type), v -> {
                        config().disabledParticles.remove(type);
                        if (!v) {
                            config().disabledParticles.add(type);
                        }
                        custom();
                    })
                    .defaultValue(!DEFAULTS.disabledParticles.contains(type)));
        }

        OptionPage performance = page(mod, "performance");
        OptionGroup frames = group(performance, "frame_rate");
        SliderOption targetFps = frames.add(slider("target_fps", 0, 240, 10, c -> c.targetFps, (c, v) -> c.targetFps = v, "fps"));
        toggle(frames, "adaptive_resolution", c -> c.adaptiveResolution, (c, v) -> c.adaptiveResolution = v).enabled(() -> targetFps.value() > 0);
        OptionGroup background = group(performance, "background");
        background.add(slider("background_fps", 0, 120, 5, c -> c.backgroundFps, (c, v) -> c.backgroundFps = v, "fps"));
        background.add(slider("minimized_fps", 0, 30, 1, c -> c.minimizedFps, (c, v) -> c.minimizedFps = v, "fps"));
        background.add(slider("background_volume", 0, 100, 5, c -> c.backgroundVolume, (c, v) -> c.backgroundVolume = v, "%"));
        SliderOption afk = background.add(slider("afk_minutes", 0, 30, 1, c -> c.afkMinutes, (c, v) -> c.afkMinutes = v, "min"));
        background.add(slider("afk_fps", 1, 60, 1, c -> c.afkFps, (c, v) -> c.afkFps = v, "fps")).enabled(() -> afk.value() > 0);
        OptionGroup ticking = group(performance, "ticking");
        toggle(ticking, "hidden_tick", c -> c.hiddenTickLod, (c, v) -> c.hiddenTickLod = v);
        ticking.add(slider("entity_tick", 0, 160, 4, c -> c.entityTickDistance, (c, v) -> c.entityTickDistance = v, "blocks"));

        OptionPage advanced = page(mod, "advanced");
        OptionGroup engine = group(advanced, "engine");
        toggle(engine, "enabled", c -> c.enabled, (c, v) -> c.enabled = v).afterApply(Terrain::toggled);
        if (Terrain.supported()) {
            var renderEngine = engine.add(Option.<Integer>cycle("nimbus:render_engine")
                    .name(label("render_engine"))
                    .tooltip(tip("render_engine"))
                    .values(List.of(0, 1))
                    .binding(() -> config().terrainRenderer ? 1 : 0, v -> {
                        config().terrainRenderer = v == 1;
                        custom();
                    })
                    .formatter(v -> Component.translatable("nimbus.engine." + v))
                    .defaultValue(DEFAULTS.terrainRenderer ? 1 : 0)
                    .afterApply(Terrain::toggled).impact(OptionImpact.HIGH));
            toggle(engine, "nimbus_mesher", c -> c.nimbusMesher, (c, v) -> c.nimbusMesher = v)
                    .afterApply(Terrain::toggled).enabled(() -> Integer.valueOf(1).equals(renderEngine.value()));
            toggle(engine, "gpu_frustum", c -> c.gpuFrustum, (c, v) -> c.gpuFrustum = v)
                    .afterApply(Terrain::toggled).enabled(() -> Integer.valueOf(1).equals(renderEngine.value())).impact(OptionImpact.MEDIUM);
            toggle(engine, "gpu_occlusion", c -> c.gpuOcclusion, (c, v) -> c.gpuOcclusion = v)
                    .enabled(() -> Integer.valueOf(1).equals(renderEngine.value())).impact(OptionImpact.MEDIUM);
            toggle(engine, "own_traversal", c -> c.ownTraversal, (c, v) -> c.ownTraversal = v)
                    .afterApply(Terrain::toggled).enabled(() -> Integer.valueOf(1).equals(renderEngine.value())).impact(OptionImpact.MEDIUM);
            toggle(engine, "own_translucent_sort", c -> c.ownTranslucentSort, (c, v) -> c.ownTranslucentSort = v)
                    .enabled(() -> Integer.valueOf(1).equals(renderEngine.value()));
        }
        OptionGroup rendering = group(advanced, "rendering");
        rendering.add(slider("render_ahead", 0, 4, 1, c -> c.renderAhead, (c, v) -> c.renderAhead = v, "frames"));
        vanilla(rendering, "chunk_updates", o.prioritizeChunkUpdates());
        toggle(rendering, "section_culling", c -> c.sectionCulling, (c, v) -> c.sectionCulling = v).impact(OptionImpact.MEDIUM);
        toggle(rendering, "occlusion_culling", c -> c.occlusionCulling, (c, v) -> c.occlusionCulling = v).impact(OptionImpact.MEDIUM);
        rendering.add(slider("leaves_culling", 0, 2, 1, c -> c.leavesCulling, (c, v) -> c.leavesCulling = v, "leaves").flags(OptionFlag.RELOAD_CHUNKS).impact(OptionImpact.MEDIUM));
        toggle(rendering, "beacon_culling", c -> c.beaconCulling, (c, v) -> c.beaconCulling = v);
        toggle(rendering, "sign_back_culling", c -> c.signBackCulling, (c, v) -> c.signBackCulling = v);
        OptionGroup optimizations = group(advanced, "optimizations").collapsed(true);
        toggle(optimizations, "fast_states", c -> c.fastStates, (c, v) -> c.fastStates = v);
        toggle(optimizations, "dedup_models", c -> c.dedupModels, (c, v) -> c.dedupModels = v);
        toggle(optimizations, "stream_uploads", c -> c.streamUploads, (c, v) -> c.streamUploads = v);
        toggle(optimizations, "gui_batching", c -> c.guiBatching, (c, v) -> c.guiBatching = v);
        toggle(optimizations, "item_icon_cache", c -> c.itemIconCache, (c, v) -> c.itemIconCache = v);
        ToggleOption hudCache = toggle(optimizations, "hud_cache", c -> c.hudCache, (c, v) -> c.hudCache = v);
        optimizations.add(slider("hud_fps", 20, 144, 4, c -> c.hudFps, (c, v) -> c.hudFps = v, "fps")).enabled(() -> on(hudCache));
        toggle(optimizations, "light_engine", c -> c.lightEngine, (c, v) -> c.lightEngine = v);
        toggle(optimizations, "chunk_decode", c -> c.chunkDecode, (c, v) -> c.chunkDecode = v);
        toggle(optimizations, "lazy_search", c -> c.lazySearch, (c, v) -> c.lazySearch = v);
        toggle(optimizations, "async_screenshots", c -> c.asyncScreenshots, (c, v) -> c.asyncScreenshots = v);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            toggle(optimizations, "sodium_graph_cache", c -> c.sodiumGraphCache, (c, v) -> c.sodiumGraphCache = v);
        }
        toggle(optimizations, "biome_cache", c -> c.biomeCache, (c, v) -> c.biomeCache = v);
        toggle(optimizations, "fast_biome_blend", c -> c.fastBiomeBlend, (c, v) -> c.fastBiomeBlend = v);
        toggle(optimizations, "fast_clouds", c -> c.fastClouds, (c, v) -> c.fastClouds = v);
        toggle(optimizations, "entity_light_cache", c -> c.entityLightCache, (c, v) -> c.entityLightCache = v);
        toggle(optimizations, "entity_grouping", c -> c.entityGrouping, (c, v) -> c.entityGrouping = v);
        toggle(optimizations, "entity_batching", c -> c.entityBatching, (c, v) -> c.entityBatching = v);
        toggle(optimizations, "entity_instancing", c -> c.entityInstancing, (c, v) -> c.entityInstancing = v);
        toggle(optimizations, "map_atlas", c -> c.mapAtlas, (c, v) -> c.mapAtlas = v);
        toggle(optimizations, "particle_light_cache", c -> c.particleLightCache, (c, v) -> c.particleLightCache = v);
        toggle(optimizations, "particle_instancing", c -> c.particleInstancing, (c, v) -> c.particleInstancing = v);
        toggle(optimizations, "weather_cache", c -> c.weatherCache, (c, v) -> c.weatherCache = v);
        toggle(optimizations, "fluid_cache", c -> c.fluidCache, (c, v) -> c.fluidCache = v);
        if (Terrain.supported()) {
            toggle(optimizations, "section_light_cache", c -> c.sectionLightCache, (c, v) -> c.sectionLightCache = v);
        }
    }

    private static OptionPage page(ModOptions mod, String id) {
        return mod.page(id, Component.translatable("nimbus.tab." + id));
    }

    private static OptionGroup group(OptionPage page, String id) {
        return page.group(Component.translatable("nimbus.group." + id));
    }

    // dependent options follow the parent's pending value, so they appear and vanish before Apply
    private static boolean on(ToggleOption option) {
        return Boolean.TRUE.equals(option.value());
    }

    private static void custom() {
        config().preset = "custom";
    }

    private static ToggleOption toggle(OptionGroup group, String key, Function<NimbusConfig, Boolean> get, BiConsumer<NimbusConfig, Boolean> set) {
        return group.add(Option.toggle("nimbus:" + key)
                .name(label(key))
                .tooltip(tip(key))
                .binding(() -> get.apply(config()), v -> {
                    set.accept(config(), v);
                    custom();
                })
                .defaultValue(get.apply(DEFAULTS)));
    }

    private static SliderOption slider(String key, int min, int max, int step, Function<NimbusConfig, Integer> get, BiConsumer<NimbusConfig, Integer> set, String unit) {
        return Option.slider("nimbus:" + key)
                .name(label(key))
                .tooltip(tip(key))
                .range(min, max, step)
                .binding(() -> get.apply(config()), v -> {
                    set.accept(config(), v);
                    custom();
                })
                .formatter(value -> format(key, min, unit, value))
                .defaultValue(get.apply(DEFAULTS));
    }

    private static Option<?, ?> vanilla(OptionGroup group, String key, OptionInstance<?> instance) {
        Option<?, ?> option = VanillaOptions.wrap("minecraft:" + key, instance);
        if (option == null) {
            return Option.action("minecraft:" + key);
        }
        // vanilla captions are short and clash with our groups ("Interface" was the GUI scale): our own wording
        if (net.minecraft.client.resources.language.I18n.exists("nimbus.vanilla." + key)) {
            option.name(Component.translatable("nimbus.vanilla." + key));
        }
        if (option instanceof SliderOption slider && key.equals("biome_blend")) {
            slider.formatter(v -> v == 0 ? Component.translatable("options.off") : Component.literal((2 * v + 1) + "×" + (2 * v + 1)));
        }
        return group.add(option);
    }

    public static Component label(String key) {
        return Component.translatable("nimbus.option." + key);
    }

    public static Component tip(String key) {
        return Component.translatable("nimbus.option." + key + ".tip");
    }

    public static Component format(String key, int min, String unit, int value) {
        switch (unit) {
            case "engine" -> {
                return Component.translatable("nimbus.engine." + value);
            }
            case "leaves" -> {
                return Component.translatable("nimbus.leaves." + value);
            }
            case "corner" -> {
                return Component.translatable("nimbus.corner." + value);
            }
            default -> {
            }
        }
        if (value == 0 && key.equals("cloud_height")) {
            return Component.translatable("nimbus.default");
        }
        if (value == 0 && min == 0 && !key.equals("sharpness")) {
            return Component.translatable(List.of("target_fps", "render_ahead", "background_fps", "minimized_fps", "afk_minutes").contains(key)
                    ? "options.off" : "nimbus.unlimited");
        }
        return Component.literal(value + switch (unit) {
            case "blocks" -> " " + Component.translatable("nimbus.blocks").getString();
            case "frames" -> " " + Component.translatable("nimbus.frames").getString();
            case "fps" -> " FPS";
            case "min" -> " " + Component.translatable("nimbus.minutes").getString();
            case "ms" -> " " + Component.translatable("nimbus.ms").getString();
            default -> unit;
        });
    }
}
