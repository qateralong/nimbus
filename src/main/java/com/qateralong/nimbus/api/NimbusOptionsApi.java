package com.qateralong.nimbus.api;

import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NimbusOptionsApi {
    public static final String ENTRYPOINT = "nimbus:options";
    private static final Logger LOGGER = LoggerFactory.getLogger("nimbus");
    private static Consumer<OptionRegistry> builtin = registry -> {
    };
    private static OptionRegistry registry;

    private NimbusOptionsApi() {
    }

    public static void builtin(Consumer<OptionRegistry> register) {
        builtin = register;
        registry = null;
    }

    public static synchronized List<ModOptions> mods() {
        if (registry == null) {
            OptionRegistry built = new OptionRegistry();
            builtin.accept(built);
            for (var container : FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, NimbusOptionsEntrypoint.class)) {
                try {
                    container.getEntrypoint().register(built);
                } catch (Throwable t) {
                    LOGGER.error("Options of {} could not be registered", container.getProvider().getMetadata().getId(), t);
                }
            }
            registry = built;
        }
        return registry.mods();
    }
}
