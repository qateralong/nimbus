package com.qateralong.nimbus.render;

import com.qateralong.nimbus.Nimbus;

// Frames over 25 ms go to the log with what the engine did in them, so a bug report shows what a stutter was made of.
public final class SlowFrames {
    private static final long SLOW = 25_000_000L;
    public static int uploads;
    public static int syncBuilds;
    public static int asyncBuilds;
    public static int repairs;
    private static long last;
    private static int inFrame;
    private static int logged;
    private static long windowStart;

    private SlowFrames() {
    }

    public static void endFrame() {
        long now = System.nanoTime();
        long frame = last == 0L ? 0L : now - last;
        last = now;
        int searches = Traversal.inFrame - inFrame;
        inFrame = Traversal.inFrame;
        if (now - windowStart > 10_000_000_000L) {
            windowStart = now;
            logged = 0;
        }
        // frames held back on purpose (background, minimized, AFK limits) are not stutters
        boolean limited = com.qateralong.nimbus.Idle.limit(Integer.MAX_VALUE) != Integer.MAX_VALUE;
        if (frame > SLOW && logged < 10 && !limited) {
            logged++;
            var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
            Nimbus.LOGGER.info("Nimbus slow frame {} ms: in-frame searches {}, uploads {}, sync builds {}, async builds {}, repairs {}, yaw {}, gc {}",
                    frame / 1_000_000L, searches, uploads, syncBuilds, asyncBuilds, repairs, (int) camera.getYRot(), gcCount());
        }
        uploads = 0;
        syncBuilds = 0;
        asyncBuilds = 0;
        repairs = 0;
    }

    private static long gcCount() {
        long count = 0;
        for (var bean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(0, bean.getCollectionCount());
        }
        return count;
    }
}
