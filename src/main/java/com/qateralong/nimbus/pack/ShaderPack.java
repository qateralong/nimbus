package com.qateralong.nimbus.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// A shader pack in OptiFine layout, from a zip or a folder: files under shaders/, programs per dimension folder
// (world0, world-1, world1) with the shaders/ root as fallback, #include resolved against shaders/ or the including file.
public final class ShaderPack implements AutoCloseable {
    private final String name;
    private final Map<String, String> files = new HashMap<>();
    private final ZipFile zip;
    private final Path folder;

    public ShaderPack(Path path) throws IOException {
        this.name = path.getFileName().toString();
        if (Files.isDirectory(path)) {
            this.zip = null;
            this.folder = path;
        } else {
            this.zip = new ZipFile(path.toFile());
            this.folder = null;
        }
    }

    public String name() {
        return this.name;
    }

    public PackOptions options;

    // text a shader can include or define options in (shaders.settings, .glsl, .vsh, ...)
    public static boolean isSource(String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        for (String ext : new String[] {".properties", ".lang", ".png", ".jpg", ".jpeg", ".bin", ".dat", ".json", ".mcmeta", ".txt", ".md", ".zip"}) {
            if (p.endsWith(ext)) {
                return false;
            }
        }
        return true;
    }
    private final Map<String, String> applied = new HashMap<>();

    // a file with the chosen option values written in (shader sources only)
    public String read(String path) throws IOException {
        String key = normalize(path);
        if (this.options == null || !isSource(key)) {
            return this.raw(key);
        }
        if (this.applied.containsKey(key)) {
            return this.applied.get(key);
        }
        String text = this.raw(key);
        if (text != null) {
            text = this.options.apply(text);
        }
        this.applied.put(key, text);
        return text;
    }

    // path relative to shaders/, with forward slashes and no leading slash; null when missing
    public String raw(String path) throws IOException {
        String key = normalize(path);
        if (this.files.containsKey(key)) {
            return this.files.get(key);
        }
        String text = null;
        if (this.zip != null) {
            ZipEntry entry = this.zip.getEntry("shaders/" + key);
            if (entry != null) {
                try (InputStream in = this.zip.getInputStream(entry)) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } else {
            Path file = this.folder.resolve("shaders").resolve(key);
            if (Files.isRegularFile(file)) {
                text = Files.readString(file, StandardCharsets.UTF_8);
            }
        }
        if (text != null) {
            text = text.replace("\r\n", "\n").replace('\r', '\n');
        }
        this.files.put(key, text);
        return text;
    }

    public List<String> list(String dir) throws IOException {
        String prefix = "shaders/" + (dir.isEmpty() ? "" : normalize(dir) + "/");
        TreeSet<String> names = new TreeSet<>();
        if (this.zip != null) {
            var entries = this.zip.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName();
                if (entry.startsWith(prefix) && entry.indexOf('/', prefix.length()) < 0 && entry.length() > prefix.length()) {
                    names.add(entry.substring(prefix.length()));
                }
            }
        } else {
            Path base = this.folder.resolve(prefix);
            if (Files.isDirectory(base)) {
                try (var stream = Files.list(base)) {
                    stream.filter(Files::isRegularFile).forEach(p -> names.add(p.getFileName().toString()));
                }
            }
        }
        return new ArrayList<>(names);
    }

    public byte[] bytes(String path) throws IOException {
        String key = normalize(path);
        if (this.zip != null) {
            ZipEntry entry = this.zip.getEntry("shaders/" + key);
            if (entry == null) {
                return null;
            }
            try (InputStream in = this.zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
        Path file = this.folder.resolve("shaders").resolve(key);
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    // every file under shaders/, paths relative to it
    public List<String> listAll() throws IOException {
        TreeSet<String> names = new TreeSet<>();
        if (this.zip != null) {
            var entries = this.zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith("shaders/")) {
                    names.add(entry.getName().substring(8));
                }
            }
        } else {
            Path base = this.folder.resolve("shaders");
            if (Files.isDirectory(base)) {
                try (var stream = Files.walk(base)) {
                    stream.filter(Files::isRegularFile).forEach(p -> names.add(base.relativize(p).toString().replace('\\', '/')));
                }
            }
        }
        return new ArrayList<>(names);
    }

    static String normalize(String path) {
        String[] parts = path.replace('\\', '/').split("/");
        ArrayList<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!out.isEmpty()) {
                    out.remove(out.size() - 1);
                }
                continue;
            }
            out.add(part);
        }
        return String.join("/", out);
    }

    // the program's source with every #include expanded in place; null when the file is missing
    public String expand(String path) throws IOException {
        String text = this.read(path);
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length() * 4);
        this.expand(normalize(path), text, out, new ArrayList<>());
        return out.toString();
    }

    private void expand(String path, String text, StringBuilder out, List<String> stack) throws IOException {
        if (stack.contains(path) || stack.size() > 64) {
            throw new IOException("include cycle at " + path);
        }
        stack.add(path);
        String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                String target = includeTarget(trimmed);
                if (target != null) {
                    String resolved = target.startsWith("/") ? normalize(target) : normalize(dir + "/" + target);
                    String included = this.read(resolved);
                    if (included == null) {
                        throw new IOException("missing include " + target + " in " + path);
                    }
                    this.expand(resolved, included, out, stack);
                    continue;
                }
            }
            out.append(line).append('\n');
        }
        stack.remove(stack.size() - 1);
    }

    private static String includeTarget(String line) {
        int open = line.indexOf('"');
        int close = open < 0 ? -1 : line.indexOf('"', open + 1);
        if (open < 0 || close < 0) {
            open = line.indexOf('<');
            close = open < 0 ? -1 : line.indexOf('>', open + 1);
        }
        return open < 0 || close < 0 ? null : line.substring(open + 1, close);
    }

    @Override
    public void close() throws IOException {
        if (this.zip != null) {
            this.zip.close();
        }
    }
}
