package com.qateralong.nimbus.lod;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.NimbusConfig;
import com.qateralong.nimbus.mixin.ShaderInstanceAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

// Distant terrain from LodStore: tiles of 8x8 chunks meshed off-thread as height fields (tops plus walls down to the
// lower neighbour), drawn after the opaque terrain layers and dropped wherever the real world is rendered.
public final class LodRenderer {
    private static final int TILE_CHUNKS = 8;
    private static final int TILE_CELLS = TILE_CHUNKS * LodStore.CELLS;
    private static final int VERTEX = 16;
    private static final int MAX_QUADS = 16384;
    private static final int MAX_BUILDS = 4;
    private static final Matrix4f MVP = new Matrix4f();
    private static final Long2ObjectOpenHashMap<Tile> TILES = new Long2ObjectOpenHashMap<>();
    private static final ConcurrentLinkedQueue<Runnable> UPLOADS = new ConcurrentLinkedQueue<>();
    private static int state;
    private static int program;
    private static int vao;
    private static int indexBuffer;
    private static int uMvp;
    private static int uOffset;
    private static int uInner;
    private static int uFogStart;
    private static int uFogEnd;
    private static int uFogColor;
    private static int building;
    private static ClientLevel level;
    private static int storeVersion = -1;

    private static final class Tile {
        final int x;
        final int z;
        int buffer;
        int quads;
        int builtVersion = -1;
        int builtLevel = -1;
        boolean pending;
        int minY;
        int maxY;
        int seen;

        Tile(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    private LodRenderer() {
    }

    public static boolean active() {
        NimbusConfig config = NimbusConfig.get();
        if (!config.enabled || !config.lod || com.qateralong.nimbus.Compat.shadersOn()) {
            return false;
        }
        // a height field of a world with a roof (the Nether) is that roof
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level == null || !level.dimensionType().hasCeiling();
    }

    public static float depthFar(float vanilla) {
        if (!active()) {
            return vanilla;
        }
        return Math.max(vanilla, (NimbusConfig.get().lodDistance + TILE_CHUNKS) * 16.0F * 1.5F);
    }

    // world joins and dimension changes open the matching LOD folder
    public static void sync(Minecraft minecraft) {
        if (minecraft.level != level) {
            level = minecraft.level;
            clear();
            if (level == null) {
                LodStore.leave();
            } else {
                LodStore.join(minecraft);
            }
        }
    }

    public static int drawnTiles;

    public static String debug() {
        int built = 0;
        int quads = 0;
        for (Tile tile : TILES.values()) {
            if (tile.quads > 0) {
                built++;
                quads += tile.quads;
            }
        }
        return "lod " + (active() ? "on" : "off") + " (" + (state == 1 ? "ready" : state == 0 ? "unused" : "unavailable") + "), " + TILES.size() + " tiles, "
                + built + " built, " + quads + " quads, " + drawnTiles + " drawn last frame, " + building + " building";
    }

    public static void leave() {
        level = null;
        clear();
        LodStore.leave();
    }

    private static void clear() {
        for (Tile tile : TILES.values()) {
            if (tile.buffer != 0) {
                GlStateManager._glDeleteBuffers(tile.buffer);
            }
        }
        TILES.clear();
        UPLOADS.clear();
        building = 0;
    }

    private static boolean available() {
        if (state == 0) {
            state = init() ? 1 : -1;
        }
        return state == 1;
    }

    private static boolean init() {
        if (!com.qateralong.nimbus.render.Terrain.supported()) {
            return false;
        }
        try {
            int vertex = compile(GL20.GL_VERTEX_SHADER, source("lod.vsh"));
            int fragment = compile(GL20.GL_FRAGMENT_SHADER, source("lod.fsh"));
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                Nimbus.LOGGER.warn("Nimbus LOD program link failed: {}", GL20.glGetProgramInfoLog(program));
                return false;
            }
            uMvp = GL20.glGetUniformLocation(program, "MvpMat");
            uOffset = GL20.glGetUniformLocation(program, "Offset");
            uInner = GL20.glGetUniformLocation(program, "Inner");
            uFogStart = GL20.glGetUniformLocation(program, "FogStart");
            uFogEnd = GL20.glGetUniformLocation(program, "FogEnd");
            uFogColor = GL20.glGetUniformLocation(program, "FogColor");
            GlStateManager._glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler2"), 2);
            GlStateManager._glUseProgram(0);
            ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
            ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
            indexBuffer = GlStateManager._glGenBuffers();
            ByteBuffer indices = MemoryUtil.memAlloc(MAX_QUADS * 12);
            for (int q = 0; q < MAX_QUADS; q++) {
                int v = q * 4;
                indices.putShort((short) v).putShort((short) (v + 1)).putShort((short) (v + 2));
                indices.putShort((short) (v + 2)).putShort((short) (v + 3)).putShort((short) v);
            }
            indices.flip();
            vao = GL30.glGenVertexArrays();
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(indices);
            GL20.glEnableVertexAttribArray(0);
            GL43.glVertexAttribFormat(0, 3, GL11.GL_FLOAT, false, 0);
            GL43.glVertexAttribBinding(0, 0);
            GL20.glEnableVertexAttribArray(1);
            GL43.glVertexAttribFormat(1, 4, GL11.GL_UNSIGNED_BYTE, true, 12);
            GL43.glVertexAttribBinding(1, 0);
            GlStateManager._glBindVertexArray(0);
            BufferUploader.invalidate();
            Nimbus.LOGGER.info("Nimbus LOD renderer ready");
            return true;
        } catch (Throwable t) {
            Nimbus.LOGGER.warn("Nimbus LOD renderer unavailable", t);
            return false;
        }
    }

