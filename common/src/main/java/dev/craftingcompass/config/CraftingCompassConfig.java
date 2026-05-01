package dev.craftingcompass.config;

public class CraftingCompassConfig {
    public static volatile DecompositionProfile profile = DecompositionProfile.RAW_MATERIALS;
    public static volatile boolean tintOutputSlots = true;
    public static volatile int sidebarMaxRows = 8;

    private CraftingCompassConfig() {}
}
