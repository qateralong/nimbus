package com.qateralong.nimbus.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class OptionPage {
    private final ModOptions mod;
    private final String id;
    private final Component name;
    private final List<OptionGroup> groups = new ArrayList<>();

    OptionPage(ModOptions mod, String id, Component name) {
        this.mod = mod;
        this.id = id;
        this.name = name;
    }

    public OptionGroup group(Component name) {
        OptionGroup group = new OptionGroup(this, name);
        this.groups.add(group);
        return group;
    }

    public OptionGroup group() {
        return this.group(null);
    }

    public ModOptions mod() {
        return this.mod;
    }

    public String id() {
        return this.id;
    }

    public Component name() {
        return this.name;
    }

    public List<OptionGroup> groups() {
        return Collections.unmodifiableList(this.groups);
    }
}
