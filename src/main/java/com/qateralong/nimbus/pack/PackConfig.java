package com.qateralong.nimbus.pack;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Pack-wide settings: option values (the #define defaults of the pack's shared sources, standard MC_* macros on top) and
// shaders.properties run through the preprocessor with them.
public final class PackConfig {
    private static final Pattern DEFINE = Pattern.compile("^\\s*#define\\s+([A-Za-z_]\\w*)(?:[ \\t]+([^/\\n]*?))?\\s*(?://.*)?$", Pattern.MULTILINE);
    private static final Pattern DEFINE_LINE = Pattern.compile("^\\s*#define\\s+(\\w+)\\s*(.*)$", Pattern.MULTILINE);

    public final Map<String, String> macros = new HashMap<>();
    public final Map<String, String> properties = new LinkedHashMap<>();

    public static PackConfig load(ShaderPack pack, String standardDefines) throws IOException {
        PackConfig config = new PackConfig();
        for (String file : pack.listAll()) {
            boolean rootProgram = !file.contains("/") && (file.endsWith(".vsh") || file.endsWith(".fsh") || file.endsWith(".gsh") || file.endsWith(".csh"));
            if (file.startsWith("world") || rootProgram || !ShaderPack.isSource(file)) {
                continue;
            }
            String text = pack.read(file);
            if (text == null) {
                continue;
            }
            Matcher m = DEFINE.matcher(text);
            while (m.find()) {
                // only options, as Iris does: other defines (VOXY_PATCH in a Voxy include...) would switch branches of every file
                if (pack.options == null || pack.options.options.containsKey(m.group(1))) {
                    config.macros.putIfAbsent(m.group(1), m.group(2) == null ? "" : m.group(2).trim());
                }
            }
        }
        Matcher m = DEFINE_LINE.matcher(standardDefines);
        while (m.find()) {
            config.macros.put(m.group(1), m.group(2).trim());
        }
        String props = pack.read("shaders.properties");
        if (props != null) {
            String active = Preprocessor.run(props, new HashMap<>(config.macros));
            String pending = null;
            for (String line : active.split("\n")) {
                String t = line.trim();
                if (pending != null) {
                    t = pending + " " + t;
                    pending = null;
                }
                if (t.endsWith("\\")) {
                    pending = t.substring(0, t.length() - 1);
                    continue;
                }
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                int eq = t.indexOf('=');
                if (eq > 0) {
                    config.properties.put(t.substring(0, eq).trim(), t.substring(eq + 1).trim());
                }
            }
        }
        return config;
    }

    public String property(String key) {
        return this.properties.get(key);
    }

    public boolean truthy(String expression) {
        if (expression == null) {
            return true;
        }
        String e = expression.trim();
        if (e.equalsIgnoreCase("true")) {
            return true;
        }
        if (e.equalsIgnoreCase("false")) {
            return false;
        }
        return Preprocessor.eval(e, this.macros) != 0;
    }

    // program.<dir>/<name>.enabled or program.<name>.enabled
    public boolean programEnabled(String dir, String name) {
        String value = this.property("program." + (dir.isEmpty() ? "" : dir + "/") + name + ".enabled");
        if (value == null) {
            value = this.property("program." + name + ".enabled");
        }
        return this.truthy(value);
    }
}
