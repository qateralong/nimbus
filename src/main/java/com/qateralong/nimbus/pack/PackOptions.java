package com.qateralong.nimbus.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A pack's user options as OptiFine defines them: #define NAME value //[values], //#define NAME switches and
// const int/float NAME = value; //[values]; screens, sliders and profiles from shaders.properties, labels from lang/.
// Chosen values live in shaderpacks/<pack>.txt (the format Iris and OptiFine use) and are applied by rewriting the lines.
public final class PackOptions {
    private static final Pattern VALUE = Pattern.compile("^(\\s*)#define\\s+(\\w+)\\s+([^\\s/]+)(\\s*//\\s*\\[([^\\]]*)\\].*)$");
    private static final Pattern CONST = Pattern.compile("^(\\s*)const\\s+(int|float|bool)\\s+(\\w+)\\s*=\\s*([^;]+);(\\s*//\\s*\\[([^\\]]*)\\].*)$");
    private static final Pattern SWITCH = Pattern.compile("^(\\s*)(//)?\\s*#define\\s+(\\w+)\\s*(//.*)?$");

    public static final class Option {
        public final String name;
        public final String initial;
        public final List<String> values;
        public final boolean toggle;

        Option(String name, String initial, List<String> values, boolean toggle) {
            this.name = name;
            this.initial = initial;
            this.values = values;
            this.toggle = toggle;
        }
    }

    public final Map<String, Option> options = new LinkedHashMap<>();
    public final Map<String, String> chosen = new LinkedHashMap<>();
    public final Map<String, List<String>> screens = new HashMap<>();
    public final Map<String, Map<String, String>> profiles = new LinkedHashMap<>();
    public final java.util.Set<String> sliders = new java.util.HashSet<>();
    public final Map<String, String> lang = new HashMap<>();
    private final Path file;

    private PackOptions(Path file) {
        this.file = file;
    }

    // raw: the pack's files before any rewriting
    public static PackOptions load(ShaderPack pack, Path file) throws IOException {
        PackOptions o = new PackOptions(file);
        java.util.Set<String> referenced = new java.util.HashSet<>();
        String props = pack.read("shaders.properties");
        if (props != null) {
            for (String line : props.split("\n")) {
                String t = line.trim();
                int eq = t.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = t.substring(0, eq).trim();
                String value = t.substring(eq + 1).trim();
                if (key.equals("screen") || key.startsWith("screen.") && !key.endsWith(".columns")) {
                    List<String> entries = new ArrayList<>(Arrays.asList(value.split("\\s+")));
                    entries.removeIf(String::isEmpty);
                    o.screens.put(key.equals("screen") ? "" : key.substring(7), entries);
                    referenced.addAll(entries);
                } else if (key.equals("sliders")) {
                    o.sliders.addAll(Arrays.asList(value.split("\\s+")));
                } else if (key.startsWith("profile.")) {
                    Map<String, String> settings = new LinkedHashMap<>();
                    for (String s : value.split("\\s+")) {
                        if (s.isEmpty() || s.startsWith("profile.")) {
                            continue;
                        }
                        int e = s.indexOf('=');
                        if (e > 0) {
                            settings.put(s.substring(0, e), s.substring(e + 1));
                        } else if (s.startsWith("!")) {
                            settings.put(s.substring(1), "false");
                        } else {
                            settings.put(s, "true");
                        }
                    }
                    o.profiles.put(key.substring(8), settings);
                }
            }
        }
        for (String path : pack.listAll()) {
            if (!ShaderPack.isSource(path)) {
                continue;
            }
            String text = pack.raw(path);
            if (text == null) {
                continue;
            }
            for (String line : text.split("\n")) {
                Matcher m = VALUE.matcher(line);
                if (m.matches()) {
                    o.options.putIfAbsent(m.group(2), new Option(m.group(2), m.group(3), values(m.group(5)), false));
                    continue;
                }
                m = CONST.matcher(line);
                if (m.matches()) {
                    o.options.putIfAbsent(m.group(3), new Option(m.group(3), m.group(4).trim(), values(m.group(6)), false));
                    continue;
                }
                m = SWITCH.matcher(line);
                if (m.matches() && referenced.contains(m.group(3))) {
                    o.options.putIfAbsent(m.group(3), new Option(m.group(3), m.group(2) == null ? "true" : "false", List.of("true", "false"), true));
                }
            }
        }
        o.readLang(pack);
        if (file != null && Files.isRegularFile(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) {
                    o.chosen.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        }
        return o;
    }

    private static List<String> values(String list) {
        List<String> values = new ArrayList<>();
        if (list != null) {
            for (String v : list.trim().split("\\s+")) {
                if (!v.isEmpty()) {
                    values.add(v);
                }
            }
        }
        return values;
    }

    private void readLang(ShaderPack pack) throws IOException {
        String locale = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        List<String> names = pack.list("lang");
        String base = null;
        String local = null;
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("en_us.lang")) {
                base = name;
            }
            if (lower.equals(locale.toLowerCase(Locale.ROOT) + ".lang")) {
                local = name;
            }
        }
        for (String name : new String[] {base, local}) {
            if (name == null) {
                continue;
            }
            String text = pack.raw("lang/" + name);
            if (text == null) {
                continue;
            }
            for (String line : text.split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) {
                    this.lang.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        }
    }

    public String value(String name) {
        Option o = this.options.get(name);
        String v = this.chosen.get(name);
        return v != null ? v : o == null ? "" : o.initial;
    }

    public void set(String name, String value) {
        Option o = this.options.get(name);
        if (o != null && o.initial.equals(value)) {
            this.chosen.remove(name);
        } else {
            this.chosen.put(name, value);
        }
    }

    public void save() throws IOException {
        if (this.file == null) {
            return;
        }
        StringBuilder out = new StringBuilder();
        for (var e : this.chosen.entrySet()) {
            out.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        Files.writeString(this.file, out, StandardCharsets.UTF_8);
    }

    // the file with chosen values written into the option lines
    public String apply(String text) {
        if (this.chosen.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 64);
        for (String line : text.split("\n", -1)) {
            out.append(this.rewrite(line)).append('\n');
        }
        out.setLength(out.length() - 1);
        return out.toString();
    }

    private String rewrite(String line) {
        if (!line.contains("define") && !line.contains("const")) {
            return line;
        }
        Matcher m = VALUE.matcher(line);
        if (m.matches()) {
            String v = this.chosen.get(m.group(2));
            return v == null ? line : m.group(1) + "#define " + m.group(2) + " " + v + m.group(4);
        }
        m = CONST.matcher(line);
        if (m.matches()) {
            String v = this.chosen.get(m.group(3));
            return v == null ? line : m.group(1) + "const " + m.group(2) + " " + m.group(3) + " = " + v + ";" + m.group(5);
        }
        m = SWITCH.matcher(line);
        if (m.matches()) {
            String v = this.chosen.get(m.group(3));
            if (v == null) {
                return line;
            }
            String tail = m.group(4) == null ? "" : " " + m.group(4);
            return m.group(1) + (v.equals("true") ? "" : "//") + "#define " + m.group(3) + tail;
        }
        return line;
    }

    public String label(String key, String fallback) {
        return this.lang.getOrDefault(key, fallback);
    }
}
