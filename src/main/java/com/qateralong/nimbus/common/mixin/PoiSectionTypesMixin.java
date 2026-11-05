package com.qateralong.nimbus.common.mixin;

import com.qateralong.nimbus.common.PoiSquare;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PoiSection.class)
public abstract class PoiSectionTypesMixin implements PoiSquare.Types {
    @Shadow
    @Final
    private Map<Holder<PoiType>, Set<PoiRecord>> byType;

    @Override
    public Map<Holder<PoiType>, Set<PoiRecord>> nimbus$byType() {
        return this.byType;
    }
}
