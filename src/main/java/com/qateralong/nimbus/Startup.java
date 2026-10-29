package com.qateralong.nimbus;

import java.lang.management.ManagementFactory;

public final class Startup {
    private static boolean title;
    private static long reloadStart;
    private static long worldStart;

    private Startup() {
    }

    private static double sinceLaunch() {
        return (System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime()) / 1000.0;
    }

    public static void titleScreen() {
        if (!title) {
            title = true;
            Nimbus.LOGGER.info("Nimbus startup: title screen after {} s", String.format(java.util.Locale.ROOT, "%.2f", sinceLaunch()));
        }
    }

    public static void reloadBegin() {
        reloadStart = System.nanoTime();
    }

    public static void reloadEnd() {
        if (reloadStart != 0) {
            Nimbus.LOGGER.info("Nimbus startup: resource reload {} s", String.format(java.util.Locale.ROOT, "%.2f", (System.nanoTime() - reloadStart) / 1e9));
            reloadStart = 0;
        }
    }

    public static void joining() {
        worldStart = System.nanoTime();
    }

    public static void firstWorldFrame() {
        if (worldStart != 0) {
            Nimbus.LOGGER.info("Nimbus startup: world join to first frame {} s", String.format(java.util.Locale.ROOT, "%.2f", (System.nanoTime() - worldStart) / 1e9));
            worldStart = 0;
        }
    }
}
