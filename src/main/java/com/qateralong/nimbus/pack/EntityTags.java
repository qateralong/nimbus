package com.qateralong.nimbus.pack;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.HashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

// entity.properties / item.properties, and what is being rendered on the render thread: (entityId, blockEntityId,
// currentRenderedItemId) triples get a table index that vertices carry in their light coordinates (bit 15 marks it).
public final class EntityTags {
    public static final int SIZE = 256;
    public static final int MARK = 0x8000;
    private static Reference2IntOpenHashMap<EntityType<?>> entities = new Reference2IntOpenHashMap<>();
    private static Reference2IntOpenHashMap<Item> items = new Reference2IntOpenHashMap<>();
    private static final Long2IntOpenHashMap INDEX = new Long2IntOpenHashMap();
    private static final int[] TABLE = new int[SIZE * 3];
    private static int count;
    public static int version;
    private static int entity = -1;
    private static int blockEntity;
    private static int item = -1;
    private static final int[] STACK = new int[3 * 64];
    private static int depth;

    private EntityTags() {
    }

    public static void load(ShaderPack pack, PackConfig config) throws java.io.IOException {
        entities = new Reference2IntOpenHashMap<>();
        items = new Reference2IntOpenHashMap<>();
        entities.defaultReturnValue(-1);
        items.defaultReturnValue(-1);
        parse(pack.read("entity.properties"), "entity.", config, (key, id) -> {
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
                entities.putIfAbsent(BuiltInRegistries.ENTITY_TYPE.get(key), id);
            }
        });
        parse(pack.read("item.properties"), "item.", config, (key, id) -> {
            if (BuiltInRegistries.ITEM.containsKey(key)) {
                items.putIfAbsent(BuiltInRegistries.ITEM.get(key), id);
            }
        });
        reset();
    }

    private static void parse(String text, String prefix, PackConfig config, java.util.function.ObjIntConsumer<ResourceLocation> sink) {
        if (text == null) {
            return;
        }
        StringBuilder joined = new StringBuilder();
        for (String line : Preprocessor.run(text, new HashMap<>(config.macros)).split("\n")) {
            String t = line.trim();
            if (t.endsWith("\\")) {
                joined.append(t, 0, t.length() - 1).append(' ');
                continue;
            }
            joined.append(t);
            String l = joined.toString();
            joined.setLength(0);
            int eq = l.indexOf('=');
            if (!l.startsWith(prefix) || eq < 0) {
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(l.substring(prefix.length(), eq).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            for (String token : l.substring(eq + 1).trim().split("\\s+")) {
                ResourceLocation key = token.isEmpty() ? null : ResourceLocation.tryParse(token.contains(":") ? token : "minecraft:" + token);
                if (key != null) {
                    sink.accept(key, id);
                }
            }
        }
    }

    public static void reset() {
        INDEX.clear();
        count = 1;
        TABLE[0] = -1;
        TABLE[1] = 0;
        TABLE[2] = -1;
        version++;
        entity = -1;
        blockEntity = 0;
        item = -1;
        depth = 0;
    }

    // a new frame: nothing from the last one is still waiting to be drawn, so a full table can start over
    private static final boolean DEBUG = Boolean.getBoolean("nimbus.tagDebug");
    private static int frames;

    public static void frame() {
        if (DEBUG && ++frames % 300 == 0) {
            com.qateralong.nimbus.Nimbus.LOGGER.info("Nimbus tags: {} entries, entities {}, items {}, table {}", count, entities.size(), items.size(),
                    java.util.Arrays.toString(java.util.Arrays.copyOf(TABLE, count * 3)));
        }
        entity = -1;
        blockEntity = 0;
        item = -1;
        depth = 0;
        BlockIds.entityTag(0);
        if (count > SIZE - 32) {
            reset();
        }
    }

    public static boolean on() {
        Pipeline p = Pipeline.get();
        return BlockIds.on && p != null && p.inWorld();
    }

    public static int[] table() {
        return TABLE;
    }

    public static int itemId(ItemStack stack) {
        return stack.isEmpty() ? -1 : items.getInt(stack.getItem());
    }

    public static int lightOf(ItemStack stack) {
        return stack.getItem() instanceof BlockItem b ? b.getBlock().defaultBlockState().getLightEmission() : 0;
    }

    public static void pushEntity(Entity e) {
        push();
        entity = entities.getInt(e.getType());
        apply();
    }

    public static void pushBlockEntity(BlockEntity e) {
        push();
        int raw = BlockIds.rawId(e.getBlockState());
        blockEntity = raw == 0 ? -1 : raw - 1;
        apply();
    }

    public static void pushItem(ItemStack stack) {
        push();
        item = itemId(stack);
        apply();
    }

    private static void push() {
        if (depth + 3 <= STACK.length) {
            STACK[depth] = entity;
            STACK[depth + 1] = blockEntity;
            STACK[depth + 2] = item;
        }
        depth += 3;
    }

    public static void pop() {
        if (depth < 3) {
            return;
        }
        depth -= 3;
        if (depth + 3 <= STACK.length) {
            entity = STACK[depth];
            blockEntity = STACK[depth + 1];
            item = STACK[depth + 2];
        }
        apply();
    }

    private static void apply() {
        if (entity == -1 && blockEntity == 0 && item == -1) {
            BlockIds.entityTag(0);
            return;
        }
        long key = (entity & 0x1FFFFFL) << 42 | (blockEntity & 0x1FFFFFL) << 21 | item & 0x1FFFFFL;
        int index = INDEX.getOrDefault(key, -1);
        if (index < 0) {
            if (count >= SIZE) {
                BlockIds.entityTag(0);
                return;
            }
            index = count++;
            TABLE[index * 3] = entity;
            TABLE[index * 3 + 1] = blockEntity;
            TABLE[index * 3 + 2] = item;
            INDEX.put(key, index);
            version++;
        }
        BlockIds.entityTag(MARK | index);
    }
}
