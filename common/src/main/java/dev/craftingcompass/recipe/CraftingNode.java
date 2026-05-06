package dev.craftingcompass.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record CraftingNode(
        RecipeProvider.Slot slot,      // what this node represents (Single or Tag)
        int amount,                     // how many needed
        List<CraftingNode> children,    // sub-ingredients (empty if leaf)
        boolean isLeaf
) {
    public static CraftingNode leaf(RecipeProvider.Slot slot, int amount) {
        return new CraftingNode(slot, amount, List.of(), true);
    }

    // Convenience for building intermediate (non-leaf) nodes, always Single
    public static CraftingNode intermediate(ItemStack output, int amount, List<CraftingNode> children) {
        return new CraftingNode(
                new RecipeProvider.Slot.Single(output.getItem(), output.getCount()),
                amount, children, false
        );
    }
}