package com.qateralong.nimbus.pack;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.mixin.ShaderInstanceAccessor;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;

// A loaded shader pack driving the frame: world draws go through its gbuffers programs into the G-buffer, then the
// deferred passes (before translucents), the composite passes and final run as full-screen passes.
public final class Pipeline {
    public enum Phase { NONE, SKY, TERRAIN, ENTITIES, BLOCK_ENTITIES, TRANSLUCENT, PARTICLES, WEATHER, CLOUDS, HAND, SHADOW }

    private static final Map<String, String> FALLBACK = new HashMap<>();
    private static final Matrix4f LIGHTMAP = new Matrix4f().scale(1.0F / 256.0F).translate(8.0F, 8.0F, 8.0F);
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Matrix4f ORTHO = new Matrix4f().setOrtho(0, 1, 0, 1, -1, 1);
    private static Pipeline active;
    private static Path activePath;
    private static boolean tried;

    static {
        String[][] f = {{"textured", "basic"}, {"textured_lit", "textured"}, {"skybasic", "basic"}, {"skytextured", "textured"}, {"clouds", "textured"},
                {"terrain", "textured_lit"}, {"terrain_solid", "terrain"}, {"terrain_cutout", "terrain"}, {"terrain_cutout_mip", "terrain"},
                {"damagedblock", "terrain"}, {"block", "terrain"}, {"block_translucent", "block"}, {"beaconbeam", "textured"}, {"item", "textured_lit"},
                {"entities", "textured_lit"}, {"entities_translucent", "entities"}, {"entities_glowing", "entities"}, {"armor_glint", "textured"},
                {"spidereyes", "textured"}, {"hand", "textured_lit"}, {"hand_water", "hand"}, {"weather", "textured_lit"}, {"water", "terrain"},
                {"line", "basic"}, {"particles", "textured_lit"}, {"particles_translucent", "particles"}, {"lightning", "entities"}};
        for (String[] pair : f) {
            FALLBACK.put("gbuffers_" + pair[0], "gbuffers_" + pair[1]);
        }
    }

    private final ShaderPack pack;
    private final PackConfig config;
    private String dir;
    private String dimension;
    private final String defines;
    public final Targets targets = new Targets();
    public final Uniforms uniforms = new Uniforms();
    public final ShadowPass shadow = new ShadowPass();
    private CustomTextures custom;
    private boolean shadowing;
    private final Map<String, PackProgram> programs = new HashMap<>();
    // a pass slot: compute shaders (name.csh, name_a.csh ...) run before the full-screen program of the same name
    private static final class Slot {
        final String name;
        final List<PackProgram> computes;
        final PackProgram pass;

        Slot(String name, List<PackProgram> computes, PackProgram pass) {
            this.name = name;
            this.computes = computes;
            this.pass = pass;
        }

        void delete() {
            for (PackProgram p : this.computes) {
                p.delete();
            }
            if (this.pass != null) {
                this.pass.delete();
            }
        }
    }

    private final List<Slot> deferred = new ArrayList<>();
    private final List<Slot> composite = new ArrayList<>();
    private final List<Slot> begin = new ArrayList<>();
    private final List<Slot> prepare = new ArrayList<>();
    private final List<Slot> shadowcomp = new ArrayList<>();
    private final List<Slot> setup = new ArrayList<>();
    private boolean setupDone;
    Images images;
    private PackProgram fin;
    private final Map<String, Integer> framebuffers = new HashMap<>();
    private int quadVao;
    private int quadBuffer;
    private boolean inWorld;
    private boolean deferredDone;
    private int savedMainFramebuffer = -1;
    private int gbufferFramebuffer;
    public Phase phase = Phase.NONE;
    public boolean oldLighting;
    public boolean clouds;
    public boolean sun;
    public boolean moon;
    public boolean vignette;
    public boolean underwaterOverlay;
    // particles.ordering: 0 after the deferred passes, 1 mixed (opaque before), 2 before; sheets = which ones the current draw takes
    public int particleOrder;
    public int particleSheets;
    private float voxelDistance;
    private PackProgram current;
    private int flipStamp;
    private final int[] highUnits = new int[32];

    public PackOptions options;

