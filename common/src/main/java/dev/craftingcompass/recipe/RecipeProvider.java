package dev.craftingcompass.recipe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface RecipeProvider {

    record FlatRecipe(List<Slot> inputs, ItemStack output, RecipeKind kind) {}

    sealed interface Slot {
        record Single(Item item, int count) implements Slot {}
        record Tag(net.minecraft.tags.TagKey<Item> tag, int count) implements Slot {}
    }

    enum RecipeKind {
        CRAFTING,
        SMELTING,
        STONECUTTING,
        GENERIC
    }

    List<FlatRecipe> recipesProducing(Item item);

    default boolean hasRecipeFor(Item item) {
        return !recipesProducing(item).isEmpty();
    }

    int totalRecipeCount();
}
