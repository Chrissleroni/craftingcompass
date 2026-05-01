package dev.craftingcompass.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record CraftingNode(ItemStack output, int outputCount, List<CraftingNode> inputs, boolean isLeaf) {
    public static CraftingNode leaf(ItemStack stack, int count) {
        return new CraftingNode(stack, count, List.of(), true);
    }
}
