package com.qateralong.nimbus.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public final class OptionRegistry {
    private final List<ModOptions> mods = new ArrayList<>();

    public ModOptions mod(String modId) {
        for (ModOptions mod : this.mods) {
            if (mod.id().equals(modId)) {
                return mod;
            }
        }
        String name = FabricLoader.getInstance().getModContainer(modId).map(c -> c.getMetadata().getName()).orElse(modId);
        ModOptions mod = new ModOptions(modId, Component.literal(name));
        this.mods.add(mod);
        return mod;
    }

    public List<ModOptions> mods() {
        return Collections.unmodifiableList(this.mods);
    }
}
