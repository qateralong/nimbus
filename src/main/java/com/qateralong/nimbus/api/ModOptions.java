package com.qateralong.nimbus.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ModOptions {
    private final String id;
    private Component name;
    private ResourceLocation icon;
    private Runnable save = () -> {
    };
    private final List<OptionPage> pages = new ArrayList<>();

    ModOptions(String id, Component name) {
        this.id = id;
        this.name = name;
    }

    public ModOptions name(Component name) {
        this.name = name;
        return this;
    }

    public ModOptions icon(ResourceLocation icon) {
        this.icon = icon;
        return this;
    }

    public ModOptions onSave(Runnable save) {
        this.save = save;
        return this;
    }

    public OptionPage page(String id, Component name) {
        OptionPage page = new OptionPage(this, id, name);
        this.pages.add(page);
        return page;
    }

    public String id() {
        return this.id;
    }

    public Component name() {
        return this.name;
    }

    public ResourceLocation icon() {
        return this.icon;
    }

    public void save() {
        this.save.run();
    }

    public List<OptionPage> pages() {
        return Collections.unmodifiableList(this.pages);
    }
}