    private Pipeline(ShaderPack pack, Path optionsFile) throws java.io.IOException {
        this.pack = pack;
        this.options = PackOptions.load(pack, optionsFile);
        pack.options = this.options;
        this.defines = PackTest.defines();
        this.config = PackConfig.load(pack, this.defines);
        this.dir = pack.list("world0").isEmpty() ? "" : "world0";
        this.uniforms.custom = CustomUniforms.parse(this.config.properties);
        for (var e : this.config.properties.entrySet()) {
            if (e.getKey().startsWith("size.buffer.")) {
                int unit = unitOf(e.getKey().substring(12));
                String[] v = e.getValue().trim().split("\\s+");
                if (unit >= PackProgram.COLORTEX && unit < PackProgram.COLORTEX + Targets.MAX && v.length >= 2) {
                    try {
                        this.targets.buffers[unit - PackProgram.COLORTEX].sizeX = Float.parseFloat(v[0]);
                        this.targets.buffers[unit - PackProgram.COLORTEX].sizeY = Float.parseFloat(v[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        BlockIds.load(pack, this.config);
        EntityTags.load(pack, this.config);
        this.custom = CustomTextures.load(pack, this.config);
        this.images = Images.load(this.config);
        Images loaded = this.images;
        PackProgram.extraSetup = loaded::setup;
        BlockIds.separateAo = "true".equalsIgnoreCase(this.config.property("separateAo"));
        Glsl.separateAo = BlockIds.separateAo;
        if (com.qateralong.nimbus.render.Terrain.supported()) {
            Glsl.enginePrelude = com.qateralong.nimbus.render.Terrain.packPrelude();
        }
        this.oldLighting = "true".equalsIgnoreCase(this.config.property("oldLighting"));
        this.shadow.entities = !"false".equalsIgnoreCase(this.config.property("shadowEntities"));
        this.shadow.player = "true".equalsIgnoreCase(this.config.property("shadowPlayer"));
        this.clouds = !off(this.config.property("clouds"));
        this.sun = !off(this.config.property("sun"));
        this.moon = !off(this.config.property("moon"));
        this.vignette = !off(this.config.property("vignette"));
        this.underwaterOverlay = !off(this.config.property("underwaterOverlay"));
        this.shadow.enabled = !off(this.config.property("shadow.enabled"));
        String order = this.config.property("particles.ordering");
        this.particleOrder = "mixed".equalsIgnoreCase(order) ? 1 : "before".equalsIgnoreCase(order) ? 2 : 0;
        String dims = pack.read("dimension.properties");
        if (dims != null) {
            for (String line : Preprocessor.run(dims, new HashMap<>(this.config.macros)).split("\n")) {
                String t = line.trim();
                int eq = t.indexOf('=');
                if (t.startsWith("dimension.") && eq > 0) {
                    this.dimensions.put(t.substring(10, eq).trim(), t.substring(eq + 1).trim());
                }
            }
        }
        this.loadDimension(this.dir);
    }

    private static boolean off(String value) {
        return value != null && (value.trim().equalsIgnoreCase("off") || value.trim().equalsIgnoreCase("false"));
    }

    // OptiFine folders per dimension (dimension.properties may remap them); missing folders fall back to world0 or the root
    private String folderFor(net.minecraft.resources.ResourceLocation id) {
        String fallback = null;
        for (var e : this.dimensions.entrySet()) {
            for (String name : e.getValue().split("\\s+")) {
                if (name.equals(id.toString()) || name.equals(id.getPath())) {
                    return e.getKey();
                }
                if (name.equals("*")) {
                    fallback = e.getKey();
                }
            }
        }
        String folder = id.toString().equals("minecraft:the_nether") ? "world-1" : id.toString().equals("minecraft:the_end") ? "world1" : fallback != null ? fallback : "world0";
        try {
            if (this.pack.list(folder).isEmpty()) {
                folder = this.pack.list("world0").isEmpty() ? "" : "world0";
            }
        } catch (java.io.IOException e) {
            folder = "";
        }
        return folder;
    }

    private final Map<String, String> dimensions = new HashMap<>();

    private void loadDimension(String dir) throws java.io.IOException {
        this.dir = dir;
        this.voxelDistance = 0.0F;
        for (String file : this.pack.list(this.dir)) {
            if (!file.endsWith(".fsh")) {
                continue;
            }
            String name = file.substring(0, file.length() - 4);
            String source = this.pack.expand((this.dir.isEmpty() ? "" : this.dir + "/") + file);
            if (source != null && !name.startsWith("dh_") && !name.startsWith("clrwl_") && this.config.programEnabled(this.dir, name)) {
                String active = Preprocessor.run(source, new HashMap<>(this.config.macros));
                this.targets.scan(active);
                this.targets.use(active);
                java.util.regex.Matcher c = java.util.regex.Pattern.compile(
                        "const\\s+(?:int|float)\\s+(shadowMapResolution|shadowDistance|sunPathRotation|voxelDistance)\\s*=\\s*([-0-9.]+|[A-Za-z_]\\w*)[fF]?\\s*;").matcher(active);
                while (c.find()) {
                    float value;
                    try {
                        value = Float.parseFloat(c.group(2));
                    } catch (NumberFormatException e) {
                        String macro = this.config.macros.get(c.group(2));
                        try {
                            value = macro == null ? 0.0F : Float.parseFloat(macro.replaceAll("[fF]$", ""));
                        } catch (NumberFormatException e2) {
                            continue;
                        }
                    }
                    switch (c.group(1)) {
                        case "shadowMapResolution" -> this.shadow.resolution = Math.max(256, Math.min(8192, (int) value));
                        case "shadowDistance" -> this.uniforms.shadowDistance = value;
                        case "voxelDistance" -> this.voxelDistance = value;
                        default -> this.uniforms.sunPathRotation = value;
                    }
                }
            }
        }
        String culling = this.config.property("shadow.culling");
        this.shadow.cullKeep = off(culling) ? -1.0F : "reversed".equalsIgnoreCase(culling == null ? "" : culling.trim()) ? this.voxelDistance : 0.0F;
        java.util.Set<String> files = new java.util.HashSet<>(this.pack.list(this.dir));
        this.slots("setup", this.setup, files, false);
        this.slots("begin", this.begin, files, true);
        this.slots("shadowcomp", this.shadowcomp, files, true);
        this.slots("prepare", this.prepare, files, true);
        this.slots("deferred", this.deferred, files, true);
        this.slots("composite", this.composite, files, true);
        this.setupDone = false;
        this.fin = this.fullscreen("final");
        Nimbus.LOGGER.info("Nimbus shader pack {} ({}): {} deferred, {} composite, {} prepare, {} begin, {} setup, {} shadowcomp passes, final {}, {} color buffers, {} images",
                this.pack.name(), dir.isEmpty() ? "root" : dir, this.deferred.size(), this.composite.size(), this.prepare.size(), this.begin.size(), this.setup.size(),
                this.shadowcomp.size(), this.fin != null, Arrays.stream(this.targets.buffers).filter(b -> b.used).count(), this.images.images.size());
        StringBuilder formats = new StringBuilder();
        for (int i = 0; i < Targets.MAX; i++) {
            if (this.targets.buffers[i].used) {
                formats.append(i).append('=').append(Integer.toHexString(this.targets.buffers[i].format[0])).append(' ');
            }
        }
        Nimbus.LOGGER.info("Nimbus shader pack buffer formats: {}", formats);
        }

    public static boolean active() {
        return active != null;
    }

    public static Pipeline get() {
        return active;
    }

    // our loader only runs without Iris and Sodium (with them, Iris drives shader packs)
    public static boolean supported() {
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        return !loader.isModLoaded("iris") && !loader.isModLoaded("sodium");
    }

    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("shaderpacks");
    }

    public static List<String> packNames() {
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(folder())) {
            stream.filter(p -> Files.isDirectory(p.resolve("shaders")) || p.getFileName().toString().endsWith(".zip"))
                    .forEach(p -> names.add(p.getFileName().toString()));
        } catch (java.io.IOException ignored) {
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    // first frame: -Dnimbus.shaderPack=<path> or the configured pack
    public static void ensure() {
        if (tried) {
            return;
        }
        tried = true;
        String path = System.getProperty("nimbus.shaderPack");
        if (path != null && !path.isBlank()) {
            open(Path.of(path));
        } else {
            String name = com.qateralong.nimbus.NimbusConfig.get().shaderPack;
            if (name != null && !name.isEmpty()) {
                open(folder().resolve(name));
            }
        }
    }

    // reload the running pack (after its options changed)
    public static void reload() {
        Path path = activePath;
        RenderSystem.recordRenderCall(() -> {
            if (active != null) {
                active.close();
                active = null;
            }
            if (path != null) {
                open(path);
            }
            com.qateralong.nimbus.render.Terrain.sync();
            if (Minecraft.getInstance().level != null) {
                Minecraft.getInstance().levelRenderer.allChanged();
            }
        });
    }

    public static void select(String name) {
        tried = true;
        RenderSystem.recordRenderCall(() -> {
            if (active != null) {
                active.close();
                active = null;
            }
            if (name != null && !name.isEmpty()) {
                open(folder().resolve(name));
            }
            if (!active() ) {
                BlockIds.on = false;
            }
            com.qateralong.nimbus.render.Terrain.sync();
            if (Minecraft.getInstance().level != null) {
                Minecraft.getInstance().levelRenderer.allChanged();
            }
        });
    }

    private static void open(Path path) {
        if (!supported() || !Files.exists(path)) {
            return;
        }
        try {
            active = new Pipeline(new ShaderPack(path), path.resolveSibling(path.getFileName().toString() + ".txt"));
            activePath = path;
        } catch (Exception e) {
            Nimbus.LOGGER.warn("Nimbus shader pack {} failed to load", path, e);
            active = null;
        }
    }

    private void close() {
        for (PackProgram p : this.programs.values()) {
            if (p != null) {
                p.delete();
            }
        }
        for (List<Slot> list : List.of(this.deferred, this.composite, this.begin, this.prepare, this.shadowcomp, this.setup)) {
            for (Slot slot : list) {
                slot.delete();
            }
        }
        if (this.fin != null) {
            this.fin.delete();
        }
        if (this.images != null) {
            this.images.free();
        }
        for (int fb : this.framebuffers.values()) {
            GlStateManager._glDeleteFramebuffers(fb);
        }
        this.targets.free();
        this.shadow.free();
        if (this.custom != null) {
            this.custom.free();
        }
        try {
            this.pack.close();
        } catch (java.io.IOException ignored) {
        }
    }

    private void slots(String prefix, List<Slot> into, java.util.Set<String> files, boolean withPass) {
        for (int i = 0; i <= 99; i++) {
            String name = i == 0 ? prefix : prefix + i;
            List<PackProgram> computes = new ArrayList<>();
            if (this.config.programEnabled(this.dir, name)) {
                for (int k = -1; k < 26; k++) {
                    String file = k < 0 ? name + ".csh" : name + "_" + (char) ('a' + k) + ".csh";
                    if (!files.contains(file)) {
                        continue;
                    }
                    try {
                        PackProgram c = PackProgram.buildCompute(this.pack, this.config, (this.dir.isEmpty() ? "" : this.dir + "/") + file, name, this.defines);
                        if (c != null) {
                            computes.add(c);
                        }
                    } catch (Exception e) {
                        PackProgram.warn(file, e);
                    }
                }
            }
            PackProgram pass = withPass && files.contains(name + ".fsh") ? this.fullscreen(name) : null;
            if (!computes.isEmpty() || pass != null) {
                into.add(new Slot(name, computes, pass));
            }
        }
    }

    private void runSlots(List<Slot> slots) {
        for (Slot slot : slots) {
            for (PackProgram c : slot.computes) {
                this.dispatch(c);
            }
            if (slot.pass != null) {
                this.pass(slot.pass, false);
            }
        }
    }

    private final java.util.Set<String> dispatchLogged = new java.util.HashSet<>();

    private void imageBarrier() {
        if (!this.images.images.isEmpty()) {
            org.lwjgl.opengl.GL42.glMemoryBarrier(org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | org.lwjgl.opengl.GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
        }
    }

    private void dispatch(PackProgram p) {
        GlStateManager._glUseProgram(p.id);
        this.images.bindBuffers();
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
        this.uniforms.upload(p);
        Uniforms.mat(p, "nimbus_ModelViewMatrix", IDENTITY);
        Uniforms.mat(p, "nimbus_ProjectionMatrix", ORTHO);
        this.bindTextures(this.targets.read(0), RenderSystem.getShaderTexture(2), p);
        this.samplers(p);
        Images.bindColorImages(p, this.targets);
        int x;
        int y;
        int z;
        if (p.workGroups != null) {
            x = p.workGroups[0];
            y = p.workGroups[1];
            z = p.workGroups[2];
        } else {
            float sx = p.workGroupsRender != null ? p.workGroupsRender[0] : 1.0F;
            float sy = p.workGroupsRender != null ? p.workGroupsRender[1] : 1.0F;
            x = (int) Math.ceil(this.targets.width * sx / Math.max(1, p.localSize[0]));
            y = (int) Math.ceil(this.targets.height * sy / Math.max(1, p.localSize[1]));
            z = 1;
        }
        if (Boolean.getBoolean("nimbus.imageDebug") && this.dispatchLogged.add(p.name)) {
            Nimbus.LOGGER.info("Nimbus compute {}: {}x{}x{} groups", p.name, x, y, z);
        }
        if (x > 0 && y > 0 && z > 0) {
            GL43.glDispatchCompute(x, y, z);
            org.lwjgl.opengl.GL42.glMemoryBarrier(org.lwjgl.opengl.GL42.GL_ALL_BARRIER_BITS);
            if (GpuTimes.PASSES) {
                GpuTimes.mark(p.name);
            }
        }
    }

    private PackProgram fullscreen(String name) {
        if (!this.config.programEnabled(this.dir, name)) {
            return null;
        }
        try {
            return PackProgram.build(this.pack, this.config, this.dir, name, this.defines, null, false);
        } catch (Exception e) {
            PackProgram.warn(name, e);
            return null;
        }
    }

    // the gbuffers program for a vanilla shader in the current phase, compiled for its vertex format on first use
    private PackProgram program(ShaderInstance shader) {
        String name = this.programName(shader.getName());
        if (name == null) {
            return null;
        }
        VertexFormat format = shader.getVertexFormat();
        String key = name + "@" + System.identityHashCode(format);
        if (this.programs.containsKey(key)) {
            return this.programs.get(key);
        }
        PackProgram program = null;
        for (String n = name; n != null && program == null; n = FALLBACK.get(n)) {
            if (!this.config.programEnabled(this.dir, n)) {
                continue;
            }
            try {
                program = PackProgram.build(this.pack, this.config, this.dir, n, this.defines, format.getElementAttributeNames(), true);
            } catch (Exception e) {
                PackProgram.warn(n + " for " + shader.getName(), e);
            }
        }
        this.programs.put(key, program);
        if (Boolean.getBoolean("nimbus.tagDebug")) {
            Nimbus.LOGGER.info("Nimbus program {} -> {} ({})", shader.getName(), name, program != null);
        }
        return program;
    }

    private String programName(String shader) {
        if (this.phase == Phase.SHADOW) {
            return shader.startsWith("rendertype_entity") || shader.startsWith("rendertype_armor") || shader.startsWith("rendertype_item_entity")
                    ? "shadow" : null;
        }
        if (this.phase == Phase.HAND) {
            return shader.contains("translucent") ? "gbuffers_hand_water" : "gbuffers_hand";
        }
        boolean blockEntities = this.phase == Phase.BLOCK_ENTITIES;
        switch (shader) {
            case "rendertype_solid", "rendertype_cutout_mipped", "rendertype_cutout":
                return blockEntities ? "gbuffers_block" : "gbuffers_terrain";
            case "rendertype_translucent", "rendertype_translucent_moving_block", "rendertype_tripwire":
                return "gbuffers_water";
            case "rendertype_crumbling":
                return "gbuffers_damagedblock";
            case "rendertype_eyes":
                return "gbuffers_spidereyes";
            case "rendertype_beacon_beam":
                return "gbuffers_beaconbeam";
            case "rendertype_lightning":
                return "gbuffers_lightning";
            case "rendertype_lines":
                return "gbuffers_line";
            case "rendertype_clouds":
                return "gbuffers_clouds";
            case "rendertype_text", "rendertype_text_see_through", "rendertype_text_intensity", "rendertype_text_intensity_see_through",
                 "rendertype_text_background", "rendertype_text_background_see_through", "rendertype_breeze_wind":
                return blockEntities ? "gbuffers_block" : "gbuffers_entities";
            case "rendertype_leash":
                return "gbuffers_basic";
            case "particle":
                return this.phase == Phase.WEATHER ? "gbuffers_weather" : "gbuffers_particles";
            case "position", "position_color":
                return this.phase == Phase.SKY ? "gbuffers_skybasic" : null;
            case "position_tex", "position_tex_color":
                return this.phase == Phase.SKY ? "gbuffers_skytextured" : null;
            default:
                break;
        }
        if (shader.contains("glint")) {
            return "gbuffers_armor_glint";
        }
        if (shader.startsWith("rendertype_entity") || shader.startsWith("rendertype_armor") || shader.startsWith("rendertype_item_entity")
                || shader.equals("rendertype_energy_swirl")) {
            if (blockEntities) {
                return shader.contains("translucent") ? "gbuffers_block_translucent" : "gbuffers_block";
            }
            return shader.contains("translucent") ? "gbuffers_entities_translucent" : "gbuffers_entities";
        }
        return null;
    }

    private void unloadPrograms() {
        for (PackProgram p : this.programs.values()) {
            if (p != null) {
                p.delete();
            }
        }
        this.programs.clear();
        for (List<Slot> list : List.of(this.deferred, this.composite, this.begin, this.prepare, this.shadowcomp, this.setup)) {
            for (Slot slot : list) {
                slot.delete();
            }
            list.clear();
        }
        if (this.fin != null) {
            this.fin.delete();
            this.fin = null;
        }
        for (int fb : this.framebuffers.values()) {
            GlStateManager._glDeleteFramebuffers(fb);
        }
        this.framebuffers.clear();
        this.targets.free();
        this.shadow.free();
    }

    // world rendering starts (LevelRenderer.renderLevel)
    public void beginWorld(Camera camera, Matrix4f view, Matrix4f projection, float partial) {
        Minecraft mc = Minecraft.getInstance();
        String dimension = mc.level.dimension().location().toString();
        if (!dimension.equals(this.dimension)) {
            String folder = this.folderFor(mc.level.dimension().location());
            this.dimension = dimension;
            if (!folder.equals(this.dir)) {
                this.unloadPrograms();
                try {
                    this.loadDimension(folder);
                } catch (java.io.IOException e) {
                    Nimbus.LOGGER.warn("Nimbus shader pack: {} failed", folder, e);
                }
            }
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (this.targets.width != main.width || this.targets.height != main.height) {
            for (int fb : this.framebuffers.values()) {
                GlStateManager._glDeleteFramebuffers(fb);
            }
            this.framebuffers.clear();
            this.targets.resize(main.width, main.height);
        }
        this.flipStamp++;
        java.util.Arrays.fill(this.highUnits, -1);
        EntityTags.frame();
        this.skyTextured = false;
        this.gbufferFramebuffer = this.framebuffer(new int[] {0}, false, true);
        this.uniforms.begin(mc, camera, view, projection, partial, main.width, main.height);
        this.targets.clear(RenderSystem.getShaderFogColor());
        this.images.resize(main.width, main.height);
        this.images.clearFrame();
        this.images.bind(this.targets);
        this.inWorld = true;
        if (!this.setupDone) {
            this.setupDone = true;
            this.runSlots(this.setup);
        }
        GpuTimes.begin();
        this.runSlots(this.begin);
        this.shadowing = true;
        this.shadow.render(this, this.pack, this.config, this.dir, this.defines);
        this.shadowing = false;
        if (!this.images.images.isEmpty()) {
            // image stores of the shadow pass (voxelization) are read by shadowcomp and later passes
            org.lwjgl.opengl.GL42.glMemoryBarrier(org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | org.lwjgl.opengl.GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
        }
        GpuTimes.mark("shadow");
        this.phase = Phase.NONE;
        this.runSlots(this.shadowcomp);
        this.images.debug();
        this.runSlots(this.prepare);
        GpuTimes.mark("prepare");
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        GlStateManager._viewport(0, 0, main.width, main.height);
        RenderSystem.enableCull();
        this.savedMainFramebuffer = main.frameBufferId;
        main.frameBufferId = this.gbufferFramebuffer;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.gbufferFramebuffer);
        this.inWorld = true;
        this.deferredDone = false;
        this.phase = Phase.NONE;
    }

    // a framebuffer writing the given buffers (gbuffers: current copies and the world depth; passes: the other copies)
    private int framebuffer(int[] outputs, boolean flip, boolean depth) {
        String key = Arrays.toString(outputs) + flip + depth + "@" + this.flipKey(outputs);
        Integer fb = this.framebuffers.get(key);
        if (fb != null) {
            return fb;
        }
        int id = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
        int[] draw = new int[Math.min(outputs.length, 8)];
        for (int i = 0; i < draw.length; i++) {
            int b = outputs[i];
            int texture = b < Targets.MAX && this.targets.buffers[b].used ? this.targets.write(b, flip) : 0;
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, GL11.GL_TEXTURE_2D, texture, 0);
            draw[i] = texture == 0 ? GL11.GL_NONE : GL30.GL_COLOR_ATTACHMENT0 + i;
        }
        if (depth) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, this.targets.depth0, 0);
        }
        GL20.glDrawBuffers(draw.length == 0 ? new int[] {GL11.GL_NONE} : draw);
        this.framebuffers.put(key, id);
        return id;
    }

    private String flipKey(int[] outputs) {
        StringBuilder s = new StringBuilder();
        for (int b : outputs) {
            s.append(b < Targets.MAX ? this.targets.buffers[b].current : 0);
        }
        return s.toString();
    }

    // after a vanilla ShaderInstance.apply(): switch to the pack's program for it
    public void applied(ShaderInstance shader) {
        if (!this.inWorld) {
            return;
        }
        PackProgram p = this.program(shader);
        this.current = p;
        if (p == null) {
            return;
        }
        if (this.phase == Phase.SHADOW) {
            this.shadow.bindTarget();
        } else {
            if (p.fboStamp != this.flipStamp) {
                p.fbo = this.framebuffer(p.outputs, false, true);
                p.fboStamp = this.flipStamp;
            }
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, p.fbo);
        }
        GlStateManager._glUseProgram(p.id);
        this.images.bindBuffers();
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        this.uniforms.renderStage = this.phase == Phase.SKY ? this.skyStage(shader.getName()) : stage(this.phase);
        this.uniforms.upload(p);
        Uniforms.int1(p, "renderStage", this.uniforms.renderStage);
        if (p.tagVersion != EntityTags.version) {
            p.tagVersion = EntityTags.version;
            int tags = p.location("nimbus_Tags");
            if (tags < 0) {
                tags = p.location("nimbus_Tags[0]");
            }
            if (tags >= 0) {
                GL20.glUniform3iv(tags, EntityTags.table());
            }
        }
        Matrix4f mv = this.phase == Phase.SHADOW ? this.uniforms.shadowModelView : matrix(shader.MODEL_VIEW_MATRIX, IDENTITY);
        Matrix4f proj = this.phase == Phase.SHADOW ? this.uniforms.shadowProjection : matrix(shader.PROJECTION_MATRIX, IDENTITY);
        Uniforms.mat(p, "nimbus_ModelViewMatrix", mv);
        Uniforms.mat(p, "nimbus_ProjectionMatrix", proj);
        Uniforms.mat(p, "nimbus_ModelViewMatrixInverse", new Matrix4f(mv).invert());
        Uniforms.mat(p, "nimbus_ProjectionMatrixInverse", new Matrix4f(proj).invert());
        Uniforms.mat(p, "nimbus_TextureMatrix0", matrix(shader.TEXTURE_MATRIX, IDENTITY));
        Uniforms.mat(p, "nimbus_TextureMatrix1", LIGHTMAP);
        int l = p.location("nimbus_NormalMatrix");
        if (l >= 0) {
            Matrix3f normal = new Matrix3f(mv).invert().transpose();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GL20.glUniformMatrix3fv(l, false, normal.get(stack.mallocFloat(9)));
            }
        }
        Uniforms.vec3(p, "nimbus_ChunkOffset", 0, 0, 0);
        Uniforms.float1(p, "nimbus_AlphaRef", this.alphaTest(p, alphaRef(shader.getName())));
        float[] fog = RenderSystem.getShaderFogColor();
        Uniforms.vec4(p, "nimbus_FogColor", fog[0], fog[1], fog[2], fog[3]);
        Uniforms.float1(p, "nimbus_FogStart", RenderSystem.getShaderFogStart());
        Uniforms.float1(p, "nimbus_FogEnd", RenderSystem.getShaderFogEnd());
        this.bindTextures(RenderSystem.getShaderTexture(0), RenderSystem.getShaderTexture(2), p);
        this.samplers(p);
        this.blend(p);
    }

    private int compareSampler;

    // depth comparison for the units the program reads as sampler2DShadow
    private void samplers(PackProgram p) {
        if (this.compareSampler == 0) {
            this.compareSampler = org.lwjgl.opengl.GL33.glGenSamplers();
            org.lwjgl.opengl.GL33.glSamplerParameteri(this.compareSampler, org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE, GL30.GL_COMPARE_REF_TO_TEXTURE);
            org.lwjgl.opengl.GL33.glSamplerParameteri(this.compareSampler, org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
            org.lwjgl.opengl.GL33.glSamplerParameteri(this.compareSampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            org.lwjgl.opengl.GL33.glSamplerParameteri(this.compareSampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
        for (int unit : new int[] {PackProgram.SHADOWTEX0, PackProgram.SHADOWTEX1, PackProgram.DEPTHTEX0, PackProgram.DEPTHTEX1}) {
            org.lwjgl.opengl.GL33.glBindSampler(unit, p != null && p.compareUnits.contains(unit) ? this.compareSampler : 0);
        }
    }

    private static int blendFactor(String name) {
        return switch (name.toUpperCase(java.util.Locale.ROOT)) {
            case "ZERO" -> GL11.GL_ZERO;
            case "ONE" -> GL11.GL_ONE;
            case "SRC_COLOR" -> GL11.GL_SRC_COLOR;
            case "ONE_MINUS_SRC_COLOR" -> GL11.GL_ONE_MINUS_SRC_COLOR;
            case "DST_COLOR" -> GL11.GL_DST_COLOR;
            case "ONE_MINUS_DST_COLOR" -> GL11.GL_ONE_MINUS_DST_COLOR;
            case "SRC_ALPHA" -> GL11.GL_SRC_ALPHA;
            case "ONE_MINUS_SRC_ALPHA" -> GL11.GL_ONE_MINUS_SRC_ALPHA;
            case "DST_ALPHA" -> GL11.GL_DST_ALPHA;
            case "ONE_MINUS_DST_ALPHA" -> GL11.GL_ONE_MINUS_DST_ALPHA;
            case "SRC_ALPHA_SATURATE" -> GL11.GL_SRC_ALPHA_SATURATE;
            default -> -1;
        };
    }

    // blend.<program> = off | src dst srcAlpha dstAlpha, and per draw buffer blend.<program>.<buffer>
    private void blend(PackProgram p) {
        String value = this.config.property("blend." + p.name);
        if (value != null) {
            String v = value.trim();
            if (v.equalsIgnoreCase("off")) {
                RenderSystem.disableBlend();
            } else {
                String[] f = v.split("\\s+");
                if (f.length == 4 && blendFactor(f[0]) >= 0 && blendFactor(f[1]) >= 0 && blendFactor(f[2]) >= 0 && blendFactor(f[3]) >= 0) {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(blendFactor(f[0]), blendFactor(f[1]), blendFactor(f[2]), blendFactor(f[3]));
                }
            }
        }
        for (int i = 0; i < p.outputs.length && i < 8; i++) {
            String per = this.config.property("blend." + p.name + ".colortex" + p.outputs[i]);
            if (per == null) {
                continue;
            }
            String v = per.trim();
            if (v.equalsIgnoreCase("off")) {
                org.lwjgl.opengl.GL40.glBlendFuncSeparatei(i, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ZERO);
            } else {
                String[] f = v.split("\\s+");
                if (f.length == 4 && blendFactor(f[0]) >= 0 && blendFactor(f[1]) >= 0 && blendFactor(f[2]) >= 0 && blendFactor(f[3]) >= 0) {
                    org.lwjgl.opengl.GL40.glBlendFuncSeparatei(i, blendFactor(f[0]), blendFactor(f[1]), blendFactor(f[2]), blendFactor(f[3]));
                }
            }
            this.perBufferBlend = true;
        }
    }

    private boolean perBufferBlend;

    // alphaTest.<program> = off | <function> <reference>; only "greater"-style tests are expressed (discard at or below)
    private float alphaTest(PackProgram p, float fallback) {
        String value = this.config.property("alphaTest." + p.name);
        if (value == null) {
            return fallback;
        }
        String[] f = value.trim().split("\\s+");
        if (f[0].equalsIgnoreCase("off") || f[0].equalsIgnoreCase("false")) {
            return -1.0F;
        }
        try {
            return f.length > 1 ? Float.parseFloat(f[1]) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // what fixed-function alpha testing would have done for this vanilla shader: cutouts drop texels at or below 0.1
    private static float alphaRef(String shader) {
        if (shader.equals("rendertype_solid") || shader.equals("rendertype_translucent") || shader.equals("rendertype_translucent_moving_block")
                || shader.startsWith("position") || shader.equals("rendertype_clouds") || shader.equals("rendertype_lines")) {
            return -1.0F;
        }
        return 0.1F;
    }

    // the pack program for our engine's terrain draw of this layer, bound and set up; null to draw without the pack
    public PackProgram engineProgram(net.minecraft.client.renderer.RenderType layer, Matrix4f modelView, Matrix4f projection, double x, double y, double z) {
        if (!this.inWorld) {
            return null;
        }
        boolean shadowing = this.phase == Phase.SHADOW;
        boolean translucent = layer == net.minecraft.client.renderer.RenderType.translucent();
        if (!shadowing) {
            if (translucent) {
                this.beforeTranslucent();
                this.phase = Phase.TRANSLUCENT;
            } else {
                this.phase = Phase.TERRAIN;
            }
        }
        String name = shadowing ? "shadow" : translucent ? "gbuffers_water" : "gbuffers_terrain";
        PackProgram p = this.engine(name);
        if (p == null) {
            return null;
        }
        if (shadowing) {
            this.shadow.bindTarget();
        } else {
            if (p.fboStamp != this.flipStamp) {
                p.fbo = this.framebuffer(p.outputs, false, true);
                p.fboStamp = this.flipStamp;
            }
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, p.fbo);
        }
        GlStateManager._glUseProgram(p.id);
        this.images.bindBuffers();
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
        this.uniforms.renderStage = terrainStage(layer);
        this.uniforms.upload(p);
        Uniforms.int1(p, "renderStage", this.uniforms.renderStage);
        Matrix4f mv = shadowing ? this.uniforms.shadowModelView : modelView;
        Matrix4f proj = shadowing ? this.uniforms.shadowProjection : projection;
        Uniforms.mat(p, "nimbus_ModelViewMatrix", mv);
        Uniforms.mat(p, "nimbus_ProjectionMatrix", proj);
        Uniforms.mat(p, "nimbus_ModelViewMatrixInverse", new Matrix4f(mv).invert());
        Uniforms.mat(p, "nimbus_ProjectionMatrixInverse", new Matrix4f(proj).invert());
        Uniforms.mat(p, "nimbus_TextureMatrix0", IDENTITY);
        Uniforms.mat(p, "nimbus_TextureMatrix1", LIGHTMAP);
        int l = p.location("nimbus_NormalMatrix");
        if (l >= 0) {
            Matrix3f normal = new Matrix3f(mv).invert().transpose();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                GL20.glUniformMatrix3fv(l, false, normal.get(stack.mallocFloat(9)));
            }
        }
        float[] fog = RenderSystem.getShaderFogColor();
        Uniforms.vec4(p, "nimbus_FogColor", fog[0], fog[1], fog[2], fog[3]);
        Uniforms.float1(p, "nimbus_FogStart", RenderSystem.getShaderFogStart());
        Uniforms.float1(p, "nimbus_FogEnd", RenderSystem.getShaderFogEnd());
        boolean cutout = layer != net.minecraft.client.renderer.RenderType.solid() && !translucent;
        Uniforms.float1(p, "nimbus_AlphaRef", this.alphaTest(p, cutout || shadowing ? 0.1F : -1.0F));
        if (Boolean.getBoolean("nimbus.imageDebug") && this.dispatchLogged.add(p.name + layer)) {
            Nimbus.LOGGER.info("Nimbus engine program {} for {}: alpha ref {} at {}", p.name, layer, this.alphaTest(p, cutout || shadowing ? 0.1F : -1.0F), p.location("nimbus_AlphaRef"));
        }
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        int cb = p.location("nimbus_CameraBlock");
        if (cb >= 0) {
            GL20.glUniform3i(cb, bx, by, bz);
        }
        Uniforms.vec3(p, "nimbus_CameraFrac", (float) (x - bx), (float) (y - by), (float) (z - bz));
        if (!p.idsSet) {
            p.idsSet = true;
            int ids = p.location("nimbus_Ids");
            if (ids < 0) {
                ids = p.location("nimbus_Ids[0]");
            }
            if (ids >= 0) {
                GL20.glUniform1iv(ids, BlockIds.table());
            }
        }
        int atlas = this.minecraftAtlas();
        this.bindTextures(atlas, RenderSystem.getShaderTexture(2), p);
        this.samplers(p);
        this.blend(p);
        return p;
    }

    private int minecraftAtlas() {
        return Minecraft.getInstance().getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).getId();
    }

    private PackProgram engine(String name) {
        String key = name + "@engine";
        if (this.programs.containsKey(key)) {
            return this.programs.get(key);
        }
        PackProgram program = null;
        for (String n = name; n != null && program == null; n = FALLBACK.get(n)) {
            if (!this.config.programEnabled(this.dir, n)) {
                continue;
            }
            try {
                program = PackProgram.build(this.pack, this.config, this.dir, n, this.defines, Glsl.ENGINE, true);
            } catch (Exception e) {
                PackProgram.warn(n + " for the engine", e);
            }
        }
        this.programs.put(key, program);
        return program;
    }

    // vanilla uploads the section offset after apply(); with a pack program bound it goes to ours
    public boolean chunkOffset(float x, float y, float z) {
        if (!this.inWorld || this.current == null) {
            return false;
        }
        Uniforms.vec3(this.current, "nimbus_ChunkOffset", x, y, z);
        return true;
    }

    private static Matrix4f matrix(com.mojang.blaze3d.shaders.Uniform uniform, Matrix4f fallback) {
        if (uniform == null) {
            return fallback;
        }
        FloatBuffer b = uniform.getFloatBuffer();
        if (b == null || b.capacity() < 16) {
            return fallback;
        }
        return new Matrix4f().set(b.duplicate().rewind());
    }

    private static int unitOf(String sampler) {
        switch (sampler) {
            case "texture", "gtexture", "tex":
                return PackProgram.ATLAS;
            case "lightmap":
                return PackProgram.LIGHTMAP;
            case "normals":
                return PackProgram.NORMALS;
            case "specular":
                return PackProgram.SPECULAR;
            case "shadowtex0", "shadow", "watershadow":
                return PackProgram.SHADOWTEX0;
            case "shadowtex1":
                return PackProgram.SHADOWTEX1;
            case "shadowcolor0", "shadowcolor":
                return PackProgram.SHADOWCOLOR0;
            case "shadowcolor1":
                return PackProgram.SHADOWCOLOR1;
            case "depthtex0":
                return PackProgram.DEPTHTEX0;
            case "depthtex1":
                return PackProgram.DEPTHTEX1;
            case "depthtex2":
                return PackProgram.DEPTHTEX2;
            case "noisetex":
                return PackProgram.NOISE;
            default:
                break;
        }
        String[] legacy = {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};
        for (int i = 0; i < legacy.length; i++) {
            if (legacy[i].equals(sampler)) {
                return PackProgram.COLORTEX + i;
            }
        }
        if (sampler.startsWith("colortex")) {
            try {
                int n = Integer.parseInt(sampler.substring(8));
                return n < Targets.MAX ? PackProgram.COLORTEX + n : -1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private void bindTextures(int atlas, int lightmap, PackProgram program) {
        this.bindTextures(atlas, lightmap);
        if (this.custom != null && program != null && this.custom.bind(program.name, Pipeline::unitOf)) {
            java.util.Arrays.fill(this.highUnits, -1);
        }
    }

    private void bindTextures(int atlas, int lightmap) {
        int previous = GlStateManager._getActiveTexture();
        bind(PackProgram.ATLAS, atlas);
        bind(PackProgram.LIGHTMAP, lightmap);
        boolean real = this.shadow.ready() && !this.shadowing;
        bind(PackProgram.SHADOWTEX0, real ? this.shadow.depth0 : this.targets.shadowDepth);
        bind(PackProgram.SHADOWTEX1, real ? this.shadow.depth1 : this.targets.shadowDepth);
        bind(PackProgram.SHADOWCOLOR0, real ? this.shadow.color0 : this.targets.shadowColor);
        bind(PackProgram.SHADOWCOLOR1, real ? this.shadow.color1 : this.targets.shadowColor);
        bind(PackProgram.DEPTHTEX0, this.targets.depth0);
        bind(PackProgram.DEPTHTEX1, this.targets.depth1);
        bind(PackProgram.DEPTHTEX2, this.targets.depth2);
        bind(PackProgram.NOISE, this.targets.noise);
        for (int i = 0; i < this.targets.count(); i++) {
            if (this.targets.buffers[i].used) {
                bind(PackProgram.COLORTEX + i, this.targets.read(i));
            }
        }
        GlStateManager._activeTexture(previous);
    }

    // GlStateManager tracks the first 12 units only; higher ones are bound directly and the active unit is put back
    void bindForShadow(int atlas, int lightmap, PackProgram p) {
        this.bindTextures(atlas, lightmap, p);
        this.samplers(p);
    }

    private void bind(int unit, int texture) {
        if (unit < 12) {
            GlStateManager._activeTexture(GL20.GL_TEXTURE0 + unit);
            GlStateManager._bindTexture(texture);
        } else if (this.highUnits[unit] != texture) {
            this.highUnits[unit] = texture;
            org.lwjgl.opengl.GL13.glActiveTexture(GL20.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            org.lwjgl.opengl.GL13.glActiveTexture(GlStateManager._getActiveTexture());
        }
    }

    private boolean skyTextured;

    // the vanilla sky draws in order: dome, sunrise fan, sun, moon, stars, the dark disc below
    private int skyStage(String shader) {
        switch (shader) {
            case "position_color":
                return 2;
            case "position_tex", "position_tex_color": {
                this.skyTextured = true;
                int bound = RenderSystem.getShaderTexture(0);
                var textures = Minecraft.getInstance().getTextureManager();
                if (bound == textures.getTexture(SUN).getId()) {
                    return 4;
                }
                if (bound == textures.getTexture(MOON).getId()) {
                    return 5;
                }
                return 3;
            }
            default:
                return this.skyTextured ? 6 : 1;
        }
    }

    // 0 all, 1 opaque sheets only, 2 translucent and custom only
    public boolean particleSheet(net.minecraft.client.particle.ParticleRenderType type) {
        if (this.particleSheets == 0) {
            return true;
        }
        boolean translucent = type != net.minecraft.client.particle.ParticleRenderType.TERRAIN_SHEET
                && type != net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_OPAQUE
                && type != net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_LIT;
        return translucent == (this.particleSheets == 2);
    }

    // sun=off / moon=off: the pack draws its own
    public boolean skipSkyDraw() {
        var shader = RenderSystem.getShader();
        if (this.sun && this.moon || shader == null || !shader.getName().startsWith("position_tex")) {
            return false;
        }
        int bound = RenderSystem.getShaderTexture(0);
        var textures = Minecraft.getInstance().getTextureManager();
        return !this.sun && bound == textures.getTexture(SUN).getId() || !this.moon && bound == textures.getTexture(MOON).getId();
    }

    private static final net.minecraft.resources.ResourceLocation SUN =net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final net.minecraft.resources.ResourceLocation MOON = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

    // MC_RENDER_STAGE_TERRAIN_* by layer, in the shadow pass too (voxelizing packs check it there)
    private static int terrainStage(net.minecraft.client.renderer.RenderType layer) {
        if (layer == net.minecraft.client.renderer.RenderType.translucent()) {
            return 17;
        }
        if (layer == net.minecraft.client.renderer.RenderType.cutoutMipped()) {
            return 9;
        }
        if (layer == net.minecraft.client.renderer.RenderType.cutout()) {
            return 10;
        }
        if (layer == net.minecraft.client.renderer.RenderType.tripwire()) {
            return 18;
        }
        return 8;
    }

    private static int stage(Phase phase) {
        return switch (phase) {
            case SKY -> 1;
            case TERRAIN -> 8;
            case ENTITIES -> 11;
            case BLOCK_ENTITIES -> 12;
            case TRANSLUCENT -> 17;
            case PARTICLES -> 19;
            case CLOUDS -> 20;
            case WEATHER -> 21;
            case HAND -> 16;
            default -> 0;
        };
    }

    // translucent terrain is next: keep the opaque depth and run the deferred passes
    public void beforeTranslucent() {
        if (!this.inWorld || this.deferredDone) {
            return;
        }
        this.deferredDone = true;
        GpuTimes.mark("opaque");
        this.imageBarrier();
        copyDepth(this.targets.depth0, this.targets.depth1, this.targets.width, this.targets.height);
        this.runSlots(this.deferred);
        GpuTimes.mark("deferred");
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.gbufferFramebuffer);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    public void beforeHand() {
        if (!this.inWorld) {
            return;
        }
        if (!this.deferredDone) {
            this.beforeTranslucent();
        }
        copyDepth(this.targets.depth0, this.targets.depth2, this.targets.width, this.targets.height);
        this.phase = Phase.HAND;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.gbufferFramebuffer);
    }

    private static void copyDepth(int from, int to, int width, int height) {
        GL43.glCopyImageSubData(from, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, to, GL11.GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
    }

    // after the hand: composite passes and final into the real main target
    public void endWorld() {
        if (!this.inWorld) {
            return;
        }
        if (!this.deferredDone) {
            this.beforeTranslucent();
        }
        this.inWorld = false;
        this.phase = Phase.NONE;
        this.current = null;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        main.frameBufferId = this.savedMainFramebuffer;
        GpuTimes.mark("translucent");
        this.imageBarrier();
        this.runSlots(this.composite);
        GpuTimes.mark("composite");
        if (this.fin != null) {
            this.pass(this.fin, true);
        } else {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.framebuffer(new int[] {0}, false, false));
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        }
        if (this.debugFbo != 0) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.debugFbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, this.targets.width, this.targets.height, 0, 0, main.width, main.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        }
        this.samplers(null);
        if (this.perBufferBlend) {
            this.perBufferBlend = false;
            for (int i = 0; i < 8; i++) {
                org.lwjgl.opengl.GL40.glBlendFuncSeparatei(i, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            }
            RenderSystem.defaultBlendFunc();
        }
        main.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }

    private void pass(PackProgram p, boolean toScreen) {
        Minecraft mc = Minecraft.getInstance();
        if (toScreen) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mc.getMainRenderTarget().frameBufferId);
            GL20.glDrawBuffers(new int[] {GL30.GL_COLOR_ATTACHMENT0});
        } else {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer(p.outputs, true, false));
        }
        int vw = this.targets.width;
        int vh = this.targets.height;
        if (!toScreen && p.outputs.length > 0 && p.outputs[0] < Targets.MAX && this.targets.buffers[p.outputs[0]].used) {
            vw = this.targets.buffers[p.outputs[0]].width;
            vh = this.targets.buffers[p.outputs[0]].height;
        }
        int vx = 0;
        int vy = 0;
        String scale = this.config.property("scale." + p.name);
        if (scale != null) {
            String[] f = scale.trim().split("\\s+");
            try {
                float sc = Float.parseFloat(f[0]);
                float ox = f.length > 2 ? Float.parseFloat(f[1]) : 0.0F;
                float oy = f.length > 2 ? Float.parseFloat(f[2]) : 0.0F;
                vx = Math.round(vw * ox);
                vy = Math.round(vh * oy);
                vw = Math.max(1, Math.round(vw * sc));
                vh = Math.max(1, Math.round(vh * sc));
            } catch (NumberFormatException ignored) {
            }
        }
        GlStateManager._viewport(vx, vy, vw, vh);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        GlStateManager._glUseProgram(p.id);
        this.images.bindBuffers();
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
        this.uniforms.upload(p);
        Uniforms.mat(p, "nimbus_ModelViewMatrix", IDENTITY);
        Uniforms.mat(p, "nimbus_ProjectionMatrix", ORTHO);
        Uniforms.mat(p, "nimbus_TextureMatrix0", IDENTITY);
        this.bindTextures(this.targets.read(0), RenderSystem.getShaderTexture(2), p);
        this.samplers(p);
        this.drawQuad();
        if (!toScreen) {
            for (int b = 0; b < Targets.MAX; b++) {
                String forced = this.config.property("flip." + p.name + ".colortex" + b);
                boolean written = false;
                for (int o : p.outputs) {
                    written |= o == b;
                }
                if (forced != null && this.targets.buffers[b].used && forced.trim().equalsIgnoreCase("true") != written) {
                    if (!written) {
                        this.targets.flip(b);
                        this.flipStamp++;
                    } else {
                        // the pass wrote the other copy but asks not to flip: copy it back over the current one
                        GL43.glCopyImageSubData(this.targets.write(b, true), GL11.GL_TEXTURE_2D, 0, 0, 0, 0, this.targets.read(b), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                                this.targets.buffers[b].width, this.targets.buffers[b].height, 1);
                    }
                }
            }
            for (int b : p.outputs) {
                String forced = this.config.property("flip." + p.name + ".colortex" + b);
                if (forced != null && forced.trim().equalsIgnoreCase("false")) {
                    continue;
                }
                if (b < Targets.MAX && this.targets.buffers[b].used) {
                    this.targets.flip(b);
                    this.flipStamp++;
                    if (this.targets.buffers[b].mipmap) {
                        GlStateManager._bindTexture(this.targets.read(b));
                        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                    }
                }
            }
        }
        if (GpuTimes.PASSES) {
            GpuTimes.mark(p.name);
        }
        if (SHOW_AFTER != null && SHOW_AFTER[0].equals(p.name)) {
            this.capture(Integer.parseInt(SHOW_AFTER[1]));
        }
        RenderSystem.enableCull();
    }

    // -Dnimbus.showAfter=<pass>:<buffer>: colortex<buffer> as that pass left it replaces the frame (debugging packs)
    private static final String[] SHOW_AFTER = System.getProperty("nimbus.showAfter") == null ? null : System.getProperty("nimbus.showAfter").split(":");
    private int debugFbo;
    private int debugTex;
    private int debugRead;

    private void capture(int buffer) {
        int w = this.targets.width;
        int h = this.targets.height;
        if (this.debugFbo == 0) {
            this.debugTex = GlStateManager._genTexture();
            GlStateManager._bindTexture(this.debugTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            this.debugFbo = GlStateManager.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.debugFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.debugTex, 0);
            this.debugRead = GlStateManager.glGenFramebuffers();
        }
        Targets.Buffer b = this.targets.buffers[buffer];
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.debugRead);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.targets.read(buffer), 0);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.debugFbo);
        GL30.glBlitFramebuffer(0, 0, b.width, b.height, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private void drawQuad() {
        if (this.quadVao == 0) {
            this.quadVao = GL30.glGenVertexArrays();
            this.quadBuffer = GL15.glGenBuffers();
            GL30.glBindVertexArray(this.quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.quadBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[] {0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1}, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 20, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 20, 12);
        }
        GL30.glBindVertexArray(this.quadVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
        GL30.glBindVertexArray(0);
        com.mojang.blaze3d.vertex.BufferUploader.invalidate();
    }

    public boolean inWorld() {
        return this.inWorld;
    }
}
