package dev.craftingcompass.fabric;

import dev.craftingcompass.client.SidebarPanel;
import dev.craftingcompass.list.CraftingListHolder;
import dev.craftingcompass.recipe.IngredientChooser;
import dev.craftingcompass.recipe.RecipeTreeResolver;
import dev.craftingcompass.recipe.StopSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;

public final class KeyHandler {

    private KeyHandler() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(KeyHandler::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        while (ClientSetup.ADD_TO_LIST.consumeClick()) {
            handlePress("tick", false);
        }
        while (ClientSetup.TOGGLE_SIDEBAR.consumeClick()) {
            cycleSidebar();
        }
    }

    /** Called from SidebarRenderHook for the in-screen path (so we can read shift state). */
    static void onAddPressedInScreen(boolean shift) {
        handlePress("screen", shift);
    }

    private static void cycleSidebar() {
        var panel = SidebarPanel.INSTANCE;
        if (!panel.isVisible()) {
            panel.setVisible(true);
            panel.setActiveTab(SidebarPanel.Tab.LIST);
            // System.out.println("[CraftingCompass] Sidebar -> LIST");
        } else if (panel.getActiveTab() == SidebarPanel.Tab.LIST) {
            panel.setActiveTab(SidebarPanel.Tab.INGREDIENTS);
            // System.out.println("[CraftingCompass] Sidebar -> INGREDIENTS");
        } else {
            panel.setVisible(false);
            // System.out.println("[CraftingCompass] Sidebar -> hidden");
        }
    }

    private static void handlePress(String source, boolean shift) {
        var runtime = dev.craftingcompass.fabric.jei.CraftingCompassJeiPlugin.runtime();

        if (runtime == null) {
            // System.out.println("[CraftingCompass] JEI runtime not available yet.");
            return;
        }

        var stack = runtime.getIngredientListOverlay()
                .getIngredientUnderMouse(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);

        if (stack == null || stack.isEmpty()) {
            // System.out.println("[CraftingCompass] Nothing hovered (" + source + ").");
            return;
        }

        int amount = shift ? Math.max(1, stack.getMaxStackSize()) : 1;
        CraftingListHolder.get().add(stack.getItem(), amount);
        String n = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // System.out.println("[CraftingCompass] Added " + amount + "x " + n + " to list (total: "
        //         + CraftingListHolder.get().quantity(stack.getItem()) + ")");

        var provider = dev.craftingcompass.fabric.jei.CraftingCompassJeiPlugin.provider();

        if (provider == null) {
            // System.out.println("[CraftingCompass] Recipe provider not ready.");
            return;
        }
        if (provider.totalRecipeCount() == 0) {
            // System.out.println("[CraftingCompass] Recipe index still building, try again in a moment.");
            return;
        }

        var resolverProvider = provider;
        var targetStack = stack;

        Thread t = new Thread(() -> {
            var resolver = new RecipeTreeResolver(
                    resolverProvider,
                    IngredientChooser.FIRST,
                    StopSet.defaultIntermediates(),
                    8
            );

            var tree = resolver.resolve(targetStack, targetStack.getCount());
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            var shopping = dev.craftingcompass.inventory.RequirementCalculator.compute(tree, player);

            //System.out.println("[CraftingCompass] === Shopping list for "
            //        + targetStack.getDisplayName().getString()
            //        + " x" + targetStack.getCount() + " ===");
            if (shopping.entries().isEmpty()) {
                // System.out.println("  (no recipes found)");
            } else {
                for (var entry : shopping.entries()) {
                    switch (entry) {
                        case dev.craftingcompass.inventory.ShoppingList.Entry.ItemEntry ie -> {
                            String name = BuiltInRegistries.ITEM.getKey(ie.item()).toString();
                            //System.out.println("  " + name
                            //        + ": need " + ie.needed()
                            //        + ", have " + ie.have()
                            //        + ", missing " + ie.missing());
                        }
                        case dev.craftingcompass.inventory.ShoppingList.Entry.TagEntry te -> {
                            //System.out.println("  #" + te.tag().location()
                            //        + ": need " + te.needed()
                            //        + " (tag — inventory check skipped)");
                        }
                    }
                }
            }
        }, "CraftingCompass-Resolver");
        t.setDaemon(true);
        t.start();
    }
}