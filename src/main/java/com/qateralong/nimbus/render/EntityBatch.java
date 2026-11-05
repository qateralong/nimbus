package com.qateralong.nimbus.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.qateralong.nimbus.Compat;
import com.qateralong.nimbus.NimbusConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public final class EntityBatch {
    private static final boolean IMMEDIATELY_FAST = FabricLoader.getInstance().isModLoaded("immediatelyfast");
    private static final int MAX_TYPES = 256;
    private static final Reference2ObjectOpenHashMap<RenderType, BufferBuilder> builders = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<RenderType, ByteBufferBuilder> buffers = new Reference2ObjectOpenHashMap<>();
    private static final ObjectArrayList<RenderType> order = new ObjectArrayList<>();
    private static final Reference2ObjectOpenHashMap<BufferBuilder, RenderType> types = new Reference2ObjectOpenHashMap<>();
    private static final ObjectArrayList<ByteBufferBuilder> pool = new ObjectArrayList<>();
    private static final Reference2BooleanOpenHashMap<RenderType> opaque = new Reference2BooleanOpenHashMap<>();
    private static MultiBufferSource.BufferSource target;
    private static boolean flushing;

    private EntityBatch() {
    }

    public static void begin() {
        NimbusConfig config = NimbusConfig.get();
        if (IMMEDIATELY_FAST || !config.enabled || !config.entityBatching || Compat.shadersOn() || !RenderSystem.isOnRenderThread()) {
            target = null;
            return;
        }
        target = Minecraft.getInstance().renderBuffers().bufferSource();
    }

    public static boolean active(MultiBufferSource.BufferSource source) {
        return target != null && source == target && !flushing;
    }

    public static BufferBuilder buffer(RenderType type) {
        BufferBuilder builder = builders.get(type);
        if (builder != null) {
            return builder;
        }
        if (order.size() >= MAX_TYPES) {
            return null;
        }
        ByteBufferBuilder buffer = pool.isEmpty() ? new ByteBufferBuilder(16384) : pool.pop();
        builder = new BufferBuilder(buffer, type.mode(), type.format());
        builders.put(type, builder);
        buffers.put(type, buffer);
        types.put(builder, type);
        order.add(type);
        return builder;
    }

    public static void end() {
        flush();
        target = null;
    }

    public static void flush() {
        if (order.isEmpty() || flushing) {
            return;
        }
        flushing = true;
        try {
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < order.size(); i++) {
                    RenderType type = order.get(i);
                    if (opaque(type) == (pass == 0)) {
                        draw(type);
                    }
                }
            }
        } finally {
            for (int i = 0; i < order.size(); i++) {
                ByteBufferBuilder buffer = buffers.get(order.get(i));
                buffer.discard();
                pool.add(buffer);
            }
            order.clear();
            types.clear();
            EntityInstances.discard();
            builders.clear();
            buffers.clear();
            flushing = false;
        }
    }

    // the batch's own builder for this layer, while the batch is collecting
    public static RenderType typeOf(Object consumer) {
        return target == null || flushing || !(consumer instanceof BufferBuilder) ? null : types.get(consumer);
    }

    private static void draw(RenderType type) {
        EntityInstances.draw(type);
        MeshData mesh = builders.get(type).build();
        if (mesh == null) {
            return;
        }
        if (type.sortOnUpload()) {
            mesh.sortQuads(buffers.get(type), RenderSystem.getVertexSorting());
        }
        type.draw(mesh);
    }

    private static boolean opaque(RenderType type) {
        if (opaque.containsKey(type)) {
            return opaque.getBoolean(type);
        }
        String text = type.toString();
        int open = text.indexOf('[');
        int close = text.indexOf(':');
        String name = open >= 0 && close > open ? text.substring(open + 1, close) : text;
        boolean result = name.startsWith("entity_solid") || name.startsWith("entity_cutout") || name.startsWith("entity_smooth_cutout")
                || name.startsWith("armor_cutout_no_cull");
        if (opaque.size() > 4096) {
            opaque.clear();
        }
        opaque.put(type, result);
        return result;
    }
}
