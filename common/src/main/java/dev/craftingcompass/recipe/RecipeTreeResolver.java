package dev.craftingcompass.recipe;

import dev.craftingcompass.config.CraftingCompassConfig;
import dev.craftingcompass.config.DecompositionProfile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class RecipeTreeResolver {
    private final RecipeProvider provider;
    private final IngredientChooser chooser;
    private final StopSet stopSet;
    private final int maxDepth;

    public RecipeTreeResolver(RecipeProvider provider, IngredientChooser chooser,
                              StopSet stopSet, int maxDepth) {
        this.provider = provider;
        this.chooser = chooser;
        this.stopSet = stopSet;
        this.maxDepth = maxDepth;
    }

    public ResolvedTree resolve(ItemStack target, int amount) {
        Map<Item, Integer> totals = new LinkedHashMap<>();
        Set<Item> visiting = new HashSet<>();
        Set<Item> resolved = new HashSet<>(); // global cache — don't recompute same item twice
        CraftingNode root = build(target, amount, totals, visiting, resolved, 0);

        List<ItemStack> base = new ArrayList<>();
        totals.forEach((item, count) -> base.add(new ItemStack(item, count)));
        return new ResolvedTree(root, base);
    }

    private CraftingNode build(ItemStack want, int amount, Map<Item, Integer> totals,
                               Set<Item> visiting, Set<Item> resolved, int depth) {
        Item item = want.getItem();

        if (depth >= maxDepth || visiting.contains(item) || isLeaf(item)) {
            totals.merge(item, amount, Integer::sum);
            return CraftingNode.leaf(want, amount);
        }

        List<RecipeProvider.FlatRecipe> recipes = provider.recipesProducing(item);
        if (recipes.isEmpty()) {
            totals.merge(item, amount, Integer::sum);
            return CraftingNode.leaf(want, amount);
        }

        RecipeProvider.FlatRecipe recipe = recipes.getFirst();
        int perCraft = Math.max(1, recipe.output().getCount());
        int crafts = (int) Math.ceil(amount / (double) perCraft);

        visiting.add(item);
        resolved.add(item);
        List<CraftingNode> children = new ArrayList<>();
        for (ItemStack ingredientStack : recipe.inputs()) {
            if (ingredientStack.isEmpty()) continue;
            // Skip items we've already fully resolved in this tree to avoid
            // exponential blowup in diamond-shaped dependency graphs
            if (resolved.contains(ingredientStack.getItem())) {
                totals.merge(ingredientStack.getItem(),
                        crafts * ingredientStack.getCount(), Integer::sum);
                children.add(CraftingNode.leaf(ingredientStack,
                        crafts * ingredientStack.getCount()));
                continue;
            }
            children.add(build(ingredientStack, crafts * ingredientStack.getCount(),
                    totals, visiting, resolved, depth + 1));
        }
        visiting.remove(item);

        return new CraftingNode(want, amount, children, false);
    }

    private boolean isLeaf(Item item) {
        if (CraftingCompassConfig.profile == DecompositionProfile.DIRECT) return true;
        if (CraftingCompassConfig.profile == DecompositionProfile.INTERMEDIATES
                && stopSet.contains(BuiltInRegistries.ITEM.getKey(item))) {
            return true;
        }
        if (!provider.hasRecipeFor(item)) return true; // raw materials
        return false;
    }
}
