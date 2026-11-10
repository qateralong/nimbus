package com.qateralong.nimbus.pack;

import com.qateralong.nimbus.Nimbus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

// -Dnimbus.shaderTest=<pack zip or folder>[,<pack>...]: transform and compile every program of the packs once the game is
// up, report what fails (log and nimbus-shadertest.txt in the game folder).
public final class PackTest {
    private PackTest() {
    }

    public static void runFromProperty(Path gameDir) {
        String packs = System.getProperty("nimbus.shaderTest");
        if (packs == null || packs.isBlank()) {
            return;
        }
        StringBuilder report = new StringBuilder();
        for (String pack : packs.split(",")) {
            report.append(run(Path.of(pack.trim())));
        }
        try {
            Files.writeString(gameDir.resolve("nimbus-shadertest.txt"), report, StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            Nimbus.LOGGER.warn("Nimbus shader test: report not written", e);
        }
    }

    public static String defines() {
        String vendor = String.valueOf(GL11.glGetString(GL11.GL_VENDOR)).toLowerCase(Locale.ROOT);
        String renderer = String.valueOf(GL11.glGetString(GL11.GL_RENDERER)).toLowerCase(Locale.ROOT);
        StringBuilder d = new StringBuilder();
        d.append("#define MC_VERSION 12101\n#define MC_GL_VERSION 460\n#define MC_GLSL_VERSION 460\n");
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        d.append(os.contains("win") ? "#define MC_OS_WINDOWS\n" : os.contains("mac") ? "#define MC_OS_MAC\n" : "#define MC_OS_LINUX\n");
        if (vendor.contains("ati") || vendor.contains("amd")) {
            d.append("#define MC_GL_VENDOR_ATI\n");
        } else if (vendor.contains("nvidia")) {
            d.append("#define MC_GL_VENDOR_NVIDIA\n");
        } else if (vendor.contains("intel")) {
            d.append("#define MC_GL_VENDOR_INTEL\n");
        } else if (vendor.contains("mesa") || renderer.contains("mesa")) {
            d.append("#define MC_GL_VENDOR_MESA\n");
        } else {
            d.append("#define MC_GL_VENDOR_OTHER\n");
        }
        if (renderer.contains("radeon")) {
            d.append("#define MC_GL_RENDERER_RADEON\n");
        } else if (renderer.contains("geforce") || renderer.contains("nvidia")) {
            d.append("#define MC_GL_RENDERER_GEFORCE\n");
        } else if (renderer.contains("intel")) {
            d.append("#define MC_GL_RENDERER_INTEL\n");
        } else {
            d.append("#define MC_GL_RENDERER_OTHER\n");
        }
        // packs pick their Iris paths by these; the feature flags are the Iris features we implement
        d.append("#define IS_IRIS 1\n#define IRIS_VERSION 10814\n");
        for (String f : new String[] {"CUSTOM_IMAGES", "SSBO", "COMPUTE_SHADERS", "PER_BUFFER_BLENDING", "ENTITY_TRANSLUCENT", "BLOCK_EMISSION_ATTRIBUTE", "REVERSED_CULLING"}) {
            d.append("#define IRIS_FEATURE_").append(f).append(" 1\n");
        }
        d.append("#define MC_RENDER_QUALITY 1.0\n#define MC_SHADOW_QUALITY 1.0\n#define MC_HAND_DEPTH 0.125\n");
        String[] stages = {"NONE", "SKY", "SUNSET", "CUSTOM_SKY", "SUN", "MOON", "STARS", "VOID", "TERRAIN_SOLID", "TERRAIN_CUTOUT_MIPPED", "TERRAIN_CUTOUT",
                "ENTITIES", "BLOCK_ENTITIES", "DESTROY", "OUTLINE", "DEBUG", "HAND_SOLID", "TERRAIN_TRANSLUCENT", "TRIPWIRE", "PARTICLES", "CLOUDS", "RAIN_SNOW",
                "WORLD_BORDER", "HAND_TRANSLUCENT"};
        for (int i = 0; i < stages.length; i++) {
            d.append("#define MC_RENDER_STAGE_").append(stages[i]).append(' ').append(i).append('\n');
        }
        return d.toString();
    }

    public static String run(Path path) {
        StringBuilder report = new StringBuilder();
        String defines = defines();
        try (ShaderPack pack = new ShaderPack(path)) {
            String dir = pack.list("world0").isEmpty() ? "" : "world0";
            PackConfig config = PackConfig.load(pack, defines);
            Map<String, Map<String, String>> programs = new TreeMap<>();
            for (String file : pack.list(dir)) {
                int dot = file.lastIndexOf('.');
                if (dot < 0) {
                    continue;
                }
                String ext = file.substring(dot + 1);
                if (file.startsWith("clrwl_") || file.startsWith("dh_") || file.indexOf('.') > 0 && !config.programEnabled(dir, file.substring(0, file.indexOf('.')))) {
                    continue;
                }
                if (ext.equals("vsh") || ext.equals("fsh") || ext.equals("gsh") || ext.equals("csh")) {
                    programs.computeIfAbsent(file.substring(0, dot), k -> new LinkedHashMap<>()).put(ext, (dir.isEmpty() ? "" : dir + "/") + file);
                }
            }
            int ok = 0;
            int failed = 0;
            for (var entry : programs.entrySet()) {
                String error = compile(pack, entry.getValue(), defines);
                if (error == null) {
                    ok++;
                } else {
                    failed++;
                    report.append("FAIL ").append(entry.getKey()).append(": ").append(error.trim().replace("\n", "\n    ")).append('\n');
                }
            }
            String summary = String.format(Locale.ROOT, "%s: %d programs, %d compiled, %d failed", pack.name(), ok + failed, ok, failed);
            Nimbus.LOGGER.info("Nimbus shader test {}", summary);
            report.insert(0, "== " + summary + "\n");
        } catch (Exception e) {
            Nimbus.LOGGER.warn("Nimbus shader test failed for {}", path, e);
            report.append("== ").append(path).append(": ").append(e).append('\n');
        }
        return report.toString();
    }

    private static String compile(ShaderPack pack, Map<String, String> files, String defines) throws java.io.IOException {
        if (files.containsKey("csh")) {
            return single(pack, files.get("csh"), GL43.GL_COMPUTE_SHADER, Glsl.Stage.COMPUTE, defines);
        }
        if (!files.containsKey("vsh") || !files.containsKey("fsh")) {
            return null;
        }
        int program = GL20.glCreateProgram();
        int[] shaders = new int[3];
        String[][] stages = {{"vsh", "VERTEX"}, {"gsh", "GEOMETRY"}, {"fsh", "FRAGMENT"}};
        try {
            for (int i = 0; i < stages.length; i++) {
                String file = files.get(stages[i][0]);
                if (file == null) {
                    continue;
                }
                Glsl.Stage stage = Glsl.Stage.valueOf(stages[i][1]);
                int type = stage == Glsl.Stage.VERTEX ? GL20.GL_VERTEX_SHADER : stage == Glsl.Stage.GEOMETRY ? 0x8DD9 : GL20.GL_FRAGMENT_SHADER;
                // gbuffers/shadow programs are compiled as the pipeline does: vertex wrapped for the entity format
                boolean gbuffers = name(file).startsWith("gbuffers_") || name(file).startsWith("shadow");
                String source = Glsl.transform(pack.expand(file), stage, defines,
                        gbuffers && stage == Glsl.Stage.VERTEX ? ENTITY : null, gbuffers, gbuffers && !files.containsKey("gsh")).source();
                dump(file, source);
                int shader = GL20.glCreateShader(type);
                shaders[i] = shader;
                GL20.glShaderSource(shader, source);
                GL20.glCompileShader(shader);
                if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
                    return file + ": " + firstLines(GL20.glGetShaderInfoLog(shader), source);
                }
                GL20.glAttachShader(program, shader);
            }
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                return "link: " + firstLines(GL20.glGetProgramInfoLog(program), null);
            }
            return null;
        } finally {
            for (int shader : shaders) {
                if (shader != 0) {
                    GL20.glDeleteShader(shader);
                }
            }
            GL20.glDeleteProgram(program);
        }
    }

    private static final List<String> ENTITY = List.of("Position", "Color", "UV0", "UV1", "UV2", "Normal");

    private static String name(String file) {
        return file.substring(file.lastIndexOf('/') + 1);
    }

    private static void dump(String file, String source) throws java.io.IOException {
        String dir = System.getProperty("nimbus.shaderDump");
        if (dir != null) {
            java.nio.file.Path out = java.nio.file.Path.of(dir, file.replace('/', '_'));
            java.nio.file.Files.createDirectories(out.getParent());
            java.nio.file.Files.writeString(out, source);
        }
    }

    private static String single(ShaderPack pack, String file, int type, Glsl.Stage stage, String defines) throws java.io.IOException {
        String source = Glsl.transform(pack.expand(file), stage, defines).source();
        dump(file, source);
        int shader = GL20.glCreateShader(type);
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
                return file + ": " + firstLines(GL20.glGetShaderInfoLog(shader), source);
            }
            return null;
        } finally {
            GL20.glDeleteShader(shader);
        }
    }

    // the first errors with the offending source lines
    private static String firstLines(String log, String source) {
        String[] lines = source == null ? new String[0] : source.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (String line : log.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            out.append(line).append('\n');
            var m = java.util.regex.Pattern.compile("^\\d+:(\\d+)\\(").matcher(line);
            if (m.find()) {
                int n = Integer.parseInt(m.group(1)) - 1;
                if (n >= 0 && n < lines.length) {
                    out.append("      > ").append(lines[n].trim()).append('\n');
                }
            }
            if (++count == 4) {
                break;
            }
        }
        return out.toString();
    }
}
