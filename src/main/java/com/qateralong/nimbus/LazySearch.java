package com.qateralong.nimbus;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.Util;

// A search tree that is built only once something needs it (the creative screen or the recipe book opens, or a search
// runs), instead of on every world join: big modpacks spent seconds of background CPU and tens of MB on trees nobody used
public final class LazySearch<T> extends CompletableFuture<T> {
    private Supplier<T> supplier;

    public LazySearch(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public void start() {
        Supplier<T> pending;
        synchronized (this) {
            pending = this.supplier;
            this.supplier = null;
        }
        if (pending != null && !this.isDone()) {
            CompletableFuture.supplyAsync(pending, Util.backgroundExecutor()).whenComplete((value, error) -> {
                if (error != null) {
                    this.completeExceptionally(error);
                } else {
                    this.complete(value);
                }
            });
        }
    }

    @Override
    public T join() {
        this.start();
        return super.join();
    }

    public interface Warm {
        void nimbus$warm();
    }

    public static void warm() {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection != null && connection.searchTrees() instanceof Warm warm) {
            warm.nimbus$warm();
        }
    }
}
