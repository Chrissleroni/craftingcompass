package dev.craftingcompass.config;

public class CraftingCompassConfig {
    public static volatile DecompositionProfile profile = DecompositionProfile.RAW_MATERIALS;
    public static volatile int sidebarMaxRows = 10;
    public static volatile boolean inventoryCheckEnabled = true;

    private CraftingCompassConfig() {}
}
