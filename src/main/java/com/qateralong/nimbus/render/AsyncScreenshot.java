package com.qateralong.nimbus.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.qateralong.nimbus.Nimbus;
import com.qateralong.nimbus.mixin.NativeImageAccessor;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

// Screenshots without the hitch: vanilla reads the frame back from the GPU synchronously (the game waits for the GPU to
// finish), here the copy goes into a pixel buffer behind a fence and is picked up a frame or two later. Saving is async
// in vanilla already and stays the same.
public final class AsyncScreenshot {
    private record Pending(int buffer, long fence, int width, int height, File file, Consumer<Component> messages) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private AsyncScreenshot() {
    }

    // render thread; false when the fast path cannot be used
    public static boolean start(RenderTarget target, File file, Consumer<Component> messages) {
        if (!RenderSystem.isOnRenderThread()) {
            return false;
        }
        int width = target.width;
        int height = target.height;
        long size = (long) width * height * 4;
        int buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);
        GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, size, GL15.GL_STREAM_READ);
        RenderSystem.bindTexture(target.getColorTextureId());
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        long fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        PENDING.add(new Pending(buffer, fence, width, height, file, messages));
        return true;
    }

    // once per frame on the render thread
    public static void poll() {
        if (PENDING.isEmpty()) {
            return;
        }
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending shot = PENDING.get(i);
            int status = GL32.glClientWaitSync(shot.fence(), 0, 0L);
            if (status != GL32.GL_ALREADY_SIGNALED && status != GL32.GL_CONDITION_SATISFIED) {
                continue;
            }
            PENDING.remove(i);
            finish(shot);
        }
    }

    private static void finish(Pending shot) {
        GL32.glDeleteSync(shot.fence());
        int stride = shot.width() * 4;
        NativeImage image = new NativeImage(shot.width(), shot.height(), false);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, shot.buffer());
        ByteBuffer data = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0, (long) stride * shot.height(), GL30.GL_MAP_READ_BIT);
        if (data != null) {
            long source = MemoryUtil.memAddress(data);
            long target = ((NativeImageAccessor) (Object) image).nimbus$pixels();
            // GL rows go bottom-up
            for (int y = 0; y < shot.height(); y++) {
                MemoryUtil.memCopy(source + (long) (shot.height() - 1 - y) * stride, target + (long) y * stride, stride);
            }
            GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GL15.glDeleteBuffers(shot.buffer());
        if (data == null) {
            image.close();
            shot.messages().accept(Component.translatable("screenshot.failure", "GPU read failed"));
            return;
        }
        File file = shot.file();
        Util.ioPool().execute(() -> {
            try {
                long pixels = ((NativeImageAccessor) (Object) image).nimbus$pixels();
                long count = (long) shot.width() * shot.height();
                for (long p = 0; p < count; p++) {
                    long at = pixels + p * 4;
                    MemoryUtil.memPutInt(at, MemoryUtil.memGetInt(at) | 0xFF000000);
                }
                image.writeToFile(file);
                Component name = Component.literal(file.getName()).withStyle(ChatFormatting.UNDERLINE)
                        .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath())));
                shot.messages().accept(Component.translatable("screenshot.success", name));
            } catch (Exception e) {
                Nimbus.LOGGER.warn("Couldn't save screenshot", e);
                shot.messages().accept(Component.translatable("screenshot.failure", e.getMessage()));
            } finally {
                image.close();
            }
        });
    }
}
