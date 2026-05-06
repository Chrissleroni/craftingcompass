package dev.craftingcompass.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

public final class KeyHandler {

    public KeyHandler() {}

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        while (ClientSetup.ADD_TO_LIST.consumeClick()) {
            handlePress("tick");
        }
    }

    @SubscribeEvent
    public void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        var key = InputConstants.Type.KEYSYM.getOrCreate(event.getKeyCode());
        if (ClientSetup.ADD_TO_LIST.isActiveAndMatches(key)) {
            handlePress("screen:" + event.getScreen().getClass().getSimpleName());
        }
    }

    private static void handlePress(String source) {
        var runtime = dev.craftingcompass.neoforge.jei.CraftingCompassJeiPlugin.runtime();
        if (runtime == null) {
            System.out.println("[CraftingCompass] JEI runtime not available yet.");
            return;
        }

        var stack = runtime.getIngredientListOverlay()
                .getIngredientUnderMouse(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);

        if (stack == null || stack.isEmpty()) {
            System.out.println("[CraftingCompass] Nothing hovered (" + source + ").");
            return;
        }

        var provider = dev.craftingcompass.neoforge.jei.CraftingCompassJeiPlugin.provider();
        if (provider == null) {
            System.out.println("[CraftingCompass] Recipe provider not ready.");
            return;
        }

        if (provider.totalRecipeCount() == 0) {
            System.out.println("[CraftingCompass] Recipe index still building, try again in a moment.");
            return;
        }

        var resolverProvider = provider;
        var targetStack = stack;

        Thread t = new Thread(() -> {
            var resolver = new dev.craftingcompass.recipe.RecipeTreeResolver(
                    resolverProvider,
                    dev.craftingcompass.recipe.IngredientChooser.FIRST,
                    dev.craftingcompass.recipe.StopSet.defaultIntermediates(),
                    8
            );

            var tree = resolver.resolve(targetStack, targetStack.getCount());
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null) return;

            var shopping = dev.craftingcompass.inventory.RequirementCalculator.compute(tree, player);

            System.out.println("[CraftingCompass] === Shopping list for "
                    + targetStack.getDisplayName().getString()
                    + " x" + targetStack.getCount() + " ===");
            if (shopping.entries().isEmpty()) {
                System.out.println("  (no recipes found)");
            } else {
                for (var entry : shopping.entries()) {
                    switch (entry) {
                        case dev.craftingcompass.inventory.ShoppingList.Entry.ItemEntry ie -> {
                            String name = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                    .getKey(ie.item()).toString();
                            System.out.println("  " + name
                                    + ": need " + ie.needed()
                                    + ", have " + ie.have()
                                    + ", missing " + ie.missing());
                        }
                        case dev.craftingcompass.inventory.ShoppingList.Entry.TagEntry te -> {
                            System.out.println("  #" + te.tag().location()
                                    + ": need " + te.needed()
                                    + " (tag — inventory check skipped)");
                        }
                    }
                }
            }
        }, "CraftingCompass-Resolver");
        t.setDaemon(true);
        t.start();
    }
}