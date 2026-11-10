package com.qateralong.nimbus.pack;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

// Render targets of a pack: colortexN pairs (written one, read the other; a pass that writes a buffer flips it), depthtex0
// (the world's depth), depthtex1 (before translucents), depthtex2 (before the hand), noisetex, stand-ins for shadow maps.
public final class Targets {
    public static final int MAX = 20;
    private static final Pattern CONST = Pattern.compile("const\\s+(int|bool|vec4|float)\\s+(\\w+)\\s*=\\s*([^;]+);");
    private static final String[] LEGACY = {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};
    private static final Map<String, int[]> FORMATS = new HashMap<>();

    static {
        format("R8", GL30.GL_R8, GL30.GL_RED, GL11.GL_UNSIGNED_BYTE);
        format("RG8", GL30.GL_RG8, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE);
        format("RGB8", GL11.GL_RGB8, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE);
        format("RGBA8", GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        format("R8_SNORM", 0x8F94, GL30.GL_RED, GL11.GL_BYTE);
        format("RG8_SNORM", 0x8F95, GL30.GL_RG, GL11.GL_BYTE);
        format("RGB8_SNORM", 0x8F96, GL11.GL_RGB, GL11.GL_BYTE);
        format("RGBA8_SNORM", 0x8F97, GL11.GL_RGBA, GL11.GL_BYTE);
        format("R16", 0x822A, GL30.GL_RED, GL11.GL_UNSIGNED_SHORT);
        format("RG16", 0x822C, GL30.GL_RG, GL11.GL_UNSIGNED_SHORT);
        format("RGB16", GL11.GL_RGB16, GL11.GL_RGB, GL11.GL_UNSIGNED_SHORT);
        format("RGBA16", GL11.GL_RGBA16, GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT);
        format("R16_SNORM", 0x8F98, GL30.GL_RED, GL11.GL_SHORT);
        format("RG16_SNORM", 0x8F99, GL30.GL_RG, GL11.GL_SHORT);
        format("RGB16_SNORM", 0x8F9A, GL11.GL_RGB, GL11.GL_SHORT);
        format("RGBA16_SNORM", 0x8F9B, GL11.GL_RGBA, GL11.GL_SHORT);
        format("R16F", GL30.GL_R16F, GL30.GL_RED, GL11.GL_FLOAT);
        format("RG16F", GL30.GL_RG16F, GL30.GL_RG, GL11.GL_FLOAT);
        format("RGB16F", GL30.GL_RGB16F, GL11.GL_RGB, GL11.GL_FLOAT);
        format("RGBA16F", GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT);
        format("R32F", GL30.GL_R32F, GL30.GL_RED, GL11.GL_FLOAT);
        format("RG32F", GL30.GL_RG32F, GL30.GL_RG, GL11.GL_FLOAT);
        format("RGB32F", GL30.GL_RGB32F, GL11.GL_RGB, GL11.GL_FLOAT);
        format("RGBA32F", GL30.GL_RGBA32F, GL11.GL_RGBA, GL11.GL_FLOAT);
        format("R11F_G11F_B10F", GL30.GL_R11F_G11F_B10F, GL11.GL_RGB, GL11.GL_FLOAT);
        format("RGB10_A2", GL11.GL_RGB10_A2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        format("RGB5_A1", GL11.GL_RGB5_A1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        format("R3_G3_B2", GL11.GL_R3_G3_B2, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE);
        format("RGBA2", GL11.GL_RGBA2, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        format("RGB9_E5", GL30.GL_RGB9_E5, GL11.GL_RGB, GL11.GL_FLOAT);
        format("R8UI", GL30.GL_R8UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE);
        format("R16UI", GL30.GL_R16UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_SHORT);
        format("R32UI", GL30.GL_R32UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_INT);
        format("RG32UI", GL30.GL_RG32UI, GL30.GL_RG_INTEGER, GL11.GL_UNSIGNED_INT);
        format("RGBA32UI", GL30.GL_RGBA32UI, GL30.GL_RGBA_INTEGER, GL11.GL_UNSIGNED_INT);
        format("R32I", GL30.GL_R32I, GL30.GL_RED_INTEGER, GL11.GL_INT);
    }

    private static void format(String name, int internal, int format, int type) {
        FORMATS.put(name, new int[] {internal, format, type});
    }

    public static int[] format(String name) {
        return FORMATS.get(name.toUpperCase(Locale.ROOT));
    }

    public static final class Buffer {
        public int[] format = FORMATS.get("RGBA8");
        public boolean clear = true;
        public float[] clearColor;
        public boolean mipmap;
        public final int[] textures = new int[2];
        public int current;
        public boolean used;
        // size.buffer: fractions of the screen (<= 1) or pixels
        public float sizeX = 1.0F;
        public float sizeY = 1.0F;
        public int width;
        public int height;
    }

    public final Buffer[] buffers = new Buffer[MAX];
    public int width;
    public int height;
    public int depth0;
    public int depth1;
    public int depth2;
    public int noise;
    public int shadowDepth;
    public int shadowColor;
    public int framebuffer;
    private int count;

    public Targets() {
        for (int i = 0; i < MAX; i++) {
            this.buffers[i] = new Buffer();
        }
    }

    public int count() {
        return this.count;
    }

    // const declarations of one (preprocessed) program source
    public void scan(String source) {
        Matcher m = CONST.matcher(source);
        while (m.find()) {
            String name = m.group(2);
            String value = m.group(3).trim();
            int index = -1;
            String rest = null;
            if (name.startsWith("colortex")) {
                int end = 8;
                while (end < name.length() && Character.isDigit(name.charAt(end))) {
                    end++;
                }
                if (end > 8) {
                    index = Integer.parseInt(name.substring(8, end));
                    rest = name.substring(end);
                }
            } else {
                for (int i = 0; i < LEGACY.length; i++) {
                    if (name.startsWith(LEGACY[i]) && name.length() > LEGACY[i].length() && Character.isUpperCase(name.charAt(LEGACY[i].length()))) {
                        index = i;
                        rest = name.substring(LEGACY[i].length());
                    }
                }
            }
            if (index < 0 || index >= MAX || rest == null) {
                continue;
            }
            Buffer b = this.buffers[index];
            switch (rest) {
                case "Format" -> {
                    int[] f = FORMATS.get(value.toUpperCase(Locale.ROOT));
                    if (f != null) {
                        b.format = f;
                    }
                }
                case "Clear" -> b.clear = !value.equals("false");
                case "ClearColor" -> b.clearColor = vec4(value);
                case "MipmapEnabled" -> b.mipmap = value.equals("true");
                default -> {
                }
            }
        }
    }

    // a program's sources name these buffers
    public void use(String source) {
        for (int i = 0; i < MAX; i++) {
            if (source.contains("colortex" + i) || i < LEGACY.length && source.contains(LEGACY[i])) {
                this.buffers[i].used = true;
            }
        }
        this.buffers[0].used = true;
    }

    private static float[] vec4(String value) {
        // numbers inside vec4(...), not the 4 of the constructor's name
        int open = value.indexOf('(');
        Matcher m = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?").matcher(open >= 0 ? value.substring(open + 1) : value);
        float[] v = new float[4];
        int n = 0;
        while (m.find() && n < 4) {
            v[n++] = Float.parseFloat(m.group());
        }
        if (n == 1) {
            v[1] = v[2] = v[3] = v[0];
        }
        return v;
    }

    public void resize(int width, int height) {
        if (width == this.width && height == this.height && this.framebuffer != 0) {
            return;
        }
        this.free();
        this.width = width;
        this.height = height;
        this.count = 0;
        for (int i = 0; i < MAX; i++) {
            Buffer b = this.buffers[i];
            if (!b.used) {
                continue;
            }
            this.count = i + 1;
            b.width = b.sizeX <= 1.0F ? Math.max(1, Math.round(width * b.sizeX)) : (int) b.sizeX;
            b.height = b.sizeY <= 1.0F ? Math.max(1, Math.round(height * b.sizeY)) : (int) b.sizeY;
            for (int k = 0; k < 2; k++) {
                b.textures[k] = texture(b.format, b.width, b.height, b.mipmap);
            }
            b.current = 0;
        }
        this.depth0 = depthTexture(width, height);
        this.depth1 = depthTexture(width, height);
        this.depth2 = depthTexture(width, height);
        this.framebuffer = GlStateManager.glGenFramebuffers();
        if (this.noise == 0) {
            this.noise = noise(256);
            this.shadowDepth = depthTexture(1, 1);
            clearDepth(this.shadowDepth);
            this.shadowColor = texture(FORMATS.get("RGBA8"), 1, 1, false);
        }
    }

    private static int texture(int[] format, int width, int height, boolean mipmap) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format[0], width, height, 0, format[1], format[2], (ByteBuffer) null);
        boolean integer = format[1] == GL30.GL_RED_INTEGER || format[1] == GL30.GL_RG_INTEGER || format[1] == GL30.GL_RGBA_INTEGER;
        int filter = integer ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mipmap && !integer ? GL11.GL_LINEAR_MIPMAP_LINEAR : filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (mipmap) {
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
        return id;
    }

    private static int depthTexture(int width, int height) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        return id;
    }

    private static void clearDepth(int texture) {
        org.lwjgl.opengl.GL44.glClearTexImage(texture, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, new float[] {1.0F});
    }

    private static int noise(int size) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        ByteBuffer data = MemoryUtil.memAlloc(size * size * 3);
        Random random = new Random(1);
        for (int i = 0; i < size * size * 3; i++) {
            data.put((byte) random.nextInt(256));
        }
        data.flip();
        CustomTextures.unpackDefaults();
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, size, size, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, data);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        MemoryUtil.memFree(data);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        return id;
    }

