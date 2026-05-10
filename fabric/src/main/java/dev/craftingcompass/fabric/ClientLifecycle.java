package dev.craftingcompass.fabric;

import dev.craftingcompass.client.SidebarController;
import dev.craftingcompass.client.SidebarPanel;
import dev.craftingcompass.list.CraftingListHolder;
import dev.craftingcompass.list.CraftingListStorage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.util.Map;
import java.util.Set;

/**
 * Login: load the saved crafting list + completion marks from disk.
 * Logout: persist the current state, stop the worker thread, clear the panel.
 *
 * Mirror of the NeoForge ClientLifecycle, hooked through Fabric's
 * ClientPlayConnectionEvents instead of the NeoForge event bus.
 */
public final class ClientLifecycle {

    private ClientLifecycle() {}

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CraftingListStorage.load(CraftingListHolder.get(), SidebarPanel.INSTANCE::restore);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CraftingListStorage.saveNow(SidebarPanel.INSTANCE::snapshot);
            SidebarController.INSTANCE.shutdown();
            CraftingListHolder.get().replaceAll(Map.of());
            SidebarPanel.INSTANCE.restore(new CraftingListStorage.SaveData(
                    Map.of(), Set.of(), Set.of()));
        });
    }
}