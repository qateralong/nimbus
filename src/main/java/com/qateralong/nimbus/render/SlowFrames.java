package com.qateralong.nimbus.render;

import com.qateralong.nimbus.Nimbus;

// Frames over 25 ms are counted and summed up in the log once a minute with the worst one and what the engine did in it;
// frames over 200 ms are logged at once. A bug report shows what stutters were made of without a line per frame.
public final class SlowFrames {
    private static final long SLOW = 25_000_000L;
    private static final long SEVERE = 200_000_000L;
    private static final long WINDOW = 60_000_000_000L;
    public static int uploads;
    public static int syncBuilds;
    public static int asyncBuilds;
    public static int repairs;
    private static long last;
    private static int inFrame;
    private static long windowStart;
    private static int slow;
    private static long worst;
    private static String worstDetail;
    public static final int HISTORY = 160;
    public static final long[] FRAMES = new long[HISTORY];
    public static int frameIndex;

    private SlowFrames() {
    }

    public static void endFrame() {
        AsyncScreenshot.poll();
        long now = System.nanoTime();
        long frame = last == 0L ? 0L : now - last;
        last = now;
        if (frame > 0L) {
            FRAMES[frameIndex] = frame;
            frameIndex = (frameIndex + 1) % HISTORY;
        }
        int searches = Traversal.inFrame - inFrame;
        inFrame = Traversal.inFrame;
        if (windowStart == 0L) {
            windowStart = now;
        }
        // frames held back on purpose (background, minimized, AFK limits) are not stutters
        boolean limited = com.qateralong.nimbus.Idle.limit(Integer.MAX_VALUE) != Integer.MAX_VALUE;
        if (frame > SLOW && !limited) {
            slow++;
            if (frame > worst || frame > SEVERE) {
                var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
                String detail = String.format("%d ms: in-frame searches %d, uploads %d, sync builds %d, async builds %d, repairs %d, yaw %d, gc %d",
                        frame / 1_000_000L, searches, uploads, syncBuilds, asyncBuilds, repairs, (int) camera.getYRot(), gcCount());
                if (frame > SEVERE) {
                    Nimbus.LOGGER.info("Nimbus long frame {}", detail);
                }
                if (frame > worst) {
                    worst = frame;
                    worstDetail = detail;
                }
            }
        }
        if (now - windowStart > WINDOW) {
            if (slow > 0) {
                Nimbus.LOGGER.info("Nimbus slow frames in the last minute: {} over 25 ms, worst {}", slow, worstDetail);
            }
            windowStart = now;
            slow = 0;
            worst = 0;
            worstDetail = null;
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