    // the texture a sampler reads and the one a pass writes
    public int read(int buffer) {
        Buffer b = this.buffers[buffer];
        return b.textures[b.current];
    }

    public int write(int buffer, boolean flip) {
        Buffer b = this.buffers[buffer];
        return b.textures[flip ? 1 - b.current : b.current];
    }

    public void flip(int buffer) {
        this.buffers[buffer].current ^= 1;
    }

    // attach buffers as draw targets (null = depth only)
    public void bind(int[] outputs, boolean flip, boolean depth) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        int max = 8;
        for (int i = 0; i < max; i++) {
            int texture = outputs != null && i < outputs.length && outputs[i] < this.count && this.buffers[outputs[i]].used ? this.write(outputs[i], flip) : 0;
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, GL11.GL_TEXTURE_2D, texture, 0);
        }
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth ? this.depth0 : 0, 0);
        int n = outputs == null ? 0 : Math.min(outputs.length, max);
        int[] draw = new int[Math.max(n, 1)];
        for (int i = 0; i < n; i++) {
            draw[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
        }
        if (n == 0) {
            draw[0] = GL11.GL_NONE;
        }
        GL20.glDrawBuffers(draw);
    }

    public void clear(float[] fog) {
        for (int i = 0; i < this.count; i++) {
            Buffer b = this.buffers[i];
            if (!b.used || !b.clear) {
                continue;
            }
            float[] c = b.clearColor != null ? b.clearColor : i == 0 ? new float[] {fog[0], fog[1], fog[2], 1.0F} : i == 1 ? new float[] {1, 1, 1, 1}
                    : new float[] {0, 0, 0, 0};
            // only the copy the world is drawn into: full-screen passes overwrite the other one
            int k = b.current;
            if (b.format[1] == GL30.GL_RED_INTEGER || b.format[1] == GL30.GL_RG_INTEGER || b.format[1] == GL30.GL_RGBA_INTEGER) {
                org.lwjgl.opengl.GL44.glClearTexImage(b.textures[k], 0, b.format[1], GL11.GL_UNSIGNED_INT, new int[] {0, 0, 0, 0});
            } else {
                org.lwjgl.opengl.GL44.glClearTexImage(b.textures[k], 0, GL11.GL_RGBA, GL11.GL_FLOAT, c);
            }
        }
    }

    public void free() {
        for (Buffer b : this.buffers) {
            for (int k = 0; k < 2; k++) {
                if (b.textures[k] != 0) {
                    GlStateManager._deleteTexture(b.textures[k]);
                    b.textures[k] = 0;
                }
            }
        }
        for (int id : new int[] {this.depth0, this.depth1, this.depth2}) {
            if (id != 0) {
                GlStateManager._deleteTexture(id);
            }
        }
        this.depth0 = this.depth1 = this.depth2 = 0;
        if (this.framebuffer != 0) {
            GlStateManager._glDeleteFramebuffers(this.framebuffer);
            this.framebuffer = 0;
        }
        this.width = this.height = 0;
    }
}
