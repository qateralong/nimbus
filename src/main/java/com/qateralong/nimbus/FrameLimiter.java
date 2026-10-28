package com.qateralong.nimbus;

import java.util.ArrayDeque;
import org.lwjgl.opengl.GL32;

public final class FrameLimiter {
    private static final ArrayDeque<Long> FENCES = new ArrayDeque<>();

    private FrameLimiter() {
    }

    public static void afterSwap() {
        NimbusConfig config = NimbusConfig.get();
        int limit = config.enabled ? config.renderAhead : 0;
        if (limit <= 0) {
            while (!FENCES.isEmpty()) {
                GL32.glDeleteSync(FENCES.poll());
            }
            return;
        }
        FENCES.add(GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));
        while (FENCES.size() > limit) {
            long fence = FENCES.poll();
            GL32.glClientWaitSync(fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
            GL32.glDeleteSync(fence);
        }
    }
}
