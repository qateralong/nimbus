package com.qateralong.nimbus.mixin;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.qateralong.nimbus.FastStates;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Map;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StateHolder.class)
public abstract class StateHolderMixin<O, S> {
    @Shadow
    @Final
    private Reference2ObjectArrayMap<Property<?>, Comparable<?>> values;

    @Shadow
    private Table<Property<?>, Comparable<?>, S> neighbours;

    @Unique
    private FastStates nimbus$fast;

    @Unique
    private int nimbus$index;

    @Inject(method = "populateNeighbours", at = @At("HEAD"), cancellable = true)
    private void nimbus$populate(Map<Map<Property<?>, Comparable<?>>, S> states, CallbackInfo ci) {
        if (this.neighbours != null) {
            return;
        }
        FastStates fast = FastStates.of(states);
        int index = fast == null ? -1 : fast.index(this.values);
        if (index < 0) {
            return;
        }
        this.nimbus$fast = fast;
        this.nimbus$index = index;
        this.neighbours = ImmutableTable.of();
        ci.cancel();
    }

    @SuppressWarnings("unchecked")
    @Redirect(method = {"setValue", "trySetValue"}, at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Table;get(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", remap = false))
    private Object nimbus$neighbour(Table<?, ?, ?> table, Object property, Object value) {
        FastStates fast = this.nimbus$fast;
        return fast != null ? fast.with(this.nimbus$index, property, value) : table.get(property, value);
    }
}
