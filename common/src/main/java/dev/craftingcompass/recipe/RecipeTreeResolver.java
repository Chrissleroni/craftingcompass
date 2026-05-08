package dev.craftingcompass.recipe;

import dev.craftingcompass.config.CraftingCompassConfig;
import dev.craftingcompass.config.DecompositionProfile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
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

    private static final class Totals {
        final Map<Item, Integer> items = new LinkedHashMap<>();
        final Map<TagKey<Item>, Integer> tags = new LinkedHashMap<>();

        void addItem(Item item, int count) {
            items.merge(item, count, Integer::sum);
        }

        void addTag(TagKey<Item> tag, int count) {
            tags.merge(tag, count, Integer::sum);
        }

        List<ResolvedTree.BaseRequirement> toList() {
            List<ResolvedTree.BaseRequirement> out = new ArrayList<>();
            items.forEach((item, count) -> out.add(new ResolvedTree.BaseRequirement.ItemReq(item, count)));
            tags.forEach((tag, count) -> out.add(new ResolvedTree.BaseRequirement.TagReq(tag, count)));
            return out;
        }
    }

    public ResolvedTree resolve(ItemStack target, int amount) {
        Totals totals = new Totals();
        Set<Item> visiting = new HashSet<>();

        RecipeProvider.Slot rootSlot = new RecipeProvider.Slot.Single(target.getItem(), target.getCount());
        CraftingNode root = build(rootSlot, amount, totals, visiting, 0);

        return new ResolvedTree(root, totals.toList());
    }

    private CraftingNode build(RecipeProvider.Slot slot, int amount, Totals totals,
                               Set<Item> visiting, int depth) {
        if (slot instanceof RecipeProvider.Slot.Tag tagSlot) {
            totals.addTag(tagSlot.tag(), amount);
            return CraftingNode.leaf(slot, amount);
        }

        RecipeProvider.Slot.Single single = (RecipeProvider.Slot.Single) slot;
        Item item = single.item();

        if (depth >= maxDepth || visiting.contains(item) || isLeaf(item)) {
            totals.addItem(item, amount);
            return CraftingNode.leaf(slot, amount);
        }

        List<RecipeProvider.FlatRecipe> recipes = provider.recipesProducing(item);
        if (recipes.isEmpty()) {
            totals.addItem(item, amount);
            return CraftingNode.leaf(slot, amount);
        }

        RecipeProvider.FlatRecipe recipe = selectBestRecipe(recipes, item);
        if (recipe == null) {
            totals.addItem(item, amount);
            return CraftingNode.leaf(slot, amount);
        }

        int perCraft = Math.max(1, recipe.output().getCount());
        int crafts = (int) Math.ceil(amount / (double) perCraft);

        visiting.add(item);
        List<CraftingNode> children = new ArrayList<>();

        for (RecipeProvider.Slot ingredientSlot : recipe.inputs()) {
            int needed = crafts * slotCount(ingredientSlot);
            children.add(build(ingredientSlot, needed, totals, visiting, depth + 1));
        }

        visiting.remove(item);
        return CraftingNode.intermediate(new ItemStack(item), amount, children);
    }

    private RecipeProvider.FlatRecipe selectBestRecipe(List<RecipeProvider.FlatRecipe> recipes, Item target) {
        RecipeProvider.FlatRecipe best = null;
        int bestScore = -1;

        for (RecipeProvider.FlatRecipe recipe : recipes) {
            if (isDecomposition(recipe)) continue;
            int score = scoreRecipe(recipe);
            if (score > bestScore) {
                bestScore = score;
                best = recipe;
            }
        }

        return best;
    }

    private static boolean isDecomposition(RecipeProvider.FlatRecipe recipe) {
        int inputCount = 0;
        for (RecipeProvider.Slot s : recipe.inputs()) {
            inputCount += slotCount(s);
        }
        return inputCount == 1 && recipe.output().getCount() > 1;
    }

    private static int scoreRecipe(RecipeProvider.FlatRecipe recipe) {
        int score = switch (recipe.kind()) {
            case SMELTING -> 1000;
            case CRAFTING -> 500;
            case STONECUTTING -> 100;
            case GENERIC -> 50;
        };
        for (RecipeProvider.Slot s : recipe.inputs()) {
            score += slotCount(s);
        }
        return score;
    }

    private static int slotCount(RecipeProvider.Slot slot) {
        return switch (slot) {
            case RecipeProvider.Slot.Single s -> s.count();
            case RecipeProvider.Slot.Tag t -> t.count();
        };
    }

    private boolean isLeaf(Item item) {
        if (CraftingCompassConfig.profile == DecompositionProfile.DIRECT) return true;
        if (stopSet.containsItem(item)) return true;
        if (CraftingCompassConfig.profile == DecompositionProfile.INTERMEDIATES
                && stopSet.contains(BuiltInRegistries.ITEM.getKey(item))) {
            return true;
        }
        if (!provider.hasRecipeFor(item)) return true;
        return false;
    }
}
