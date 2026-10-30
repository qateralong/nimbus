package com.qateralong.nimbus.api;

import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;

public final class ActionOption extends Option<Void, ActionOption> {
    private Consumer<Screen> action = screen -> {
    };

    ActionOption(String id) {
        super(id);
    }

    public ActionOption action(Consumer<Screen> action) {
        this.action = action;
        return this;
    }

    public void run(Screen screen) {
        this.action.accept(screen);
    }
}
