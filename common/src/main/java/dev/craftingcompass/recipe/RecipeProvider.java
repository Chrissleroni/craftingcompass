package dev.craftingcompass.recipe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface RecipeProvider {

    record FlatRecipe(List<ItemStack> inputs, ItemStack output) {}

    List<FlatRecipe> recipesProducing(Item item);

    default boolean hasRecipeFor(Item item) {
        return !recipesProducing(item).isEmpty();
    }

    int totalRecipeCount();
}