    private static int compile(int type, String text) throws java.io.IOException {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, text);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new java.io.IOException("LOD shader compile failed: " + log);
        }
        return shader;
    }

    private static String source(String name) throws java.io.IOException {
        try (var stream = LodRenderer.class.getResourceAsStream("/assets/nimbus/shaders/lod/" + name)) {
            if (stream == null) {
                throw new java.io.IOException("Missing shader " + name);
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static long key(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }

    public static void render(Matrix4f modelView, Matrix4f projection, double cx, double cy, double cz) {
        Minecraft minecraft = Minecraft.getInstance();
        sync(minecraft);
        if (!active() || level == null || !LodStore.open() || !available()) {
            return;
        }
        for (Runnable upload; (upload = UPLOADS.poll()) != null; ) {
            upload.run();
        }
        int radius = NimbusConfig.get().lodDistance;
        int rd = minecraft.options.getEffectiveRenderDistance();
        if (radius <= rd) {
            return;
        }
        Frustum frustum = new Frustum(modelView, projection);
        frustum.prepare(cx, cy, cz);
        int chunkX = (int) Math.floor(cx) >> 4;
        int chunkZ = (int) Math.floor(cz) >> 4;
        int tileMinX = Math.floorDiv(chunkX - radius, TILE_CHUNKS);
        int tileMaxX = Math.floorDiv(chunkX + radius, TILE_CHUNKS);
        int tileMinZ = Math.floorDiv(chunkZ - radius, TILE_CHUNKS);
        int tileMaxZ = Math.floorDiv(chunkZ + radius, TILE_CHUNKS);
        int frame = ++seenFrame;
        float inner = Math.max(0, rd - 1) * 16.0F;
        boolean drawing = false;
        drawnTiles = 0;
        for (int tz = tileMinZ; tz <= tileMaxZ; tz++) {
            for (int tx = tileMinX; tx <= tileMaxX; tx++) {
                int nearX = Math.max(tx * TILE_CHUNKS, Math.min(chunkX, tx * TILE_CHUNKS + TILE_CHUNKS - 1));
                int nearZ = Math.max(tz * TILE_CHUNKS, Math.min(chunkZ, tz * TILE_CHUNKS + TILE_CHUNKS - 1));
                int far = Math.max(Math.abs(nearX - chunkX), Math.abs(nearZ - chunkZ));
                if (far > radius) {
                    continue;
                }
                boolean inside = Math.abs(tx * TILE_CHUNKS - chunkX) < rd - 1 && Math.abs(tx * TILE_CHUNKS + TILE_CHUNKS - 1 - chunkX) < rd - 1
                        && Math.abs(tz * TILE_CHUNKS - chunkZ) < rd - 1 && Math.abs(tz * TILE_CHUNKS + TILE_CHUNKS - 1 - chunkZ) < rd - 1;
                if (inside) {
                    continue;
                }
                long key = key(tx, tz);
                Tile tile = TILES.get(key);
                if (tile == null) {
                    tile = new Tile(tx, tz);
                    TILES.put(key, tile);
                }
                tile.seen = frame;
                LodStore.Region region = LodStore.region(Math.floorDiv(tx * TILE_CHUNKS, LodStore.REGION_CHUNKS), Math.floorDiv(tz * TILE_CHUNKS, LodStore.REGION_CHUNKS));
                int detail = far > 32 ? 1 : 0;
                if (region != null && !tile.pending && building < MAX_BUILDS && (tile.builtVersion != region.version || tile.builtLevel != detail)) {
                    schedule(tile, region.version, detail);
                }
                if (tile.quads == 0) {
                    continue;
                }
                double x0 = tx * TILE_CHUNKS * 16.0;
                double z0 = tz * TILE_CHUNKS * 16.0;
                if (!frustum.isVisible(new net.minecraft.world.phys.AABB(x0, tile.minY - 16, z0, x0 + TILE_CHUNKS * 16, tile.maxY, z0 + TILE_CHUNKS * 16))) {
                    continue;
                }
                if (!drawing) {
                    begin(modelView, projection, inner, radius);
                    drawing = true;
                }
                drawnTiles++;
                GL20.glUniform3f(uOffset, (float) (x0 - cx), (float) (-cy), (float) (z0 - cz));
                GL43.glBindVertexBuffer(0, tile.buffer, 0, VERTEX);
                GL11.glDrawElements(GL11.GL_TRIANGLES, tile.quads * 6, GL11.GL_UNSIGNED_SHORT, 0L);
            }
        }
        if (drawing) {
            end();
        }
        if ((frame & 63) == 0) {
            ObjectIterator<Long2ObjectOpenHashMap.Entry<Tile>> it = TILES.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                Tile tile = it.next().getValue();
                if (frame - tile.seen > 600 && !tile.pending) {
                    if (tile.buffer != 0) {
                        GlStateManager._glDeleteBuffers(tile.buffer);
                    }
                    it.remove();
                }
            }
        }
    }

    private static int seenFrame;

    private static void begin(Matrix4f modelView, Matrix4f projection, float inner, int radius) {
        GlStateManager._glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL20.glUniformMatrix4fv(uMvp, false, projection.mul(modelView, MVP).get(stack.mallocFloat(16)));
        }
        GL20.glUniform1f(uInner, inner);
        float end = radius * 16.0F;
        GL20.glUniform1f(uFogStart, end * 0.8F);
        GL20.glUniform1f(uFogEnd, end);
        float[] fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(uFogColor, fog[0], fog[1], fog[2], 1.0F);
        Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
        GlStateManager._activeTexture(GL13.GL_TEXTURE2);
        GlStateManager._bindTexture(RenderSystem.getShaderTexture(2));
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        // pushed back so the real world wins where both are drawn
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(1.0F, 16.0F);
        GlStateManager._glBindVertexArray(vao);
    }

    private static void end() {
        GlStateManager._glBindVertexArray(0);
        BufferUploader.invalidate();
        GlStateManager._glUseProgram(0);
        ShaderInstanceAccessor.nimbus$setLastProgramId(-1);
        ShaderInstanceAccessor.nimbus$setLastAppliedShader(null);
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
    }

    private static void schedule(Tile tile, int regionVersion, int detail) {
        int step = 1 << detail;
        int cells = TILE_CELLS / step;
        // one cell of border on every side so walls know their neighbours
        int side = cells + 2;
        short[] height = new short[side * side];
        int[] color = new int[side * side];
        int originX = tile.x * TILE_CELLS;
        int originZ = tile.z * TILE_CELLS;
        for (int j = 0; j < side; j++) {
            for (int i = 0; i < side; i++) {
                int gx = originX + (i - 1) * step;
                int gz = originZ + (j - 1) * step;
                short best = LodStore.EMPTY;
                int bestColor = 0;
                for (int dz = 0; dz < step; dz++) {
                    for (int dx = 0; dx < step; dx++) {
                        long cell = cell(gx + dx, gz + dz);
                        short h = (short) (cell >> 32);
                        if (h != LodStore.EMPTY && (best == LodStore.EMPTY || h > best)) {
                            best = h;
                            bestColor = (int) cell;
                        }
                    }
                }
                height[j * side + i] = best;
                color[j * side + i] = bestColor;
            }
        }
        tile.pending = true;
        building++;
        int scale = 4 * step;
        CompletableFuture.supplyAsync(() -> mesh(height, color, cells, side, scale), Util.backgroundExecutor()).whenComplete((mesh, error) -> UPLOADS.add(() -> {
            building = Math.max(0, building - 1);
            tile.pending = false;
            if (error != null || mesh == null || TILES.get(key(tile.x, tile.z)) != tile) {
                if (mesh != null) {
                    MemoryUtil.memFree(mesh.data);
                }
                return;
            }
            if (tile.buffer == 0) {
                tile.buffer = GlStateManager._glGenBuffers();
            }
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, tile.buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.data, GL15.GL_STATIC_DRAW);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            MemoryUtil.memFree(mesh.data);
            tile.quads = mesh.quads;
            tile.minY = mesh.minY;
            tile.maxY = mesh.maxY;
            tile.builtVersion = regionVersion;
            tile.builtLevel = detail;
        }));
    }

    // height in the high half, colour in the low half; empty outside known regions
    private static long cell(int gx, int gz) {
        LodStore.Region region = LodStore.region(Math.floorDiv(gx, LodStore.SIDE), Math.floorDiv(gz, LodStore.SIDE));
        if (region == null) {
            return (long) LodStore.EMPTY << 32;
        }
        int index = Math.floorMod(gz, LodStore.SIDE) * LodStore.SIDE + Math.floorMod(gx, LodStore.SIDE);
        return (long) LodStore.height(region, index) << 32 | LodStore.color(region, index) & 0xFFFFFFFFL;
    }

    private record Mesh(ByteBuffer data, int quads, int minY, int maxY) {
    }

    private static Mesh mesh(short[] height, int[] color, int cells, int side, int scale) {
        ByteBuffer data = MemoryUtil.memAlloc(cells * cells * 5 * 4 * VERTEX);
        int quads = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        long p = MemoryUtil.memAddress(data);
        for (int j = 1; j <= cells; j++) {
            for (int i = 1; i <= cells; i++) {
                short h = height[j * side + i];
                if (h == LodStore.EMPTY) {
                    continue;
                }
                int argb = color[j * side + i];
                float x0 = (i - 1) * scale;
                float z0 = (j - 1) * scale;
                float x1 = x0 + scale;
                float z1 = z0 + scale;
                minY = Math.min(minY, h);
                maxY = Math.max(maxY, h);
                int top = abgr(argb, 1.0F);
                p = quad(p, x0, h, z0, x0, h, z1, x1, h, z1, x1, h, z0, top);
                quads++;
                // walls towards lower or unknown neighbours; unknown ones get a short skirt
                short north = height[(j - 1) * side + i];
                short south = height[(j + 1) * side + i];
                short west = height[j * side + i - 1];
                short east = height[j * side + i + 1];
                int ns = abgr(argb, 0.8F);
                int ew = abgr(argb, 0.6F);
                if (north < h) {
                    int bottom = north == LodStore.EMPTY ? h - 16 : north;
                    p = quad(p, x0, h, z0, x1, h, z0, x1, bottom, z0, x0, bottom, z0, ns);
                    quads++;
                    minY = Math.min(minY, bottom);
                }
                if (south < h) {
                    int bottom = south == LodStore.EMPTY ? h - 16 : south;
                    p = quad(p, x1, h, z1, x0, h, z1, x0, bottom, z1, x1, bottom, z1, ns);
                    quads++;
                    minY = Math.min(minY, bottom);
                }
                if (west < h) {
                    int bottom = west == LodStore.EMPTY ? h - 16 : west;
                    p = quad(p, x0, h, z1, x0, h, z0, x0, bottom, z0, x0, bottom, z1, ew);
                    quads++;
                    minY = Math.min(minY, bottom);
                }
                if (east < h) {
                    int bottom = east == LodStore.EMPTY ? h - 16 : east;
                    p = quad(p, x1, h, z0, x1, h, z1, x1, bottom, z1, x1, bottom, z0, ew);
                    quads++;
                    minY = Math.min(minY, bottom);
                }
            }
        }
        data.limit(quads * 4 * VERTEX);
        return new Mesh(data, Math.min(quads, MAX_QUADS), minY, maxY);
    }

    private static long quad(long p, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             int abgr) {
        p = vertex(p, ax, ay, az, abgr);
        p = vertex(p, bx, by, bz, abgr);
        p = vertex(p, cx, cy, cz, abgr);
        return vertex(p, dx, dy, dz, abgr);
    }

    private static long vertex(long p, float x, float y, float z, int abgr) {
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4L, y);
        MemoryUtil.memPutFloat(p + 8L, z);
        MemoryUtil.memPutInt(p + 12L, abgr);
        return p + VERTEX;
    }

    private static int abgr(int argb, float shade) {
        int r = (int) ((argb >> 16 & 255) * shade);
        int g = (int) ((argb >> 8 & 255) * shade);
        int b = (int) ((argb & 255) * shade);
        return 0xFF000000 | b << 16 | g << 8 | r;
    }
}
