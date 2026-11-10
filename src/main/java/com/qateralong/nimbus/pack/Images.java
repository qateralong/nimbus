package com.qateralong.nimbus.pack;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL44;

// Custom images (Iris): image.<name> = <sampler> <format> <internalFormat> <pixelType> <clear> <relative> <w> [h [d]] | <sx> <sy>,
// readable as <sampler> and writable as image <name>; colortex buffers are also images colorimgN.
public final class Images {
    public static final int FIRST_UNIT = 32;

    public static final class Image {
        final String name;
        final String sampler;
        final int target;
        final int[] format;
        final boolean clear;
        final boolean relative;
        final float[] size;
        int texture;
        int width;
        int height;
        int depth;
        int unit;
        int imageUnit;

        Image(String name, String sampler, int target, int[] format, boolean clear, boolean relative, float[] size) {
            this.name = name;
            this.sampler = sampler;
            this.target = target;
            this.format = format;
            this.clear = clear;
            this.relative = relative;
            this.size = size;
        }
    }

    public final List<Image> images = new ArrayList<>();
    // bufferObject.<binding> = <bytes>: shader storage buffers, zeroed once, bound for every program
    private final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap bufferSizes = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
    private final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap buffers = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

    public static Images load(PackConfig config) {
        Images result = new Images();
        for (var e : config.properties.entrySet()) {
            if (e.getKey().startsWith("bufferObject.")) {
                try {
                    result.bufferSizes.put(Integer.parseInt(e.getKey().substring(13).trim()), Long.parseLong(e.getValue().trim()));
                } catch (NumberFormatException ex) {
                    PackProgram.warn(e.getKey(), ex);
                }
                continue;
            }
            if (!e.getKey().startsWith("image.")) {
                continue;
            }
            String[] w = e.getValue().trim().split("\\s+");
            if (w.length < 7) {
                continue;
            }
            int[] format = Targets.format(w[2]);
            if (format == null) {
                PackProgram.warn("image " + e.getKey(), new IllegalArgumentException("format " + w[2]));
                continue;
            }
            boolean relative = w[5].equalsIgnoreCase("true");
            int dims = relative ? 2 : w.length - 6;
            float[] size = new float[Math.max(dims, 1)];
            for (int i = 0; i < size.length && 6 + i < w.length; i++) {
                size[i] = Float.parseFloat(w[6 + i]);
            }
            int target = dims >= 3 ? GL12.GL_TEXTURE_3D : dims == 1 ? GL11.GL_TEXTURE_1D : GL11.GL_TEXTURE_2D;
            result.images.add(new Image(e.getKey().substring(6), w[0], target, format, w[4].equalsIgnoreCase("true"), relative, size));
        }
        for (int i = 0; i < result.images.size(); i++) {
            result.images.get(i).unit = FIRST_UNIT + i;
            result.images.get(i).imageUnit = i;
        }
        return result;
    }

