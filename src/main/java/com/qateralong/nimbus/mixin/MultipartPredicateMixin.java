package com.qateralong.nimbus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.renderer.block.model.multipart.KeyValueCondition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

// Every multipart model part asked for its own predicate object; the same block, key and value test the same thing.
@Mixin(KeyValueCondition.class)
public abstract class MultipartPredicateMixin {
    @Unique
    private static final Map<StateDefinition<Block, BlockState>, Map<String, Predicate<BlockState>>> NIMBUS$CACHE = new ConcurrentHashMap<>();

    @Shadow
    @Final
    private String key;

    @Shadow
    @Final
    private String value;

    @WrapMethod(method = "getPredicate")
    private Predicate<BlockState> nimbus$shared(StateDefinition<Block, BlockState> definition, Operation<Predicate<BlockState>> original) {
        Map<String, Predicate<BlockState>> perBlock = NIMBUS$CACHE.computeIfAbsent(definition, d -> new ConcurrentHashMap<>());
        String id = this.key + '=' + this.value;
        Predicate<BlockState> cached = perBlock.get(id);
        if (cached == null) {
            cached = original.call(definition);
            Predicate<BlockState> raced = perBlock.putIfAbsent(id, cached);
            if (raced != null) {
                cached = raced;
            }
        }
        return cached;
    }
}
