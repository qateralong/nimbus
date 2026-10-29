package com.qateralong.nimbus;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

final class Bench {
    private static final String MODE = System.getProperty("nimbus.bench");
    private static final int SETTLE = 300;
    private static final int PHASE = 300;
    private static final String[] PHASES = "storage".equals(System.getProperty("nimbus.bench"))
            ? new String[]{"off", "on", "nochest", "off", "on", "nochest"} : new String[]{"off", "on", "on70", "off", "on", "on70"};
    private static int phaseStart;
    private static int ticks;
    private static int phase = -1;
    private static long lastFrame;
    private static final List<Long> FRAMES = new ArrayList<>();
    private static final List<String> RESULTS = new ArrayList<>();
    private static boolean hooked;

    private Bench() {
    }

    private static int waiting;

    static void tick(Minecraft client) {
        if (MODE == null || "prod".equals(MODE)) {
            return;
        }
        if (client.player == null || client.getSingleplayerServer() == null) {
            if (++waiting % 200 == 0) {
                Nimbus.LOGGER.info("BENCH waiting on screen {}", client.screen == null ? "none" : client.screen.getClass().getName());
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "bench-wait.png", client.getMainRenderTarget(), message -> {
                });
            }
            return;
        }
        if (!hooked) {
            hooked = true;
            WorldRenderEvents.END.register(context -> frame());
        }
        ticks++;
        MinecraftServer server = client.getSingleplayerServer();
        if ("chests".equals(MODE)) {
            chests(client, server);
            return;
        }
        if ("mem".equals(MODE)) {
            if (ticks == 300) {
                memory(client);
            }
            return;
        }
        if ("scale".equals(MODE)) {
            if (ticks == 20) {
                client.options.hideGui = true;
                build(server);
                NimbusConfig.get().renderScale = 70;
                NimbusConfig.get().sharpness = 60;
            }
            if (ticks == 200) {
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "scale-70.png", client.getMainRenderTarget(), message -> {
                });
            }
            if (ticks == 220) {
                NimbusConfig.get().renderScale = 100;
                NimbusConfig.get().sharpness = 0;
                client.stop();
            }
            return;
        }
        if ("screen".equals(MODE)) {
            String[] tabs = {"general", "quality", "particles", "entities", "performance"};
            if (ticks == 40) {
                client.options.guiScale().set(3);
                client.resizeDisplay();
            }
            int index = (ticks - 40) / 30;
            if (ticks >= 40 && (ticks - 40) % 30 == 0 && index < tabs.length) {
                NimbusScreen.openTab(client, tabs[index]);
            }
            if (ticks >= 40 && (ticks - 40) % 30 == 20 && index < tabs.length) {
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "nimbus-" + tabs[index] + ".png", client.getMainRenderTarget(), message -> {
                });
            }
            if (ticks == 40 + tabs.length * 30 + 10) {
                client.stop();
            }
            return;
        }
        if (ticks == 60) {
            run(server, "tp @p 0 -57 -12 0 8");
        }
        if (ticks == 20) {
            client.options.framerateLimit().set(260);
            client.options.enableVsync().set(false);
            client.options.renderDistance().set(12);
            client.options.hideGui = true;
            build(server);
        }
        if (ticks > 40 && ticks % 5 == 0) {
            run(server, "particle minecraft:flame 0 -57 40 25 2 25 0 400 force");
            run(server, "particle minecraft:flame 0 -57 -40 25 2 25 0 400 force");
            run(server, "particle minecraft:campfire_cosy_smoke 0 -55 120 30 3 10 0 60 force");
        }
        if (phase >= 0 && phase < 3 && ticks == SETTLE + phase * PHASE + PHASE / 2) {
            net.minecraft.client.Screenshot.grab(client.gameDirectory, "bench-" + MODE + "-" + PHASES[phase] + ".png", client.getMainRenderTarget(), message -> {
            });
        }
        int start = SETTLE;
        if (ticks >= start && (ticks - start) % PHASE == 0) {
            if (phase >= 0) {
                finish(PHASES[phase]);
            }
            phase++;
            if (phase >= PHASES.length) {
                report(client);
                return;
            }
            NimbusConfig.get().enabled = !PHASES[phase].equals("off");
            NimbusConfig.get().renderScale = PHASES[phase].equals("on70") ? 70 : 100;
            NimbusConfig.get().chestModels = !PHASES[phase].equals("nochest");
            Chests.reset();
            Cull.reload();
            client.levelRenderer.allChanged();
            phaseStart = ticks;
            Cull.reload();
            FRAMES.clear();
            lastFrame = 0;
        }
    }

    private static void frame() {
        if (phase < 0 || phase >= PHASES.length || ticks - phaseStart < 60) {
            lastFrame = 0;
            return;
        }
        long now = System.nanoTime();
        if (lastFrame != 0) {
            FRAMES.add(now - lastFrame);
        }
        lastFrame = now;
    }

    private static void finish(String name) {
        if (FRAMES.size() < 10) {
            RESULTS.add(name + ": too few frames");
            return;
        }
        long[] times = FRAMES.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(times);
        double total = Arrays.stream(times).sum() / 1e9;
        double avg = times.length / total;
        int count = Math.max(1, times.length / 100);
        double worst = 0;
        for (int i = times.length - count; i < times.length; i++) {
            worst += times[i];
        }
        double low = count / (worst / 1e9);
        double median = 1e9 / times[times.length / 2];
        String line = String.format(Locale.ROOT, "%s: avg %.1f fps, median %.1f, 1%% low %.1f, frames %d | %s", name, avg, median, low, times.length, Cull.last);
        RESULTS.add(line);
        Nimbus.LOGGER.info("BENCH {}", line);
    }

    private static void shot(Minecraft client, String name) {
        net.minecraft.client.Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), message -> {
        });
    }

    private static void lid(MinecraftServer server, int x, int z, int open) {
        server.execute(() -> {
            var level = server.overworld();
            var pos = new net.minecraft.core.BlockPos(x, -60, z);
            level.blockEvent(pos, level.getBlockState(pos).getBlock(), 1, open);
        });
    }

    private static void chests(Minecraft client, MinecraftServer server) {
        switch (ticks) {
            case 20 -> {
                client.options.hideGui = true;
                for (String command : List.of("time set 6000", "weather clear", "gamerule doDaylightCycle false", "fill -12 -60 -4 12 -50 12 air",
                        "setblock -8 -60 6 chest[facing=north]", "setblock -6 -60 6 chest[facing=east]", "setblock -4 -60 6 chest[facing=south]",
                        "setblock -2 -60 6 chest[facing=west]", "setblock 1 -60 6 chest[facing=north,type=left]", "setblock 2 -60 6 chest[facing=north,type=right]",
                        "setblock 5 -60 6 trapped_chest[facing=north]", "setblock 7 -60 6 ender_chest[facing=north]",
                        "setblock 4 -60 10 chest[facing=south,type=left]", "setblock 3 -60 10 chest[facing=south,type=right]",
                        "tp @p 1.5 -57.5 0.5 0 30")) {
                    run(server, command);
                }
            }
            case 120 -> shot(client, "chests-closed");
            case 125 -> {
                lid(server, -8, 6, 1);
                lid(server, 1, 6, 1);
                lid(server, 2, 6, 1);
                lid(server, 7, 6, 1);
            }
            case 140 -> shot(client, "chests-open");
            case 160 -> {
                lid(server, -8, 6, 0);
                lid(server, 1, 6, 0);
                lid(server, 2, 6, 0);
                lid(server, 7, 6, 0);
            }
            case 200 -> shot(client, "chests-closed-again");
            case 205 -> {
                NimbusConfig.get().chestModels = false;
                Chests.reset();
                client.levelRenderer.allChanged();
            }
            case 260 -> shot(client, "chests-vanilla");
            case 270 -> {
                NimbusConfig.get().chestModels = true;
                client.stop();
            }
            default -> {
            }
        }
    }

    private static long used() {
        for (int i = 0; i < 4; i++) {
            System.gc();
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
            }
        }
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object setUnchecked(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.state.properties.Property property, Object value) {
        return state.setValue(property, (Comparable) value);
    }

    private static void memory(Minecraft client) {
        StringBuilder out = new StringBuilder();
        try {
            long base = used();
            out.append("heap after load ").append(base).append(" MB\n");
            int states = 0;
            long cells = 0;
            java.lang.reflect.Field neighbours = net.minecraft.world.level.block.state.StateHolder.class.getDeclaredField("neighbours");
            neighbours.setAccessible(true);
            java.util.List<Object> holders = new ArrayList<>();
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    states++;
                    Object table = neighbours.get(state);
                    if (table instanceof com.google.common.collect.Table<?, ?, ?> t) {
                        cells += t.size();
                    }
                    holders.add(state);
                }
            }
            out.append("block states ").append(states).append(", neighbour cells ").append(cells).append("\n");
            long checks = 0;
            long wrong = 0;
            for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                java.util.Map<java.util.Map<?, ?>, Object> byValues = new java.util.HashMap<>();
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    byValues.put(new java.util.HashMap<>(state.getValues()), state);
                }
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    for (var property : state.getProperties()) {
                        for (Object value : property.getPossibleValues()) {
                            java.util.Map<Object, Object> wanted = new java.util.HashMap<>(state.getValues());
                            wanted.put(property, value);
                            Object expected = byValues.get(wanted);
                            Object actual = setUnchecked(state, property, value);
                            checks++;
                            if (expected != actual) {
                                wrong++;
                            }
                        }
                    }
                }
            }
            out.append("setValue checks ").append(checks).append(", wrong ").append(wrong).append("\n");
            java.lang.reflect.Field registry = net.minecraft.client.resources.model.ModelManager.class.getDeclaredField("bakedRegistry");
            registry.setAccessible(true);
            java.util.Map<?, ?> models = (java.util.Map<?, ?>) registry.get(client.getModelManager());
            java.util.Set<net.minecraft.client.renderer.block.model.BakedQuad> quads = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42);
            for (Object model : models.values()) {
                var baked = (net.minecraft.client.resources.model.BakedModel) model;
                for (var side : net.minecraft.core.Direction.values()) {
                    quads.addAll(baked.getQuads(null, side, random));
                }
                quads.addAll(baked.getQuads(null, null, random));
            }
            java.util.Set<java.nio.IntBuffer> unique = new java.util.HashSet<>();
            long bytes = 0;
            java.util.Set<int[]> arrays = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (var quad : quads) {
                int[] vertices = quad.getVertices();
                if (arrays.add(vertices)) {
                    bytes += 16 + vertices.length * 4L;
                    unique.add(java.nio.IntBuffer.wrap(vertices));
                }
            }
            out.append("models ").append(models.size()).append(", quads ").append(quads.size()).append(", vertex arrays ").append(arrays.size())
                    .append(" (").append(bytes / 1048576).append(" MB), unique contents ").append(unique.size()).append(", deduped while baking ").append(QuadDedup.saved).append("\n");
            for (Object state : holders) {
                neighbours.set(state, null);
            }
            long without = used();
            out.append("heap without neighbour tables ").append(without).append(" MB -> tables ").append(base - without).append(" MB\n");
        } catch (Throwable t) {
            out.append("failed: ").append(t);
        }
        Nimbus.LOGGER.info("BENCH MEM\n{}", out);
        try {
            Files.writeString(client.gameDirectory.toPath().resolve("bench-mem.txt"), out.toString());
        } catch (Exception ignored) {
        }
        client.stop();
    }

    private static void report(Minecraft client) {
        String text = "mode " + MODE + "\n" + String.join("\n", RESULTS) + "\n";
        try {
            Files.writeString(client.gameDirectory.toPath().resolve("bench-" + MODE + ".txt"), text);
        } catch (Exception e) {
            Nimbus.LOGGER.warn("bench write failed", e);
        }
        Nimbus.LOGGER.info("BENCH DONE\n{}", text);
        NimbusConfig.get().enabled = true;
        NimbusConfig.get().renderScale = 100;
        client.stop();
    }

    private static void run(MinecraftServer server, String command) {
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    private static void build(MinecraftServer server) {
        if ("storage".equals(MODE)) {
            List<String> storage = new ArrayList<>(List.of("gamerule doDaylightCycle false", "time set 6000", "weather clear", "forceload add -64 -64 64 96",
                    "kill @e[type=!player]"));
            for (int z = -10; z <= 60; z += 10) {
                storage.add("fill -40 -60 " + z + " 40 -40 " + (z + 9) + " air");
            }
            for (int row = 0; row < 3; row++) {
                int z = 12 + row * 8;
                storage.add("fill -30 -60 " + z + " 30 -45 " + z + " chest[facing=north]");
            }
            storage.add("tp @p 0 -55 -2 0 10");
            for (String command : storage) {
                run(server, command);
            }
            return;
        }
        List<String> commands = new ArrayList<>(List.of(
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule randomTickSpeed 0",
                "gamerule doEntityDrops false", "time set 6000", "weather clear", "forceload add -64 -64 64 176", "kill @e[type=!player]"));
        for (int z = -60; z <= 160; z += 15) {
            commands.add("fill -48 -60 " + z + " 48 -36 " + Math.min(160, z + 12) + " air");
        }
        commands.add("fill -48 -61 -60 48 -61 160 grass_block");
        for (int z = 8; z <= 92; z += 4) {
            commands.add("fill -40 -60 " + z + " 40 -60 " + z
                    + " oak_sign{front_text:{messages:['\"Nimbus\"','\"bench\"','\"line three\"','\"" + z + "\"']}}");
        }
        commands.add("fill -40 -60 96 40 -60 96 chest");
        commands.add("fill -44 -60 98 44 -51 98 stone_bricks");
        for (int x = -40; x <= 40; x += 2) {
            commands.add("fill " + x + " -60 104 " + x + " -60 140 chest");
        }
        commands.add("fill -44 -60 150 44 -44 150 stone");
        for (int x = -42; x <= 42; x += 3) {
            for (int y = -59; y <= -45; y += 3) {
                commands.add("summon item_frame " + x + " " + y + " 149 {Facing:2b,Item:{id:\"minecraft:diamond_sword\",count:1}}");
            }
        }
        for (int x = -40; x <= 40; x += 4) {
            for (int z = 10; z <= 90; z += 8) {
                commands.add("summon armor_stand " + x + " -60 " + z
                        + " {ArmorItems:[{id:\"iron_boots\",count:1},{id:\"iron_leggings\",count:1},{id:\"iron_chestplate\",count:1},{id:\"iron_helmet\",count:1}],HandItems:[{id:\"iron_sword\",count:1},{}],ShowArms:1b}");
            }
        }
        String[] items = {"diamond", "cobblestone", "oak_log", "iron_ingot", "bread", "torch", "arrow", "redstone"};
        for (int i = 0; i < 800; i++) {
            int x = -40 + (i * 37) % 81;
            int z = 6 + (i * 53) % 90;
            commands.add("summon item " + x + " -59 " + z + " {Item:{id:\"" + items[i % items.length] + "\",count:1},PickupDelay:32767,Age:-32768}");
        }
        for (int i = 0; i < 160; i++) {
            int x = -40 + (i * 13) % 81;
            int z = 100 + (i * 7) % 45;
            commands.add("summon husk " + x + " -60 " + z
                    + " {NoAI:1b,PersistenceRequired:1b,Silent:1b,ArmorItems:[{},{},{id:\"iron_chestplate\",count:1},{id:\"golden_helmet\",count:1}],HandItems:[{id:\"iron_axe\",count:1},{}]}");
        }
        commands.add("tp @p 0 -57 -12 0 8");
        for (String command : commands) {
            run(server, command);
        }
    }
}
