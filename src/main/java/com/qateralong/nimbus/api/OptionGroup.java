package com.qateralong.nimbus.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.Component;

public final class OptionGroup {
    private final OptionPage page;
    private final Component name;
    private final List<Option<?, ?>> options = new ArrayList<>();
    private boolean collapsed;

    OptionGroup(OptionPage page, Component name) {
        this.page = page;
        this.name = name;
    }

    public <O extends Option<?, ?>> O add(O option) {
        option.attach(this);
        this.options.add(option);
        return option;
    }

    public OptionGroup collapsed(boolean collapsed) {
        this.collapsed = collapsed;
        return this;
    }

    public boolean collapsedByDefault() {
        return this.collapsed;
    }

    public OptionPage page() {
        return this.page;
    }

    public Component name() {
        return this.name;
    }

    public List<Option<?, ?>> options() {
        return Collections.unmodifiableList(this.options);
    }
}
