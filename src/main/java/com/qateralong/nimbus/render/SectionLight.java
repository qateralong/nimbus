package com.qateralong.nimbus.render;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;

public final class SectionLight {
    public static final int SIZE = 18;
    private static final ThreadLocal<SectionLight> PENDING = new ThreadLocal<>();
    public final int x;
    public final int y;
    public final int z;
    public final BlockAndTintGetter level;

    private SectionLight(int x, int y, int z, BlockAndTintGetter level) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.level = level;
    }

    public static void begin(SectionPos section, BlockAndTintGetter level) {
        PENDING.set(new SectionLight(section.minBlockX() - 1, section.minBlockY() - 1, section.minBlockZ() - 1, level));
    }

    public static SectionLight take() {
        SectionLight pending = PENDING.get();
        PENDING.remove();
        return pending;
    }
}
