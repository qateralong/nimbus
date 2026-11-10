package com.qateralong.nimbus.pack;

import com.qateralong.nimbus.Nimbus;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL20;

// One linked pack program: its draw buffers, uniform locations and fixed texture units.
public final class PackProgram {
    private static final Pattern DRAWBUFFERS = Pattern.compile("/\\*\\s*DRAWBUFFERS\\s*:\\s*([0-9A-Fa-f]+)\\s*\\*/");
    private static final Pattern RENDERTARGETS = Pattern.compile("/\\*\\s*RENDERTARGETS\\s*:\\s*([0-9,\\s]+)\\*/");

    // texture units
    public static final int ATLAS = 0;
    public static final int LIGHTMAP = 1;
    public static final int NORMALS = 2;
    public static final int SPECULAR = 3;
    public static final int SHADOWTEX0 = 4;
    public static final int SHADOWTEX1 = 5;
    public static final int SHADOWCOLOR0 = 6;
    public static final int SHADOWCOLOR1 = 7;
    public static final int DEPTHTEX0 = 8;
    public static final int DEPTHTEX1 = 9;
    public static final int COLORTEX = 10;
    public static final int NOISE = 30;
    public static final int DEPTHTEX2 = 31;

    public final String name;
    public final int id;
    public final int[] outputs;
    // texture units read through sampler2DShadow (need depth comparison on)
    public final java.util.Set<Integer> compareUnits = new java.util.HashSet<>();
    private final Object2IntOpenHashMap<String> locations = new Object2IntOpenHashMap<>();
    public int frame = -1;
    // gbuffers framebuffer for the current flip state of the buffers
    public int fbo;
    public int fboStamp = -1;
    public boolean idsSet;
    public int tagVersion = -1;
    // compute programs: fixed group count, or groups covering the screen scaled by render
    public int[] workGroups;
    public float[] workGroupsRender;
    public int[] localSize = {1, 1, 1};
    public static java.util.function.Consumer<PackProgram> extraSetup;

    private PackProgram(String name, int id, int[] outputs) {
        this.name = name;
        this.id = id;
        this.outputs = outputs;
        this.locations.defaultReturnValue(-2);
    }

    private Map<String, Integer> types;

