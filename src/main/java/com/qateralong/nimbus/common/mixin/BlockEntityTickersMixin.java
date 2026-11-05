package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.CompactingIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Removed block entities left the ticker list one ArrayList shift each: unloading many at once was quadratic.
@Mixin(Level.class)
public abstract class BlockEntityTickersMixin {
    @Redirect(method = "tickBlockEntities", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<?> nimbus$compacting(List<?> list) {
        return list instanceof ArrayList<?> array ? new CompactingIterator<>(array) : list.iterator();
    }
}
