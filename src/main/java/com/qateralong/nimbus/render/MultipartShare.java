package com.qateralong.nimbus.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.renderer.block.model.multipart.Condition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

// And/Or conditions of multipart models: one predicate per distinct list of (already shared) part predicates, tested
// with a plain loop instead of a stream per call. Same order and short-circuiting as vanilla.
public final class MultipartShare {
    private static final Map<List<Predicate<BlockState>>, Predicate<BlockState>> ALL = new ConcurrentHashMap<>();
    private static final Map<List<Predicate<BlockState>>, Predicate<BlockState>> ANY = new ConcurrentHashMap<>();

    private MultipartShare() {
    }

    public static Predicate<BlockState> all(Iterable<? extends Condition> conditions, StateDefinition<Block, BlockState> definition) {
        return ALL.computeIfAbsent(parts(conditions, definition), k -> {
            @SuppressWarnings("unchecked")
            Predicate<BlockState>[] all = k.toArray(new Predicate[0]);
            return state -> {
                for (Predicate<BlockState> p : all) {
                    if (!p.test(state)) {
                        return false;
                    }
                }
                return true;
            };
        });
    }

    public static Predicate<BlockState> any(Iterable<? extends Condition> conditions, StateDefinition<Block, BlockState> definition) {
        return ANY.computeIfAbsent(parts(conditions, definition), k -> {
            @SuppressWarnings("unchecked")
            Predicate<BlockState>[] any = k.toArray(new Predicate[0]);
            return state -> {
                for (Predicate<BlockState> p : any) {
                    if (p.test(state)) {
                        return true;
                    }
                }
                return false;
            };
        });
    }

    private static List<Predicate<BlockState>> parts(Iterable<? extends Condition> conditions, StateDefinition<Block, BlockState> definition) {
        List<Predicate<BlockState>> parts = new ArrayList<>();
        for (Condition condition : conditions) {
            parts.add(condition.getPredicate(definition));
        }
        return List.copyOf(parts);
    }
}