    // GL type of an active uniform (GL_FLOAT, GL_INT, ...), 0 when absent
    public int type(String uniform) {
        if (this.types == null) {
            this.types = new HashMap<>();
            int count = GL20.glGetProgrami(this.id, GL20.GL_ACTIVE_UNIFORMS);
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                java.nio.IntBuffer size = stack.mallocInt(1);
                java.nio.IntBuffer type = stack.mallocInt(1);
                for (int i = 0; i < count; i++) {
                    String name = GL20.glGetActiveUniform(this.id, i, size, type);
                    int bracket = name.indexOf('[');
                    this.types.put(bracket > 0 ? name.substring(0, bracket) : name, type.get(0));
                }
            }
        }
        return this.types.getOrDefault(uniform, 0);
    }

    public int location(String uniform) {
        int l = this.locations.getInt(uniform);
        if (l == -2) {
            l = GL20.glGetUniformLocation(this.id, uniform);
            this.locations.put(uniform, l);
        }
        return l;
    }

    // inputs: vanilla vertex format element names in attribute order, or null for a full-screen pass
    public static PackProgram build(ShaderPack pack, PackConfig config, String dir, String name, String defines, List<String> inputs, boolean gbuffers)
            throws IOException {
        String base = (dir.isEmpty() ? "" : dir + "/") + name;
        String vsh = pack.expand(base + ".vsh");
        String fsh = pack.expand(base + ".fsh");
        if (vsh == null || fsh == null) {
            return null;
        }
        String gsh = pack.expand(base + ".gsh");
        Glsl.Result vertex = Glsl.transform(vsh, Glsl.Stage.VERTEX, defines, inputs);
        Glsl.Result fragment = Glsl.transform(fsh, Glsl.Stage.FRAGMENT, defines, null, gbuffers, gbuffers && gsh == null && inputs != null);
        Glsl.Result geometry = gsh == null ? null : Glsl.transform(gsh, Glsl.Stage.GEOMETRY, defines, null);
        int program = GL20.glCreateProgram();
        List<Integer> shaders = new ArrayList<>();
        try {
            shaders.add(compile(program, GL20.GL_VERTEX_SHADER, vertex.source(), base + (inputs == Glsl.ENGINE ? ".engine" : "") + ".vsh"));
            if (geometry != null) {
                shaders.add(compile(program, 0x8DD9, geometry.source(), base + ".gsh"));
            }
            shaders.add(compile(program, GL20.GL_FRAGMENT_SHADER, fragment.source(), base + ".fsh"));
        } catch (IOException e) {
            GL20.glDeleteProgram(program);
            for (int s : shaders) {
                GL20.glDeleteShader(s);
            }
            throw e;
        }
        if (inputs == Glsl.ENGINE) {
            // nimbus_Origin has an explicit location
        } else if (inputs != null) {
            for (int i = 0; i < inputs.size(); i++) {
                GL20.glBindAttribLocation(program, i, "nimbus_in_" + inputs.get(i));
            }
        } else {
            GL20.glBindAttribLocation(program, 0, "nimbus_Vertex");
            GL20.glBindAttribLocation(program, 1, "nimbus_MultiTexCoord0");
        }
        GL20.glLinkProgram(program);
        for (int s : shaders) {
            GL20.glDetachShader(program, s);
            GL20.glDeleteShader(s);
        }
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IOException(base + " link: " + log);
        }
        int[] outputs = outputs(Preprocessor.run(fsh, new HashMap<>(config.macros)), fragment.outputs(), gbuffers);
        PackProgram result = new PackProgram(name, program, outputs);
        Matcher shadow = Pattern.compile("sampler2DShadow\\s+(\\w+)").matcher(vertex.source() + fragment.source());
        while (shadow.find()) {
            switch (shadow.group(1)) {
                case "shadow", "watershadow", "shadowtex0" -> result.compareUnits.add(SHADOWTEX0);
                case "shadowtex1" -> result.compareUnits.add(SHADOWTEX1);
                case "depthtex0" -> result.compareUnits.add(DEPTHTEX0);
                case "depthtex1" -> result.compareUnits.add(DEPTHTEX1);
                default -> {
                }
            }
        }
        result.bindSamplers(gbuffers);
        return result;
    }

    public static PackProgram buildCompute(ShaderPack pack, PackConfig config, String file, String name, String defines) throws IOException {
        String source = pack.expand(file);
        if (source == null) {
            return null;
        }
        Glsl.Result result = Glsl.transform(source, Glsl.Stage.COMPUTE, defines, null);
        int program = GL20.glCreateProgram();
        int shader = compile(program, 0x91B9, result.source(), file);
        GL20.glLinkProgram(program);
        GL20.glDetachShader(program, shader);
        GL20.glDeleteShader(shader);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IOException(file + " link: " + log);
        }
        PackProgram p = new PackProgram(name, program, new int[0]);
        HashMap<String, String> seen = new HashMap<>(config.macros);
        String active = Preprocessor.run(source, seen);
        if (Boolean.getBoolean("nimbus.imageDebug")) {
            StringBuilder d = new StringBuilder();
            for (String k : new String[] {"COLORED_LIGHTING", "COLORED_LIGHTING_INTERNAL", "SHADOW_QUALITY", "DH_TERRAIN", "DH_WATER", "VOXY_PATCH", "IRIS_FEATURE_CUSTOM_IMAGES", "MC_OS_MAC"}) {
                d.append(k).append('=').append(seen.get(k)).append(' ');
            }
            com.qateralong.nimbus.Nimbus.LOGGER.info("Nimbus compute {} macros: {}", name, d);
        }
        Matcher m = Pattern.compile("const\\s+ivec3\\s+workGroups\\s*=\\s*ivec3\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)").matcher(active);
        if (m.find()) {
            p.workGroups = new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
        }
        m = Pattern.compile("const\\s+vec2\\s+workGroupsRender\\s*=\\s*vec2\\s*\\(\\s*([\\d.]+)\\s*,\\s*([\\d.]+)\\s*\\)").matcher(active);
        if (m.find()) {
            p.workGroupsRender = new float[] {Float.parseFloat(m.group(1)), Float.parseFloat(m.group(2))};
        }
        m = Pattern.compile("layout\\s*\\(([^)]*local_size[^)]*)\\)\\s*in").matcher(active);
        if (m.find()) {
            for (String part : m.group(1).split(",")) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    int v = Integer.parseInt(kv[1].trim());
                    switch (kv[0].trim()) {
                        case "local_size_x" -> p.localSize[0] = v;
                        case "local_size_y" -> p.localSize[1] = v;
                        case "local_size_z" -> p.localSize[2] = v;
                        default -> {
                        }
                    }
                }
            }
        }
        p.bindSamplers(false);
        return p;
    }

    private static int compile(int program, int type, String source, String file) throws IOException {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        String dump = System.getProperty("nimbus.shaderDump");
        if (dump != null) {
            java.nio.file.Path out = java.nio.file.Path.of(dump, file.replace('/', '_'));
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out, source);
        }
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IOException(file + ": " + log);
        }
        GL20.glAttachShader(program, shader);
        return shader;
    }

    // which colortex each fragment output location writes
    private static int[] outputs(String fsh, List<Integer> locations, boolean gbuffers) {
        String digits = null;
        Matcher m = DRAWBUFFERS.matcher(fsh);
        while (m.find()) {
            digits = m.group(1);
        }
        int[] list = null;
        if (digits != null) {
            list = new int[digits.length()];
            for (int i = 0; i < digits.length(); i++) {
                list[i] = Character.digit(digits.charAt(i), 16);
            }
        }
        m = RENDERTARGETS.matcher(fsh);
        while (m.find()) {
            String[] parts = m.group(1).trim().split("\\s*,\\s*");
            list = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                list[i] = Integer.parseInt(parts[i].trim());
            }
        }
        if (list != null) {
            return list;
        }
        int count = locations.isEmpty() ? (gbuffers ? 8 : 1) : locations.get(locations.size() - 1) + 1;
        list = new int[count];
        for (int i = 0; i < count; i++) {
            list[i] = i;
        }
        return list;
    }

    private void bindSamplers(boolean gbuffers) {
        GL20.glUseProgram(this.id);
        if (extraSetup != null) {
            extraSetup.accept(this);
        }
        set("texture", gbuffers ? ATLAS : COLORTEX);
        set("gtexture", gbuffers ? ATLAS : COLORTEX);
        set("tex", gbuffers ? ATLAS : COLORTEX);
        set("lightmap", LIGHTMAP);
        set("normals", NORMALS);
        set("specular", SPECULAR);
        set("shadow", SHADOWTEX0);
        set("watershadow", SHADOWTEX0);
        set("shadowtex0", SHADOWTEX0);
        set("shadowtex1", SHADOWTEX1);
        set("shadowcolor", SHADOWCOLOR0);
        set("shadowcolor0", SHADOWCOLOR0);
        set("shadowcolor1", SHADOWCOLOR1);
        set("depthtex0", DEPTHTEX0);
        set("gdepthtex", DEPTHTEX0);
        set("depthtex1", DEPTHTEX1);
        set("depthtex2", DEPTHTEX2);
        set("noisetex", NOISE);
        String[] legacy = {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};
        for (int i = 0; i < Targets.MAX; i++) {
            set("colortex" + i, COLORTEX + i);
            if (i < legacy.length) {
                set(legacy[i], COLORTEX + i);
            }
        }
        GL20.glUseProgram(0);
    }

    private void set(String sampler, int unit) {
        int l = this.location(sampler);
        if (l >= 0) {
            GL20.glUniform1i(l, unit);
        }
    }

    public void delete() {
        GL20.glDeleteProgram(this.id);
    }

    static void warn(String what, Exception e) {
        Nimbus.LOGGER.warn("Nimbus shader pack: {}: {}", what, e.getMessage());
    }
}
