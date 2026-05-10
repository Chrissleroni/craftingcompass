package dev.craftingcompass.fabric;

import dev.craftingcompass.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatform implements Platform {
    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}