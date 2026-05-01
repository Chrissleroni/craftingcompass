package dev.craftingcompass.neoforge;

import dev.craftingcompass.platform.Platform;
import net.neoforged.fml.ModList;

public final class NeoForgePlatform implements Platform {
    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get() != null && ModList.get().isLoaded(modId);
    }
}
