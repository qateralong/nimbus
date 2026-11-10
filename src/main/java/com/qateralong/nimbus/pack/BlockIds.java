package com.qateralong.nimbus.pack;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

// block.properties: block.<id>=<block>[:<property>=<value>...] ... mapped onto block states. Terrain vertices carry the
// id (+1, 0 = unlisted) in the free high bytes of their light coordinates while a pack is on; mc_Entity reads it back.
public final class BlockIds {
    private static final ThreadLocal<int[]> CURRENT = ThreadLocal.withInitial(() -> new int[3]);
    // per-vertex ambient occlusion of the quad being written (separateAo: goes to the color's alpha instead of its rgb)
    private static final ThreadLocal<float[]> AO = new ThreadLocal<>();
    private static final ThreadLocal<int[]> AO_INDEX = ThreadLocal.withInitial(() -> new int[1]);
    public static volatile boolean separateAo;
    private static volatile Reference2IntOpenHashMap<BlockState> ids;
    // engine vertices keep a 9-bit index into this table of distinct (id, light emission) pairs (index 0: unlisted, dark);
    // an entry is (id + 1) | emission << 17
    private static volatile it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap indexOf = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
    private static volatile int[] table = {-1};
    // a pack with block ids is running: vertex writers look at the thread's current block
    public static volatile boolean on;

    private BlockIds() {
    }

    public static void load(ShaderPack pack, PackConfig config) throws java.io.IOException {
        String text = pack.read("block.properties");
        Reference2IntOpenHashMap<BlockState> map = new Reference2IntOpenHashMap<>();
        if (text != null) {
            String active = Preprocessor.run(text, new HashMap<>(config.macros));
            StringBuilder joined = new StringBuilder();
            for (String line : active.split("\n")) {
                String t = line.trim();
                if (t.endsWith("\\")) {
                    joined.append(t, 0, t.length() - 1).append(' ');
                    continue;
                }
                joined.append(t);
                parse(joined.toString(), map);
                joined.setLength(0);
            }
        }
        ids = map;
        it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap index = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        it.unimi.dsi.fastutil.ints.IntArrayList list = new it.unimi.dsi.fastutil.ints.IntArrayList();
        list.add(0);
        java.util.TreeSet<Integer> keys = new java.util.TreeSet<>();
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            int key = map.getInt(state) | state.getLightEmission() << 17;
            if (key != 0) {
                keys.add(key);
            }
        }
        for (int key : keys) {
            if (list.size() < 512) {
                index.put(key, list.size());
                list.add(key);
            }
        }
        indexOf = index;
        table = list.toIntArray();
        on = true;
        StringBuilder probe = new StringBuilder();
        for (String name : new String[] {"grass_block", "oak_leaves", "stone", "lava", "water", "short_grass"}) {
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(name));
            probe.append(name).append('=').append(map.getInt(block.defaultBlockState()) - 1).append(' ');
        }
        com.qateralong.nimbus.Nimbus.LOGGER.info("Nimbus shader pack block ids: {} states, {} distinct; {}", map.size(), list.size() - 1, probe);
    }

    private static void parse(String line, Reference2IntOpenHashMap<BlockState> map) {
        if (!line.startsWith("block.")) {
            return;
        }
        int eq = line.indexOf('=');
        if (eq < 0) {
            return;
        }
        int id;
        try {
            id = Integer.parseInt(line.substring(6, eq).trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (id < 0 || id > 65534) {
            return;
        }
        for (String token : line.substring(eq + 1).trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            String[] parts = token.split(":");
            String name;
            int first;
            if (parts.length > 1 && !parts[1].contains("=")) {
                name = parts[0] + ":" + parts[1];
                first = 2;
            } else {
                name = "minecraft:" + parts[0];
                first = 1;
            }
            ResourceLocation key = ResourceLocation.tryParse(name);
            if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.get(key);
            Map<String, String> wanted = new HashMap<>();
            for (int i = first; i < parts.length; i++) {
                int e = parts[i].indexOf('=');
                if (e > 0) {
                    wanted.put(parts[i].substring(0, e), parts[i].substring(e + 1));
                }
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (matches(state, wanted) && !map.containsKey(state)) {
                    map.put(state, id + 1);
                }
            }
        }
    }

    private static boolean matches(BlockState state, Map<String, String> wanted) {
        for (var e : wanted.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(e.getKey());
            if (property == null) {
                return false;
            }
            boolean any = false;
            for (String value : e.getValue().split(",")) {
                if (name(state, property).equals(value)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    public static boolean enabled() {
        return ids != null && Pipeline.active();
    }

    public static int rawId(BlockState state) {
        Reference2IntOpenHashMap<BlockState> map = ids;
        return map == null ? 0 : map.getInt(state);
    }

    // render thread, outside meshing: the EntityTags index the next vertices carry
    public static void entityTag(int tag) {
        int[] c = CURRENT.get();
        if (c[1] == 0) {
            c[0] = tag;
        }
    }

    public static void begin(BlockState state) {
        Reference2IntOpenHashMap<BlockState> map = ids;
        int[] c = CURRENT.get();
        c[0] = map == null ? 0 : map.getInt(state);
        c[1] = 1;
        c[2] = state.getLightEmission();
    }

    // table index of the block being meshed
    public static int index() {
        int[] c = CURRENT.get();
        int key = c[0] | c[2] << 17;
        return key == 0 ? 0 : indexOf.getOrDefault(key, 0);
    }

    public static int[] table() {
        return table;
    }

    public static boolean meshing() {
        return CURRENT.get()[1] != 0;
    }

    public static void end() {
        int[] c = CURRENT.get();
        c[0] = 0;
        c[1] = 0;
        c[2] = 0;
    }

    public static void ao(float[] brightness) {
        AO.set(brightness);
        AO_INDEX.get()[0] = 0;
    }

    public static void aoDone() {
        AO.remove();
    }

    // ARGB color with the pending vertex's occlusion as alpha
    public static int color(int color) {
        float[] ao = AO.get();
        if (ao == null) {
            return color;
        }
        int[] index = AO_INDEX.get();
        int i = index[0]++;
        if (i >= ao.length) {
            return color;
        }
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, ao[i])) * 255.0F);
        return color & 0x00FFFFFF | a << 24;
    }

    // light packed as vanilla does (block u in the low short, sky v in the high one), with the id's bytes on top of each
    public static int tag(int packedLight) {
        int id = CURRENT.get()[0];
        if (id == 0) {
            return packedLight;
        }
        return packedLight & 0x00FF00FF | (id & 0xFF) << 8 | (id >> 8 & 0xFF) << 24;
    }
}
