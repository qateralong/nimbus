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
    private static final String[] PHASES = {"off", "on", "on70", "off", "on", "on70"};
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
        if (MODE == null) {
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
            Cull.reload();
            FRAMES.clear();
            lastFrame = 0;
        }
    }

    private static void frame() {
        if (phase < 0 || phase >= PHASES.length) {
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
