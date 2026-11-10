package com.qateralong.nimbus;

import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.server.IntegratedServer;

// Leaving a singleplayer world returns to the menu at once; the integrated server finishes saving on its own thread.
// Opening the same world again, or closing the game, waits for that save.
public final class FastQuit {
    private static final CopyOnWriteArrayList<IntegratedServer> SAVING = new CopyOnWriteArrayList<>();
    // the game is closing: its disconnect waits as vanilla does (System.exit follows)
    public static volatile boolean closing;

    private FastQuit() {
    }

    public static boolean leave(IntegratedServer server) {
        if (closing || !NimbusConfig.get().enabled || !NimbusConfig.get().fastQuit) {
            return false;
        }
        server.halt(false);
        SAVING.add(server);
        Nimbus.LOGGER.info("Nimbus fast quit: saving {} in the background", levelId(server));
        return true;
    }

    // levelId null: every world
    public static void await(String levelId) {
        for (IntegratedServer server : SAVING) {
            if (levelId != null && !levelId.equals(levelId(server))) {
                continue;
            }
            long start = System.nanoTime();
            while (!server.isShutdown()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            SAVING.remove(server);
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (ms > 0) {
                Nimbus.LOGGER.info("Nimbus fast quit: waited {} ms for {} to finish saving", ms, levelId(server));
            }
        }
        SAVING.removeIf(IntegratedServer::isShutdown);
    }

    private static String levelId(IntegratedServer server) {
        return ((com.qateralong.nimbus.mixin.ServerStorageAccessor) server).nimbus$storage().getLevelId();
    }
}
