package dev.craftingcompass.inventory;

import dev.craftingcompass.config.CraftingCompassConfig;
import net.minecraft.client.Minecraft;

/**
 * Cached snapshot of player inventory, refreshed at most once per second.
 * Read on the render thread by SidebarPanel; the cache means we don't pay
 * the scan cost (including shulker contents) on every frame.
 */
public final class InventoryCache {

    public static final InventoryCache INSTANCE = new InventoryCache();

    private static final long REFRESH_INTERVAL_MS = 500L;

    private volatile InventoryScanner.Snapshot lastSnapshot = new InventoryScanner.Snapshot(java.util.Map.of());
    private volatile long lastRefresh = 0;

    private InventoryCache() {}

    public InventoryScanner.Snapshot get() {
        if (!CraftingCompassConfig.inventoryCheckEnabled) {
            return new InventoryScanner.Snapshot(java.util.Map.of());
        }
        long now = System.currentTimeMillis();
        if (now - lastRefresh > REFRESH_INTERVAL_MS) {
            var player = Minecraft.getInstance().player;
            lastSnapshot = InventoryScanner.scanFull(player);
            lastRefresh = now;
        }
        return lastSnapshot;
    }

    /** Force the next get() to refresh. Useful after known inventory changes. */
    public void invalidate() { lastRefresh = 0; }
}