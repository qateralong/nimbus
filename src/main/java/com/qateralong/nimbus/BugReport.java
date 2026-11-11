package com.qateralong.nimbus;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

// One key press packs a screenshot, the log, settings and an engine dump into nimbus-bugs/BugReport-<date>-N.zip.
public final class BugReport {
    public static KeyMapping key;
    private static volatile boolean busy;

    private BugReport() {
    }

    private static boolean hinted;

    public static void joined(Minecraft minecraft) {
        if (hinted || key == null || key.isUnbound() || !version("nimbus").contains("dev")) {
            return;
        }
        hinted = true;
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("nimbus.bug.hint", version("nimbus"), key.getTranslatedKeyMessage())
                        .withStyle(ChatFormatting.GRAY), false);
            }
        });
    }

    // Called for every key press, also inside screens, so GUI bugs can be reported too.
    public static void keyPressed(int keyCode, int scanCode, int action) {
        if (action == GLFW.GLFW_PRESS && key != null && !key.isUnbound() && key.matches(keyCode, scanCode)) {
            capture();
        }
    }

    public static void capture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (busy) {
            return;
        }
        busy = true;
        NativeImage image;
        String system;
        String info;
        String engine;
        try {
            image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
            system = system(minecraft);
            info = info(minecraft);
            engine = engine(minecraft);
        } catch (Throwable t) {
            busy = false;
            Nimbus.LOGGER.error("Nimbus bug report failed", t);
            return;
        }
        Nimbus.LOGGER.info("Nimbus bug report requested");
        Path folder = minecraft.gameDirectory.toPath().resolve("nimbus-bugs");
        Path log = minecraft.gameDirectory.toPath().resolve("logs").resolve("latest.log");
        Path config = FabricLoader.getInstance().getConfigDir().resolve("nimbus.json");
        Util.ioPool().execute(() -> {
            Path written = null;
            try {
                written = write(folder, image, system, info, engine, log, config);
            } catch (Throwable t) {
                Nimbus.LOGGER.error("Nimbus bug report failed", t);
            } finally {
                image.close();
                busy = false;
            }
            Path result = written;
            minecraft.execute(() -> notify(minecraft, result));
        });
    }

    private static long logOffset = -1;

    // Unchanged system info, settings and old log lines are not repeated: a report points to the one that has them.
    private static Path write(Path folder, NativeImage image, String system, String info, String engine, Path log, Path config) throws IOException {
        Files.createDirectories(folder);
        int number = next(folder);
        byte[] settings = Files.exists(config) ? Files.readAllBytes(config) : new byte[0];
        String hash = Integer.toHexString(system.hashCode() * 31 + java.util.Arrays.hashCode(settings));
        Path state = folder.resolve(".last-system");
        String[] last = Files.exists(state) ? Files.readString(state).split("\n") : new String[0];
        boolean same = last.length == 2 && last[1].equals(hash) && last[0].matches("\\d+") && find(folder, Integer.parseInt(last[0])) != null;
        StringBuilder header = new StringBuilder();
        if (same) {
            header.append("system, mods and nimbus.json: same as bug report ").append(last[0]).append('\n');
        }
        byte[] logPart = new byte[0];
        if (Files.exists(log)) {
            long size = Files.size(log);
            long from = logOffset >= 0 && logOffset <= size ? logOffset : Math.max(0, size - (512 << 10));
            if (logOffset >= 0 && logOffset <= size) {
                header.append("latest.log: only lines after the previous report\n");
            }
            try (var channel = java.nio.channels.FileChannel.open(log)) {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) (size - from));
                channel.read(buffer, from);
                logPart = buffer.array();
            }
            logOffset = size;
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path target = folder.resolve("BugReport-" + stamp + "-" + number + ".zip");
        Path temporary = folder.resolve("BugReport-" + number + ".part");
        try (OutputStream file = Files.newOutputStream(temporary); ZipOutputStream zip = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
            entry(zip, "screenshot.jpg", jpeg(image));
            entry(zip, "info.txt", (header + info).getBytes(StandardCharsets.UTF_8));
            entry(zip, "engine.txt", engine.getBytes(StandardCharsets.UTF_8));
            if (!same) {
                entry(zip, "system.txt", system.getBytes(StandardCharsets.UTF_8));
                entry(zip, "nimbus.json", settings);
            }
            if (logPart.length > 0) {
                entry(zip, "latest.log", logPart);
            }
        }
        Files.move(temporary, target);
        if (!same) {
            Files.writeString(state, number + "\n" + hash);
        }
        return target;
    }

    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("BugReport-.*-(\\d+)\\.zip");

    private static Path find(Path folder, int number) throws IOException {
        try (var files = Files.list(folder)) {
            return files.filter(f -> {
                var match = NAME.matcher(f.getFileName().toString());
                return match.matches() && Integer.parseInt(match.group(1)) == number;
            }).findFirst().orElse(null);
        }
    }

    private static int next(Path folder) throws IOException {
        try (var files = Files.list(folder)) {
            return files.map(f -> NAME.matcher(f.getFileName().toString())).filter(java.util.regex.Matcher::matches)
                    .mapToInt(m -> Integer.parseInt(m.group(1))).max().orElse(0) + 1;
        }
    }

    private static byte[] jpeg(NativeImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int abgr = image.getPixelRGBA(x, y);
                out.setRGB(x, y, (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16 & 0xFF));
            }
        }
        var writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(0.8F);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (var stream = javax.imageio.ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(stream);
            writer.write(null, new javax.imageio.IIOImage(out, null, null), params);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static void notify(Minecraft minecraft, Path written) {
        if (written == null) {
            SystemToast.addOrUpdate(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.translatable("nimbus.bug.title"),
                    Component.translatable("nimbus.bug.failed"));
            return;
        }
        String name = written.getFileName().toString().replace(".zip", "");
        SystemToast.addOrUpdate(minecraft.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.translatable("nimbus.bug.title"),
                Component.translatable("nimbus.bug.saved", name));
        if (minecraft.player != null) {
            Component link = Component.translatable("nimbus.bug.open").withStyle(style -> style.withUnderlined(true).withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, written.getParent().toString())));
            minecraft.player.displayClientMessage(Component.translatable("nimbus.bug.chat", name, link), false);
        }
    }

    private static String system(Minecraft minecraft) {
        StringBuilder out = new StringBuilder(16 * 1024);
        line(out, "nimbus %s, minecraft %s, loader %s", version("nimbus"), version("minecraft"), version("fabricloader"));
        line(out, "java %s (%s), os %s %s %s", System.getProperty("java.version"), System.getProperty("java.vendor"), System.getProperty("os.name"),
                System.getProperty("os.version"), System.getProperty("os.arch"));
        line(out, "cpu threads %d, max heap %d MB", Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() >> 20);
        line(out, "gpu %s / %s / %s", GlUtil.getVendor(), GlUtil.getRenderer(), GlUtil.getOpenGLVersion());
        var options = minecraft.options;
        line(out, "render distance %d, simulation %d, graphics %s, smooth lighting %s, biome blend %d, vsync %s, fps limit %d, clouds %s, entity distance %.2f",
                options.renderDistance().get(), options.simulationDistance().get(), options.graphicsMode().get(), options.ambientOcclusion().get(),
                options.biomeBlendRadius().get(), options.enableVsync().get(), options.framerateLimit().get(), options.cloudStatus().get(),
                options.entityDistanceScaling().get());
        line(out, "chunk updates %s, mipmaps %d, packs %s", options.prioritizeChunkUpdates().get(), options.mipmapLevels().get(), options.resourcePacks);
        out.append("\n== mods ==\n");
        FabricLoader.getInstance().getAllMods().stream().map(ModContainer::getMetadata)
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .forEach(meta -> out.append(meta.getId()).append(' ').append(meta.getVersion().getFriendlyString()).append('\n'));
        return out.toString();
    }

    private static String info(Minecraft minecraft) {
        StringBuilder out = new StringBuilder(4 * 1024);
        line(out, "time %s, nimbus %s", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), version("nimbus"));
        Runtime runtime = Runtime.getRuntime();
        var window = minecraft.getWindow();
        line(out, "fps %d, heap used %d MB, window %dx%d, gui scale %.1f, fullscreen %s", minecraft.getFps(), (runtime.totalMemory() - runtime.freeMemory()) >> 20,
                window.getWidth(), window.getHeight(), window.getGuiScale(), window.isFullscreen());
        var server = minecraft.getCurrentServer();
        line(out, "world: %s", minecraft.hasSingleplayerServer() ? "singleplayer" : server != null ? "server " + server.ip : "none");
        line(out, "screen: %s", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        if (minecraft.player != null && minecraft.level != null) {
            var player = minecraft.player;
            var camera = minecraft.gameRenderer.getMainCamera().getPosition();
            line(out, "dimension %s, player %.2f %.2f %.2f, camera %.2f %.2f %.2f, yaw %.1f pitch %.1f, flying %s, mode %s",
                    minecraft.level.dimension().location(), player.getX(), player.getY(), player.getZ(), camera.x, camera.y, camera.z, player.getYRot(),
                    player.getXRot(), player.getAbilities().flying, minecraft.gameMode == null ? "?" : minecraft.gameMode.getPlayerMode());
            HitResult hit = minecraft.hitResult;
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                line(out, "looking at block %s %s (%s)", block.getBlockPos().toShortString(), minecraft.level.getBlockState(block.getBlockPos()),
                        block.getDirection());
            } else if (hit instanceof EntityHitResult entity) {
                line(out, "looking at entity %s", entity.getEntity());
            }
            line(out, "main hand %s, off hand %s", player.getMainHandItem(), player.getOffhandItem());
        }
        out.append("\n== F3 ==\n");
        try {
            for (String text : ((com.qateralong.nimbus.mixin.DebugOverlayAccessor) minecraft.getDebugOverlay()).nimbus$game()) {
                out.append(text).append('\n');
            }
        } catch (Throwable t) {
            line(out, "unavailable: %s", t);
        }
        return out.toString();
    }

    private static String engine(Minecraft minecraft) {
        try {
            return com.qateralong.nimbus.render.Diagnostics.report(minecraft);
        } catch (Throwable t) {
            java.io.StringWriter text = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(text));
            return "engine dump failed\n" + text;
        }
    }

    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private static void line(StringBuilder out, String format, Object... args) {
        out.append(String.format(Locale.ROOT, format, args)).append('\n');
    }
}
