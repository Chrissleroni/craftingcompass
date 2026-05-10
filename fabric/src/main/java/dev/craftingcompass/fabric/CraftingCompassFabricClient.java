package dev.craftingcompass.fabric;

import dev.craftingcompass.CraftingCompassConstants;
import dev.craftingcompass.platform.Platform;
import net.fabricmc.api.ClientModInitializer;

public final class CraftingCompassFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycle.register();
        ClientSetup.init();
        KeyHandler.register();
        SidebarRenderHook.register();

        System.out.println("[CraftingCompass] Loader: " + Platform.get().loaderName());
        System.out.println("[" + CraftingCompassConstants.MOD_ID + "] Fabric client initialized");
    }
}
