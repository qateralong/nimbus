package com.qateralong.nimbus.pack;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

// texture.<stage>.<sampler>=<file in the pack | namespace:path | raw.bin TYPE FORMAT W [H [D]] PIXELFORMAT PIXELTYPE>
// replaces what the sampler reads in that stage's programs (stage: shadow, gbuffers, deferred, composite).
public final class CustomTextures {
    public record Texture(int id, int target) {
    }

    // stage -> sampler -> texture
    private final Map<String, Map<String, Object>> stages = new HashMap<>();

    public static CustomTextures load(ShaderPack pack, PackConfig config) {
        CustomTextures c = new CustomTextures();
        if (Boolean.getBoolean("nimbus.noCustomTextures")) {
            return c;
        }
        for (var e : config.properties.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("texture.")) {
                continue;
            }
            String[] parts = key.split("\\.", 3);
            if (parts.length == 2) {
                // texture.noise: the noise texture of every stage
                parts = new String[] {"texture", "*", parts[1].equals("noise") ? "noisetex" : parts[1]};
            }
            if (parts.length < 3) {
                continue;
            }
            int dot = parts[2].indexOf('.');
            if (dot > 0) {
                parts[2] = parts[2].substring(0, dot);
            }
            try {
                Object texture = c.create(pack, e.getValue().trim());
                if (texture != null) {
                    c.stages.computeIfAbsent(parts[1], k -> new HashMap<>()).put(parts[2], texture);
                }
            } catch (Exception ex) {
                PackProgram.warn("texture " + key, ex);
            }
        }
        return c;
    }

    private Object create(ShaderPack pack, String value) throws IOException {
        String[] words = value.split("\\s+");
        String path = words[0];
        if (path.contains(":") && !path.startsWith("/")) {
            // game texture, resolved when bound (the texture manager owns it)
            return ResourceLocation.tryParse(path);
        }
        String file = path.startsWith("/") ? ShaderPack.normalize(path) : ShaderPack.normalize(path);
        byte[] data = pack.bytes(file);
        if (data == null) {
            throw new IOException("missing " + file);
        }
        if (words.length > 1) {
            return raw(data, words);
        }
        boolean blur = true;
        boolean clamp = true;
        String meta = pack.read(file + ".mcmeta");
        if (meta != null) {
            blur = meta.contains("\"blur\"") && meta.replaceAll("\\s", "").contains("\"blur\":true");
            clamp = !meta.replaceAll("\\s", "").contains("\"clamp\":false");
        }
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        buffer.put(data).flip();
        try (NativeImage image = NativeImage.read(buffer)) {
            int id = GlStateManager._genTexture();
            GlStateManager._bindTexture(id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, blur ? GL11.GL_LINEAR : GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, blur ? GL11.GL_LINEAR : GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT);
            unpackDefaults();
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    pixels.putInt(image.getPixelRGBA(x, y));
                }
            }
            pixels.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, image.getWidth(), image.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            MemoryUtil.memFree(pixels);
            return new Texture(id, GL11.GL_TEXTURE_2D);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    // raw data: TEXTURE_1D|2D|3D internalFormat width [height [depth]] pixelFormat pixelType
    private static Texture raw(byte[] data, String[] w) throws IOException {
        int target = switch (w[1].toUpperCase(Locale.ROOT)) {
            case "TEXTURE_1D" -> GL11.GL_TEXTURE_1D;
            case "TEXTURE_3D" -> GL12.GL_TEXTURE_3D;
            default -> GL11.GL_TEXTURE_2D;
        };
        int dims = target == GL12.GL_TEXTURE_3D ? 3 : target == GL11.GL_TEXTURE_1D ? 1 : 2;
        int[] internal = Targets.format(w[2]);
        if (internal == null || w.length < 3 + dims + 2) {
            throw new IOException("bad raw texture spec");
        }
        int width = Integer.parseInt(w[3]);
        int height = dims > 1 ? Integer.parseInt(w[4]) : 1;
        int depth = dims > 2 ? Integer.parseInt(w[5]) : 1;
        int pixelFormat = glEnum(w[3 + dims]);
        int pixelType = glEnum(w[4 + dims]);
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        buffer.put(data).flip();
        int id = GlStateManager._genTexture();
        GL11.glBindTexture(target, id);
        unpackDefaults();
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        if (target == GL12.GL_TEXTURE_3D) {
            GL12.glTexImage3D(target, 0, internal[0], width, height, depth, 0, pixelFormat, pixelType, buffer);
        } else if (target == GL11.GL_TEXTURE_1D) {
            GL11.glTexImage1D(target, 0, internal[0], width, 0, pixelFormat, pixelType, buffer);
        } else {
            GL11.glTexImage2D(target, 0, internal[0], width, height, 0, pixelFormat, pixelType, buffer);
        }
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(target, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
        GL11.glBindTexture(target, 0);
        MemoryUtil.memFree(buffer);
        return new Texture(id, target);
    }

    // Minecraft leaves row length and skips set after its own uploads
    static void unpackDefaults() {
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_IMAGES, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, GL11.GL_FALSE);
        GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, GL11.GL_FALSE);
    }

    private static int glEnum(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "RED" -> GL11.GL_RED;
            case "RG" -> GL30.GL_RG;
            case "RGB" -> GL11.GL_RGB;
            case "BGR" -> GL12.GL_BGR;
            case "RGBA" -> GL11.GL_RGBA;
            case "BGRA" -> GL12.GL_BGRA;
            case "RED_INTEGER" -> GL30.GL_RED_INTEGER;
            case "RG_INTEGER" -> GL30.GL_RG_INTEGER;
            case "RGBA_INTEGER" -> GL30.GL_RGBA_INTEGER;
            case "BYTE" -> GL11.GL_BYTE;
            case "UNSIGNED_SHORT" -> GL11.GL_UNSIGNED_SHORT;
            case "SHORT" -> GL11.GL_SHORT;
            case "UNSIGNED_INT" -> GL11.GL_UNSIGNED_INT;
            case "INT" -> GL11.GL_INT;
            case "FLOAT" -> GL11.GL_FLOAT;
            case "HALF_FLOAT" -> GL30.GL_HALF_FLOAT;
            default -> GL11.GL_UNSIGNED_BYTE;
        };
    }

    public static String stage(String program) {
        if (program.startsWith("shadow")) {
            return "shadow";
        }
        if (program.startsWith("gbuffers")) {
            return "gbuffers";
        }
        if (program.startsWith("deferred")) {
            return "deferred";
        }
        return "composite";
    }

    // bind the stage's custom textures over the standard units
    public boolean bind(String program, java.util.function.ToIntFunction<String> unitOf) {
        Map<String, Object> staged = this.stages.get(stage(program));
        Map<String, Object> every = this.stages.get("*");
        if (staged == null && every == null) {
            return false;
        }
        Map<String, Object> map = new HashMap<>();
        if (every != null) {
            map.putAll(every);
        }
        if (staged != null) {
            map.putAll(staged);
        }
        int previous = GlStateManager._getActiveTexture();
        for (var e : map.entrySet()) {
            int unit = unitOf.applyAsInt(e.getKey());
            if (unit < 0) {
                continue;
            }
            int id;
            int target;
            if (e.getValue() instanceof Texture t) {
                id = t.id();
                target = t.target();
            } else {
                id = Minecraft.getInstance().getTextureManager().getTexture((ResourceLocation) e.getValue()).getId();
                target = GL11.GL_TEXTURE_2D;
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(target, id);
            if (unit < 12 && target == GL11.GL_TEXTURE_2D) {
                // keep GlStateManager's idea of the unit in step
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                GlStateManager._bindTexture(id);
            }
        }
        GL13.glActiveTexture(previous);
        GlStateManager._activeTexture(previous);
        return true;
    }

    public void free() {
        for (Map<String, Object> map : this.stages.values()) {
            for (Object o : map.values()) {
                if (o instanceof Texture t) {
                    GlStateManager._deleteTexture(t.id());
                }
            }
        }
        this.stages.clear();
    }
}
