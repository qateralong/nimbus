package com.qateralong.nimbus.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Shader pack GLSL (OptiFine style: #version 120/130 or 4xx compatibility, fixed-function built-ins) rewritten for the
// core profile Minecraft runs on: built-in matrices and vertex arrays become Nimbus uniforms and attributes, varyings
// and fragment outputs become in/out, removed texture functions their core names.
public final class Glsl {
    public enum Stage { VERTEX, GEOMETRY, FRAGMENT, COMPUTE }

    private static final Pattern VERSION = Pattern.compile("^\\s*#version\\s+(\\d+)(\\s+\\w+)?\\s*$", Pattern.MULTILINE);
    private static final Pattern FRAG_DATA = Pattern.compile("\\bgl_FragData\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final Pattern TEX_COORD = Pattern.compile("\\bgl_TexCoord\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final Pattern TEXTURE_MATRIX = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*(\\d+)\\s*\\]");
    private static final String[][] FUNCTIONS = {
            {"texture2DLod", "textureLod"}, {"texture2DGradARB", "textureGrad"}, {"texture2DGrad", "textureGrad"}, {"texture2DProj", "textureProj"},
            {"texture2D", "texture"}, {"texture3D", "texture"}, {"textureCube", "texture"}, {"texelFetch2D", "texelFetch"},
            {"shadow2DLod", "nimbus_shadow2DLod"}, {"shadow2D", "nimbus_shadow2D"}, {"texture1D", "texture"}};

    private Glsl() {
    }

    public record Result(String source, int version, List<Integer> outputs) {
    }

    public static Result transform(String source, Stage stage, String defines) {
        return transform(source, stage, defines, null);
    }

    // inputs marker: our terrain engine's packed vertices instead of a vanilla vertex format
    public static final List<String> ENGINE = List.of("nimbus:engine");
    public static volatile String enginePrelude = "";
    public static volatile boolean separateAo;
    private static final String[] EXTRA = {"mc_Entity", "mc_midTexCoord", "at_tangent", "at_midBlock"};

    // per-draw ids Iris provides as uniforms; here they ride on the vertices (see EntityTags)
    private static final Pattern TAG_UNIFORM = Pattern.compile("\\buniform\\s+int\\s+(entityId|blockEntityId|currentRenderedItemId)\\s*;");
    private static final String TAG_FIELDS = "entityId:x blockEntityId:y currentRenderedItemId:z";

    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)");

    // inputs: names of the vanilla vertex format's elements the program is fed from (Position, Color, UV0, UV1, UV2, Normal);
    // null for our own attributes (full-screen passes)
    public static Result transform(String source, Stage stage, String defines, List<String> inputs) {
        return transform(source, stage, defines, inputs, false);
    }

    // alphaTest: gbuffers fragment programs get the fixed-function alpha test OptiFine packs rely on
    public static Result transform(String source, Stage stage, String defines, List<String> inputs, boolean alphaTest) {
        return transform(source, stage, defines, inputs, alphaTest, false);
    }

    // tags: a gbuffers fragment program fed straight by our wrapped vertex stage reads entityId & co. from it
    public static Result transform(String source, Stage stage, String defines, List<String> inputs, boolean alphaTest, boolean tags) {
        Matcher version = VERSION.matcher(source);
        int original = 110;
        boolean core = false;
        if (version.find()) {
            original = Integer.parseInt(version.group(1));
            core = version.group(2) != null && version.group(2).trim().equals("core");
            source = source.substring(0, version.start()) + source.substring(version.end());
        }
        int target = Math.max(original, stage == Stage.COMPUTE || inputs == ENGINE ? 430 : 330);
        // hoist top-level #extension lines so they precede everything we prepend
        StringBuilder extensions = new StringBuilder();
        StringBuilder body = new StringBuilder(source.length() + 1024);
        int depth = 0;
        for (String line : source.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("#if")) {
                depth++;
            } else if (t.startsWith("#endif")) {
                depth--;
            }
            if (t.startsWith("#extension")) {
                extensions.append(t).append('\n');
                body.append('\n');
            } else {
                body.append(line).append('\n');
            }
        }
        String text = body.toString();
        if (!core) {
            text = rewrite(text, stage);
        }
        boolean wrap = !core && stage == Stage.VERTEX && inputs != null;
        if (wrap) {
            // mc_Entity and friends are filled by the wrapper, not read from attributes
            java.util.Map<String, String> extras = new java.util.HashMap<>();
            for (String name : EXTRA) {
                Matcher m = Pattern.compile("\\bin\\s+(\\w+)\\s+" + name + "\\s*;").matcher(text);
                if (m.find()) {
                    // declared once at the top by us, so it exists whatever the pack's #if blocks around its own declaration say
                    String type = m.group(1);
                    extras.put(name, type);
                    text = type + " " + name + ";\n" + m.replaceAll("");
                }
            }
            // declared once at the top, whatever #if blocks surround the pack's own declarations
            StringBuilder tagged = new StringBuilder();
            StringBuilder declared = new StringBuilder();
            java.util.Set<String> seen = new java.util.HashSet<>();
            Matcher t = TAG_UNIFORM.matcher(text);
            while (t.find()) {
                if (seen.add(t.group(1))) {
                    tagged.append("    ").append(t.group(1)).append(" = nimbus_tagv.").append(tagField(t.group(1))).append(";\n");
                    declared.append("int ").append(t.group(1)).append(";\n");
                }
            }
            text = declared + TAG_UNIFORM.matcher(text).replaceAll("");
            String entityType = extras.get("mc_Entity");
            text = MAIN.matcher(text).replaceAll("void nimbus_main()") + (inputs == ENGINE ? engineWrapper(extras, tagged) : wrapper(text, inputs, entityType, extras, tagged));
        }
        if (!core && tags && stage == Stage.FRAGMENT && TAG_UNIFORM.matcher(text).find()) {
            // globals filled before the pack's main runs (a #define would also hit locals of the same name)
            StringBuilder declared = new StringBuilder("flat in ivec3 nimbus_tagv;\n");
            StringBuilder assigned = new StringBuilder();
            java.util.Set<String> seen = new java.util.HashSet<>();
            Matcher t = TAG_UNIFORM.matcher(text);
            while (t.find()) {
                if (seen.add(t.group(1))) {
                    declared.append("int ").append(t.group(1)).append(";\n");
                    assigned.append("    ").append(t.group(1)).append(" = nimbus_tagv.").append(tagField(t.group(1))).append(";\n");
                }
            }
            text = declared + TAG_UNIFORM.matcher(text).replaceAll("");
            text = MAIN.matcher(text).replaceAll("void nimbus_tagmain()") + "\nvoid main() {\n" + assigned + "    nimbus_tagmain();\n}\n";
        }
        if (!core && alphaTest && stage == Stage.FRAGMENT && Pattern.compile("\\bnimbus_FragData0\\b").matcher(text).find()) {
            text = MAIN.matcher(text).replaceAll("void nimbus_main()")
                    + "\nuniform float nimbus_AlphaRef;\nvoid main() {\n    nimbus_main();\n    if (nimbus_FragData0.a <= nimbus_AlphaRef) discard;\n}\n";
        }
        List<Integer> outputs = new ArrayList<>();
        StringBuilder head = new StringBuilder();
        head.append("#version ").append(target).append(" core\n");
        head.append(extensions);
        head.append(defines);
        if (!core) {
            declare(head, text, stage, outputs, wrap ? inputs : null);
        }
        return new Result(head + text, target, outputs);
    }

    private static String rewrite(String text, Stage stage) {
        // a sampler called "texture" would hide the core texture() function: it becomes gtexture, as Iris names it
        if (Pattern.compile("\\buniform\\s+\\w+\\s+texture\\s*;").matcher(text).find()) {
            text = text.replaceAll("\\btexture\\b(?!\\s*\\()", "gtexture");
        }
        text = FRAG_DATA.matcher(text).replaceAll("nimbus_FragData$1");
        text = word(text, "gl_FragColor", "nimbus_FragData0");
        text = TEXTURE_MATRIX.matcher(text).replaceAll("nimbus_TextureMatrix$1");
        text = TEX_COORD.matcher(text).replaceAll("nimbus_TexCoord$1");
        text = word(text, "gl_ModelViewProjectionMatrix", "(nimbus_ProjectionMatrix * nimbus_ModelViewMatrix)");
        text = word(text, "gl_ModelViewMatrixInverse", "nimbus_ModelViewMatrixInverse");
        text = word(text, "gl_ProjectionMatrixInverse", "nimbus_ProjectionMatrixInverse");
        text = word(text, "gl_ModelViewMatrix", "nimbus_ModelViewMatrix");
        text = word(text, "gl_ProjectionMatrix", "nimbus_ProjectionMatrix");
        text = word(text, "gl_NormalMatrix", "nimbus_NormalMatrix");
        text = text.replaceAll("\\bgl_Fog\\s*\\.\\s*color\\b", "nimbus_FogColor").replaceAll("\\bgl_Fog\\s*\\.\\s*start\\b", "nimbus_FogStart")
                .replaceAll("\\bgl_Fog\\s*\\.\\s*end\\b", "nimbus_FogEnd").replaceAll("\\bgl_Fog\\s*\\.\\s*density\\b", "nimbus_FogDensity")
                .replaceAll("\\bgl_Fog\\s*\\.\\s*scale\\b", "nimbus_FogScale");
        text = word(text, "gl_FogFragCoord", "nimbus_FogFragCoord");
        for (String[] f : FUNCTIONS) {
            if (!text.contains(f[0])) {
                continue;
            }
            text = text.replaceAll("\\b" + f[0] + "\\s*\\(", f[1] + "(");
        }
        if (stage == Stage.VERTEX) {
            text = text.replaceAll("\\bftransform\\s*\\(\\s*\\)", "(nimbus_ProjectionMatrix * (nimbus_ModelViewMatrix * nimbus_Vertex))");
            text = word(text, "gl_Vertex", "nimbus_Vertex");
            text = word(text, "gl_Color", "nimbus_Color");
            text = word(text, "gl_Normal", "nimbus_Normal");
            text = word(text, "gl_MultiTexCoord0", "nimbus_MultiTexCoord0");
            text = word(text, "gl_MultiTexCoord1", "nimbus_MultiTexCoord1");
            text = word(text, "gl_MultiTexCoord2", "nimbus_MultiTexCoord1");
            text = word(text, "gl_FrontColor", "nimbus_FrontColor");
            text = word(text, "attribute", "in");
            text = word(text, "varying", "out");
        } else if (stage == Stage.FRAGMENT) {
            text = word(text, "gl_Color", "nimbus_FrontColor");
            text = word(text, "varying", "in");
        } else if (stage == Stage.GEOMETRY) {
            text = word(text, "varying", "out");
        }
        return text;
    }

    private static String word(String text, String from, String to) {
        if (!text.contains(from)) {
            return text;
        }
        return text.replaceAll("\\b" + Pattern.quote(from) + "\\b", Matcher.quoteReplacement(to));
    }

    private static String tagField(String name) {
        int i = TAG_FIELDS.indexOf(name + ":");
        return TAG_FIELDS.substring(i + name.length() + 1, i + name.length() + 2);
    }

    private static boolean uses(String text, String name) {
        return text.contains(name) && Pattern.compile("\\b" + name + "\\b").matcher(text).find();
    }

    private static String cast(String type, String vec4) {
        return switch (type) {
            case "vec4" -> vec4;
            case "vec3" -> "(" + vec4 + ").xyz";
            case "vec2" -> "(" + vec4 + ").xy";
            case "float" -> "(" + vec4 + ").x";
            case "ivec4" -> "ivec4(" + vec4 + ")";
            case "ivec3" -> "ivec3((" + vec4 + ").xyz)";
            case "ivec2" -> "ivec2((" + vec4 + ").xy)";
            case "int" -> "int((" + vec4 + ").x)";
            default -> vec4;
        };
    }

    // our engine's packed vertices: position, uv, light, tint (rgb565) + occlusion + block id index; normal, mid texture
    // coordinate and tangent come from the quad's four vertices
    private static String engineWrapper(java.util.Map<String, String> extras, CharSequence tagged) {
        StringBuilder w = new StringBuilder("\nflat out ivec3 nimbus_tagv;\nvec3 nimbus_pos(uvec4 v) {\n");
        w.append("    return vec3(float(v.x & 0xFFFFu), float(v.x >> 16u), float(v.y & 0xFFFFu)) * (1.0 / 1024.0) - 16.0;\n}\n");
        w.append("vec2 nimbus_uv(uvec4 v) {\n    return vec2(float(v.y >> 16u), float(v.z & 0xFFFFu)) * (1.0 / 65536.0);\n}\n");
        w.append("void main() {\n");
        w.append("    int nimbus_i = gl_VertexID;\n    int nimbus_q = nimbus_i & ~3;\n");
        w.append("    uvec4 nimbus_v0 = nimbus_fetchVertex(nimbus_q);\n    uvec4 nimbus_v1 = nimbus_fetchVertex(nimbus_q + 1);\n");
        w.append("    uvec4 nimbus_v2 = nimbus_fetchVertex(nimbus_q + 2);\n    uvec4 nimbus_v3 = nimbus_fetchVertex(nimbus_q + 3);\n");
        // the vertex itself is one of its quad's four
        w.append("    int nimbus_k = nimbus_i & 3;\n");
        w.append("    uvec4 nimbus_v = nimbus_k == 0 ? nimbus_v0 : nimbus_k == 1 ? nimbus_v1 : nimbus_k == 2 ? nimbus_v2 : nimbus_v3;\n");
        w.append("    vec3 nimbus_p0 = nimbus_pos(nimbus_v0);\n    vec3 nimbus_p1 = nimbus_pos(nimbus_v1);\n    vec3 nimbus_p2 = nimbus_pos(nimbus_v2);\n");
        w.append("    vec3 nimbus_p3 = nimbus_pos(nimbus_v3);\n");
        w.append("    nimbus_Vertex = vec4(nimbus_pos(nimbus_v) + vec3(nimbus_Origin - nimbus_CameraBlock) - nimbus_CameraFrac, 1.0);\n");
        w.append("    uint nimbus_c = nimbus_v.w;\n");
        w.append("    nimbus_tagv = ivec3(-1, 0, -1);\n").append(tagged);
        w.append("    vec3 nimbus_rgb = vec3(float((nimbus_c >> 27u) & 31u) / 31.0, float((nimbus_c >> 21u) & 63u) / 63.0, float((nimbus_c >> 16u) & 31u) / 31.0);\n");
        w.append("    float nimbus_ao = float((nimbus_c >> 9u) & 127u) / 127.0;\n");
        w.append("    nimbus_Color = ").append(separateAo ? "vec4(nimbus_rgb, nimbus_ao)" : "vec4(nimbus_rgb * nimbus_ao, 1.0)").append(";\n");
        w.append("    vec2 nimbus_t = nimbus_uv(nimbus_v);\n");
        w.append("    nimbus_MultiTexCoord0 = vec4(nimbus_t, 0.0, 1.0);\n");
        w.append("    nimbus_MultiTexCoord1 = vec4(float((nimbus_v.z >> 16u) & 0xFFu), float(nimbus_v.z >> 24u), 0.0, 1.0);\n");
        w.append("    vec3 nimbus_e1 = nimbus_p1 - nimbus_p0;\n    vec3 nimbus_e2 = nimbus_p2 - nimbus_p0;\n");
        w.append("    vec3 nimbus_n = cross(nimbus_e1, nimbus_e2) + cross(nimbus_p2 - nimbus_p0, nimbus_p3 - nimbus_p0);\n");
        w.append("    nimbus_Normal = length(nimbus_n) > 0.0 ? normalize(nimbus_n) : vec3(0.0, 1.0, 0.0);\n");
        if (extras.containsKey("mc_Entity")) {
            w.append("    mc_Entity = ").append(cast(extras.get("mc_Entity"), "vec4(float((nimbus_Ids[int(nimbus_c & 511u)] & 0x1FFFF) - 1), -1.0, 0.0, 0.0)")).append(";\n");
        }
        if (extras.containsKey("mc_midTexCoord")) {
            w.append("    vec2 nimbus_mid = (nimbus_uv(nimbus_v0) + nimbus_uv(nimbus_v1) + nimbus_uv(nimbus_v2) + nimbus_uv(nimbus_v3)) * 0.25;\n");
            w.append("    mc_midTexCoord = ").append(cast(extras.get("mc_midTexCoord"), "vec4(nimbus_mid, 0.0, 1.0)")).append(";\n");
        }
        if (extras.containsKey("at_tangent")) {
            w.append("    vec2 nimbus_d1 = nimbus_uv(nimbus_v1) - nimbus_uv(nimbus_v0);\n    vec2 nimbus_d2 = nimbus_uv(nimbus_v2) - nimbus_uv(nimbus_v0);\n");
            w.append("    float nimbus_det = nimbus_d1.x * nimbus_d2.y - nimbus_d2.x * nimbus_d1.y;\n");
            w.append("    vec3 nimbus_tan = (nimbus_e1 * nimbus_d2.y - nimbus_e2 * nimbus_d1.y) * (abs(nimbus_det) > 1e-12 ? 1.0 / nimbus_det : 0.0);\n");
            w.append("    vec3 nimbus_bit = (nimbus_e2 * nimbus_d1.x - nimbus_e1 * nimbus_d2.x);\n");
            w.append("    nimbus_tan = length(nimbus_tan) > 0.0 ? normalize(nimbus_tan) : vec3(1.0, 0.0, 0.0);\n");
            w.append("    float nimbus_w = dot(cross(nimbus_Normal, nimbus_tan), nimbus_bit) < 0.0 ? -1.0 : 1.0;\n");
            w.append("    at_tangent = ").append(cast(extras.get("at_tangent"), "vec4(nimbus_tan, nimbus_w)")).append(";\n");
        }
        if (extras.containsKey("at_midBlock")) {
            // the block a face belongs to: just behind the quad's centre; at_midBlock.w is its light emission
            w.append("    vec3 nimbus_mb = floor((nimbus_p0 + nimbus_p1 + nimbus_p2 + nimbus_p3) * 0.25 - nimbus_Normal * 0.01) + 0.5;\n");
            w.append("    at_midBlock = ").append(cast(extras.get("at_midBlock"),
                    "vec4((nimbus_mb - nimbus_pos(nimbus_v)) * 64.0, float(nimbus_Ids[int(nimbus_c & 511u)] >> 17))")).append(";\n");
        }
        w.append("    nimbus_main();\n}\n");
        return w.toString();
    }

    private static String wrapper(String text, List<String> inputs, String entityType, java.util.Map<String, String> extras, CharSequence tagged) {
        StringBuilder w = new StringBuilder("\nuniform ivec3 nimbus_Tags[" + EntityTags.SIZE + "];\nflat out ivec3 nimbus_tagv;\nvoid main() {\n");
        boolean light = inputs.contains("UV2");
        if (light) {
            w.append("    ivec2 nimbus_uv2 = nimbus_in_UV2 & 0xFFFF;\n");
            w.append("    int nimbus_raw = ((nimbus_uv2.x >> 8) & 255) | (((nimbus_uv2.y >> 8) & 255) << 8);\n");
            w.append("    bool nimbus_tagged = (nimbus_raw & ").append(EntityTags.MARK).append(") != 0;\n");
            w.append("    nimbus_tagv = nimbus_tagged ? nimbus_Tags[nimbus_raw & ").append(EntityTags.SIZE - 1).append("] : ivec3(-1, 0, -1);\n");
        } else {
            w.append("    nimbus_tagv = ivec3(-1, 0, -1);\n");
        }
        w.append(tagged);
        if (entityType != null) {
            String id = light ? "(nimbus_tagged ? -1.0 : float(nimbus_raw) - 1.0)" : "-1.0";
            String value = switch (entityType) {
                case "vec4" -> "vec4(" + id + ", -1.0, 0.0, 0.0)";
                case "vec3" -> "vec3(" + id + ", -1.0, 0.0)";
                case "vec2" -> "vec2(" + id + ", -1.0)";
                case "ivec4" -> "ivec4(int(" + id + "), -1, 0, 0)";
                case "ivec3" -> "ivec3(int(" + id + "), -1, 0)";
                case "ivec2" -> "ivec2(int(" + id + "), -1)";
                case "int" -> "int(" + id + ")";
                default -> id;
            };
            w.append("    mc_Entity = ").append(value).append(";\n");
        }
        w.append("    nimbus_Vertex = vec4(").append(inputs.contains("Position") ? "nimbus_in_Position + nimbus_ChunkOffset" : "0.0, 0.0, 0.0").append(", 1.0);\n");
        w.append("    nimbus_Color = ").append(inputs.contains("Color") ? "nimbus_in_Color" : "vec4(1.0)").append(";\n");
        w.append("    nimbus_MultiTexCoord0 = vec4(").append(inputs.contains("UV0") ? "nimbus_in_UV0" : "0.0, 0.0").append(", 0.0, 1.0);\n");
        w.append("    nimbus_MultiTexCoord1 = vec4(").append(inputs.contains("UV2") ? "vec2(nimbus_uv2 & 255)" : "240.0, 240.0").append(", 0.0, 1.0);\n");
        w.append("    nimbus_Normal = ").append(inputs.contains("Normal") ? "nimbus_in_Normal" : "vec3(0.0, 1.0, 0.0)").append(";\n");
        if (extras.containsKey("mc_midTexCoord")) {
            w.append("    mc_midTexCoord = ").append(cast(extras.get("mc_midTexCoord"), "nimbus_MultiTexCoord0")).append(";\n");
        }
        if (extras.containsKey("at_tangent")) {
            w.append("    at_tangent = ").append(cast(extras.get("at_tangent"), "vec4(1.0, 0.0, 0.0, 1.0)")).append(";\n");
        }
        if (extras.containsKey("at_midBlock")) {
            w.append("    at_midBlock = ").append(cast(extras.get("at_midBlock"), "vec4(0.0)")).append(";\n");
        }
        w.append("    nimbus_main();\n}\n");
        return w.toString();
    }

    private static void declare(StringBuilder head, String text, Stage stage, List<Integer> outputs, List<String> inputs) {
        String[][] uniforms = {{"mat4", "nimbus_ModelViewMatrix"}, {"mat4", "nimbus_ProjectionMatrix"}, {"mat4", "nimbus_ModelViewMatrixInverse"},
                {"mat4", "nimbus_ProjectionMatrixInverse"}, {"mat3", "nimbus_NormalMatrix"}, {"vec4", "nimbus_FogColor"}, {"float", "nimbus_FogStart"},
                {"float", "nimbus_FogEnd"}, {"float", "nimbus_FogDensity"}, {"float", "nimbus_FogScale"}};
        for (String[] u : uniforms) {
            if (uses(text, u[1])) {
                head.append("uniform ").append(u[0]).append(' ').append(u[1]).append(";\n");
            }
        }
        TreeSet<Integer> matrices = numbered(text, "nimbus_TextureMatrix");
        for (int i : matrices) {
            head.append("uniform mat4 nimbus_TextureMatrix").append(i).append(";\n");
        }
        String varyingOut = stage == Stage.VERTEX ? "out" : "in";
        if (stage == Stage.VERTEX) {
            String[][] attributes = {{"vec4", "nimbus_Vertex"}, {"vec4", "nimbus_Color"}, {"vec3", "nimbus_Normal"}, {"vec4", "nimbus_MultiTexCoord0"},
                    {"vec4", "nimbus_MultiTexCoord1"}};
            if (inputs == ENGINE) {
                head.append("layout(location = 0) in ivec3 nimbus_Origin;\nuniform ivec3 nimbus_CameraBlock;\nuniform vec3 nimbus_CameraFrac;\n");
                head.append("uniform int nimbus_Ids[512];\n");
                head.append(enginePrelude.replace("fetchVertex", "nimbus_fetchVertex").replace("page", "nimbus_page").replace("Page", "NimbusPage"));
                for (String[] a : attributes) {
                    head.append(a[0]).append(' ').append(a[1]).append(";\n");
                }
            } else if (inputs != null) {
                String[][] vanilla = {{"Position", "vec3"}, {"Color", "vec4"}, {"UV0", "vec2"}, {"UV1", "ivec2"}, {"UV2", "ivec2"}, {"Normal", "vec3"}};
                for (String[] v : vanilla) {
                    if (inputs.contains(v[0])) {
                        head.append("in ").append(v[1]).append(" nimbus_in_").append(v[0]).append(";\n");
                    }
                }
                head.append("uniform vec3 nimbus_ChunkOffset;\n");
                for (String[] a : attributes) {
                    head.append(a[0]).append(' ').append(a[1]).append(";\n");
                }
            } else {
                for (String[] a : attributes) {
                    if (uses(text, a[1])) {
                        head.append("in ").append(a[0]).append(' ').append(a[1]).append(";\n");
                    }
                }
            }
        }
        if (stage == Stage.VERTEX || stage == Stage.FRAGMENT) {
            if (uses(text, "nimbus_FrontColor")) {
                head.append(varyingOut).append(" vec4 nimbus_FrontColor;\n");
            }
            if (uses(text, "nimbus_FogFragCoord")) {
                head.append(varyingOut).append(" float nimbus_FogFragCoord;\n");
            }
            for (int i : numbered(text, "nimbus_TexCoord")) {
                head.append(varyingOut).append(" vec4 nimbus_TexCoord").append(i).append(";\n");
            }
        }
        if (stage == Stage.FRAGMENT) {
            for (int i : numbered(text, "nimbus_FragData")) {
                head.append("layout(location = ").append(i).append(") out vec4 nimbus_FragData").append(i).append(";\n");
                outputs.add(i);
            }
        }
        // Iris core-profile names some packs use for line and other core programs
        String[][] core = stage == Stage.VERTEX
                ? new String[][] {{"in", "vec3", "vaPosition"}, {"in", "vec4", "vaColor"}, {"in", "vec2", "vaUV0"}, {"in", "ivec2", "vaUV1"}, {"in", "ivec2", "vaUV2"},
                {"in", "vec3", "vaNormal"}, {"uniform", "mat4", "modelViewMatrix"}, {"uniform", "mat4", "projectionMatrix"}, {"uniform", "mat3", "normalMatrix"},
                {"uniform", "mat4", "textureMatrix"}, {"uniform", "vec3", "chunkOffset"}}
                : new String[][] {{"uniform", "mat4", "modelViewMatrix"}, {"uniform", "mat4", "projectionMatrix"}, {"uniform", "mat3", "normalMatrix"},
                {"uniform", "mat4", "textureMatrix"}};
        for (String[] c : core) {
            if (uses(text, c[2]) && !declared(text, c[2])) {
                head.append(c[0]).append(' ').append(c[1]).append(' ').append(c[2]).append(";\n");
            }
        }
        if (uses(text, "nimbus_shadow2D")) {
            head.append("vec4 nimbus_shadow2D(sampler2DShadow s, vec3 c) { return vec4(texture(s, c)); }\n");
            if (stage == Stage.FRAGMENT) {
                head.append("vec4 nimbus_shadow2D(sampler2DShadow s, vec3 c, float b) { return vec4(texture(s, c, b)); }\n");
            }
        }
        if (uses(text, "nimbus_shadow2DLod")) {
            head.append("vec4 nimbus_shadow2DLod(sampler2DShadow s, vec3 c, float l) { return vec4(textureLod(s, c, l)); }\n");
        }
    }

    private static boolean declared(String text, String name) {
        return Pattern.compile("\\b(uniform|in|attribute|out|varying)\\s+(\\w+\\s+)*\\w+\\s+" + name + "\\s*[;\\[=,]").matcher(text).find();
    }

    private static TreeSet<Integer> numbered(String text, String prefix) {
        TreeSet<Integer> found = new TreeSet<>();
        if (!text.contains(prefix)) {
            return found;
        }
        Matcher m = Pattern.compile("\\b" + prefix + "(\\d+)\\b").matcher(text);
        while (m.find()) {
            found.add(Integer.parseInt(m.group(1)));
        }
        return found;
    }
}
