package dev.craftingcompass.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.List;

@FunctionalInterface
public interface IngredientChooser {
    ItemStack choose(List<ItemStack> options);

    IngredientChooser FIRST = opts -> opts.isEmpty() ? ItemStack.EMPTY : opts.get(0);
}
