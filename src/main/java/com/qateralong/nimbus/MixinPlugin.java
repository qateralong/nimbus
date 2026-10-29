package com.qateralong.nimbus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class MixinPlugin implements IMixinConfigPlugin {
    private boolean fastStates = true;
    private boolean dedupModels = true;

    @Override
    public void onLoad(String mixinPackage) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("nimbus.json");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("fastStates")) {
                    this.fastStates = json.get("fastStates").getAsBoolean();
                }
                if (json.has("dedupModels")) {
                    this.dedupModels = json.get("dedupModels").getAsBoolean();
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".StateHolderMixin")) {
            return this.fastStates && !FabricLoader.getInstance().isModLoaded("ferritecore");
        }
        if (mixinClassName.endsWith(".SodiumOcclusionMixin")) {
            return FabricLoader.getInstance().isModLoaded("sodium");
        }
        if (mixinClassName.endsWith(".BakedQuadMixin") || mixinClassName.endsWith(".ModelManagerMixin")) {
            return this.dedupModels;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
