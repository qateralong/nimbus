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

// One key press packs a screenshot, the log, settings and an engine dump into nimbus-bugs/баг N.zip.
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
        String info;
        String engine;
        try {
            image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
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
                written = write(folder, image, info, engine, log, config);
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

    private static Path write(Path folder, NativeImage image, String info, String engine, Path log, Path config) throws IOException {
        Files.createDirectories(folder);
        int number = 1;
        while (Files.exists(folder.resolve("баг " + number + ".zip"))) {
            number++;
        }
        Path target = folder.resolve("баг " + number + ".zip");
        Path temporary = folder.resolve("баг " + number + ".zip.part");
        try (OutputStream file = Files.newOutputStream(temporary); ZipOutputStream zip = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
            entry(zip, "screenshot.png", image.asByteArray());
            entry(zip, "info.txt", info.getBytes(StandardCharsets.UTF_8));
            entry(zip, "engine.txt", engine.getBytes(StandardCharsets.UTF_8));
            if (Files.exists(config)) {
                entry(zip, "nimbus.json", Files.readAllBytes(config));
            }
            if (Files.exists(log)) {
                entry(zip, "latest.log", Files.readAllBytes(log));
            }
        }
        Files.move(temporary, target);
        return target;
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
            Component link = Component.literal(written.getParent().toString()).withStyle(style -> style.withUnderlined(true).withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, written.getParent().toString())));
            minecraft.player.displayClientMessage(Component.translatable("nimbus.bug.chat", name, link), false);
        }
    }

    private static String info(Minecraft minecraft) {
        StringBuilder out = new StringBuilder(16 * 1024);
        line(out, "time %s", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        line(out, "nimbus %s, minecraft %s, loader %s", version("nimbus"), version("minecraft"), version("fabricloader"));
        line(out, "java %s (%s), os %s %s %s", System.getProperty("java.version"), System.getProperty("java.vendor"), System.getProperty("os.name"),
                System.getProperty("os.version"), System.getProperty("os.arch"));
        Runtime runtime = Runtime.getRuntime();
        line(out, "cpu threads %d, heap used %d MB of %d MB (max %d MB)", runtime.availableProcessors(), (runtime.totalMemory() - runtime.freeMemory()) >> 20,
                runtime.totalMemory() >> 20, runtime.maxMemory() >> 20);
        line(out, "gpu %s / %s / %s", GlUtil.getVendor(), GlUtil.getRenderer(), GlUtil.getOpenGLVersion());
        var window = minecraft.getWindow();
        line(out, "window %dx%d, gui scale %.1f, fullscreen %s, fps %d", window.getWidth(), window.getHeight(), window.getGuiScale(), window.isFullscreen(),
                minecraft.getFps());
        var options = minecraft.options;
        line(out, "render distance %d, simulation %d, graphics %s, smooth lighting %s, biome blend %d, vsync %s, fps limit %d, clouds %s, entity distance %.2f",
                options.renderDistance().get(), options.simulationDistance().get(), options.graphicsMode().get(), options.ambientOcclusion().get(),
                options.biomeBlendRadius().get(), options.enableVsync().get(), options.framerateLimit().get(), options.cloudStatus().get(),
                options.entityDistanceScaling().get());
        line(out, "chunk updates %s, mipmaps %d, packs %s", options.prioritizeChunkUpdates().get(), options.mipmapLevels().get(), options.resourcePacks);
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
            var overlay = (com.qateralong.nimbus.mixin.DebugOverlayAccessor) minecraft.getDebugOverlay();
            for (String text : overlay.nimbus$game()) {
                out.append(text).append('\n');
            }
            out.append('\n');
            for (String text : overlay.nimbus$system()) {
                out.append(text).append('\n');
            }
        } catch (Throwable t) {
            line(out, "unavailable: %s", t);
        }
        out.append("\n== mods ==\n");
        FabricLoader.getInstance().getAllMods().stream().map(ModContainer::getMetadata)
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .forEach(meta -> out.append(meta.getId()).append(' ').append(meta.getVersion().getFriendlyString()).append('\n'));
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
