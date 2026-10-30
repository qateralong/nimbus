package com.qateralong.nimbus.api;

import net.minecraft.network.chat.CommonComponents;

public final class ToggleOption extends Option<Boolean, ToggleOption> {
    ToggleOption(String id) {
        super(id);
        this.formatter(value -> value != null && value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
}