    public void resize(int screenWidth, int screenHeight) {
        for (var e : this.bufferSizes.int2LongEntrySet()) {
            if (this.buffers.containsKey(e.getIntKey())) {
                continue;
            }
            int id = org.lwjgl.opengl.GL15.glGenBuffers();
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER, id);
            org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER, e.getLongValue(), org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW);
            org.lwjgl.opengl.GL43.glClearBufferData(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R8UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER, 0);
            this.buffers.put(e.getIntKey(), id);
        }
        for (Image image : this.images) {
            int w = image.relative ? Math.max(1, Math.round(screenWidth * image.size[0])) : (int) image.size[0];
            int h = image.relative ? Math.max(1, Math.round(screenHeight * image.size[1])) : image.size.length > 1 ? (int) image.size[1] : 1;
            int d = image.size.length > 2 ? (int) image.size[2] : 1;
            if (image.texture != 0 && w == image.width && h == image.height && d == image.depth) {
                continue;
            }
            if (image.texture != 0) {
                GlStateManager._deleteTexture(image.texture);
            }
            image.width = w;
            image.height = h;
            image.depth = d;
            image.texture = GlStateManager._genTexture();
            GL11.glBindTexture(image.target, image.texture);
            if (image.target == GL12.GL_TEXTURE_3D) {
                GL12.glTexImage3D(image.target, 0, image.format[0], w, h, d, 0, image.format[1], image.format[2], (ByteBuffer) null);
            } else if (image.target == GL11.GL_TEXTURE_1D) {
                GL11.glTexImage1D(image.target, 0, image.format[0], w, 0, image.format[1], image.format[2], (ByteBuffer) null);
            } else {
                GL11.glTexImage2D(image.target, 0, image.format[0], w, h, 0, image.format[1], image.format[2], (ByteBuffer) null);
            }
            boolean integer = image.format[1] == GL30.GL_RED_INTEGER || image.format[1] == GL30.GL_RG_INTEGER || image.format[1] == GL30.GL_RGBA_INTEGER;
            GL11.glTexParameteri(image.target, GL11.GL_TEXTURE_MIN_FILTER, integer ? GL11.GL_NEAREST : GL11.GL_LINEAR);
            GL11.glTexParameteri(image.target, GL11.GL_TEXTURE_MAG_FILTER, integer ? GL11.GL_NEAREST : GL11.GL_LINEAR);
            GL11.glTexParameteri(image.target, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(image.target, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(image.target, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(image.target, 0);
            clear(image);
        }
    }

    private static void clear(Image image) {
        boolean integer = image.format[1] == GL30.GL_RED_INTEGER || image.format[1] == GL30.GL_RG_INTEGER || image.format[1] == GL30.GL_RGBA_INTEGER;
        if (integer) {
            GL44.glClearTexImage(image.texture, 0, image.format[1], GL11.GL_UNSIGNED_INT, new int[] {0, 0, 0, 0});
        } else {
            GL44.glClearTexImage(image.texture, 0, GL11.GL_RGBA, GL11.GL_FLOAT, new float[] {0, 0, 0, 0});
        }
    }

    public void clearFrame() {
        for (Image image : this.images) {
            if (image.clear && image.texture != 0) {
                clear(image);
            }
        }
    }

    // sampler and image uniforms of a program
    public void setup(PackProgram p) {
        for (Image image : this.images) {
            int s = p.location(image.sampler);
            if (s >= 0) {
                GL20.glUniform1i(s, image.unit);
            }
            int i = p.location(image.name);
            if (i >= 0) {
                GL20.glUniform1i(i, image.imageUnit);
            }
        }
    }

    // our engine and culling passes share the binding points: rebound before each pack program
    public void bindBuffers() {
        for (var e : this.buffers.int2IntEntrySet()) {
            org.lwjgl.opengl.GL30.glBindBufferBase(org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER, e.getIntKey(), e.getIntValue());
        }
        for (Image image : this.images) {
            if (image.texture != 0) {
                GL42.glBindImageTexture(image.imageUnit, image.texture, 0, image.target == GL12.GL_TEXTURE_3D, 0, GL42.GL_READ_WRITE, image.format[0]);
            }
        }
    }

    private static final boolean DEBUG = Boolean.getBoolean("nimbus.imageDebug");
    private int debugFrames;

    // -Dnimbus.imageDebug: non-zero bytes per image now and then
    public void debug() {
        if (!DEBUG || ++this.debugFrames % 60 != 0) {
            return;
        }
        StringBuilder s = new StringBuilder();
        for (Image image : this.images) {
            if (image.texture == 0) {
                continue;
            }
            int bytes = image.width * image.height * image.depth * 8;
            ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(bytes);
            GL11.glBindTexture(image.target, image.texture);
            boolean integer = image.format[1] == GL30.GL_RED_INTEGER || image.format[1] == GL30.GL_RG_INTEGER || image.format[1] == GL30.GL_RGBA_INTEGER;
            GL11.glGetTexImage(image.target, 0, integer ? GL30.GL_RGBA_INTEGER : GL11.GL_RGBA, integer ? GL11.GL_UNSIGNED_SHORT : org.lwjgl.opengl.GL30.GL_HALF_FLOAT, buffer);
            GL11.glBindTexture(image.target, 0);
            int nonZero = 0;
            for (int i = 0; i < bytes; i += 2) {
                if (buffer.getShort(i) != 0) {
                    nonZero++;
                }
            }
            org.lwjgl.system.MemoryUtil.memFree(buffer);
            s.append(image.name).append(' ').append(image.width).append('x').append(image.height).append('x').append(image.depth).append(": ").append(nonZero).append("  ");
        }
        com.qateralong.nimbus.Nimbus.LOGGER.info("Nimbus images: {}", s);
    }

    public void bind(Targets targets) {
        this.bindBuffers();
        for (Image image : this.images) {
            if (image.texture == 0) {
                continue;
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + image.unit);
            GL11.glBindTexture(image.target, image.texture);
            GL42.glBindImageTexture(image.imageUnit, image.texture, 0, image.target == GL12.GL_TEXTURE_3D, 0, GL42.GL_READ_WRITE, image.format[0]);
        }
        GL13.glActiveTexture(GlStateManager._getActiveTexture());
    }

    // colorimgN: the buffer a pass would read, as an image (unit = image unit FIRST_UNIT + 16 + N)
    public static void bindColorImages(PackProgram p, Targets targets) {
        for (int i = 0; i < targets.count(); i++) {
            if (!targets.buffers[i].used) {
                continue;
            }
            int l = p.location("colorimg" + i);
            if (l < 0) {
                continue;
            }
            int unit = 8 + i;
            GL20.glUniform1i(l, unit);
            GL42.glBindImageTexture(unit, targets.read(i), 0, false, 0, GL42.GL_READ_WRITE, targets.buffers[i].format[0]);
        }
    }

    public void free() {
        for (int id : this.buffers.values()) {
            org.lwjgl.opengl.GL15.glDeleteBuffers(id);
        }
        this.buffers.clear();
        for (Image image : this.images) {
            if (image.texture != 0) {
                GlStateManager._deleteTexture(image.texture);
                image.texture = 0;
            }
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
