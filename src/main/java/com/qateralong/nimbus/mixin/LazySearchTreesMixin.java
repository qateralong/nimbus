package com.qateralong.nimbus.mixin;

import com.qateralong.nimbus.LazySearch;
import com.qateralong.nimbus.NimbusConfig;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SessionSearchTrees.class)
public abstract class LazySearchTreesMixin implements LazySearch.Warm {
    @Shadow
    private CompletableFuture<?> creativeByNameSearch;

    @Shadow
    private CompletableFuture<?> creativeByTagSearch;

    @Shadow
    private CompletableFuture<?> recipeSearch;

    // the three tree builders inside the update lambdas
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private <T> CompletableFuture<T> nimbus$lazy(Supplier<T> supplier, Executor executor) {
        if (!NimbusConfig.get().enabled || !NimbusConfig.get().lazySearch) {
            return CompletableFuture.supplyAsync(supplier, executor);
        }
        return new LazySearch<>(supplier);
    }

    @Override
    public void nimbus$warm() {
        for (CompletableFuture<?> future : new CompletableFuture<?>[]{this.creativeByNameSearch, this.creativeByTagSearch, this.recipeSearch}) {
            if (future instanceof LazySearch<?> lazy) {
                lazy.start();
            }
        }
    }
}
