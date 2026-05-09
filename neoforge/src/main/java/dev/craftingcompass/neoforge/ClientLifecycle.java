package dev.craftingcompass.neoforge;

import dev.craftingcompass.CraftingCompassConstants;
import dev.craftingcompass.list.CraftingListHolder;
import dev.craftingcompass.list.CraftingListStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = CraftingCompassConstants.MOD_ID, value = Dist.CLIENT)
public final class ClientLifecycle {

    private ClientLifecycle() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        CraftingListStorage.load(CraftingListHolder.get());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CraftingListStorage.saveNow(CraftingListHolder.get());
        CraftingListHolder.get().replaceAll(java.util.Map.of());
    }
}
