package com.qateralong.nimbus.render;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

// Runs section builds on the shared background pool, but never more at once than BuildThreads allows.
public final class BuildExecutor implements Executor {
    private final Executor backing;
    private final ConcurrentLinkedQueue<Runnable> waiting = new ConcurrentLinkedQueue<>();
    private final AtomicInteger running = new AtomicInteger();

    public BuildExecutor(Executor backing) {
        this.backing = backing;
    }

    @Override
    public void execute(Runnable task) {
        this.waiting.add(task);
        this.drain();
    }

    private void drain() {
        while (!this.waiting.isEmpty()) {
            int now = this.running.get();
            if (now >= limit()) {
                return;
            }
            if (!this.running.compareAndSet(now, now + 1)) {
                continue;
            }
            Runnable task = this.waiting.poll();
            if (task == null) {
                this.running.decrementAndGet();
                continue;
            }
            this.backing.execute(() -> {
                try {
                    task.run();
                } finally {
                    this.running.decrementAndGet();
                    this.drain();
                }
            });
        }
    }

    private static int limit() {
        Minecraft minecraft = Minecraft.getInstance();
        int distance = minecraft.options == null ? 16 : minecraft.options.getEffectiveRenderDistance();
        return BuildThreads.active(Runtime.getRuntime().availableProcessors(), distance);
    }
}
