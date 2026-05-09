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

    /**
     * Two-pass resolver:
     *
     * Pass 1: Accumulate per-item demand into a single map. We don't ceil-to-crafts
     *         per ingredient - instead we keep raw demand and a counter of how many
     *         crafts of each item we've already provisioned. When demand grows on a
     *         re-visit (because two different parents both consume this ingredient),
     *         we provision only the additional crafts needed.
     *
     * This converges naturally for acyclic recipe graphs. The safety counter is a
     * backstop against malformed graphs (cycles dodged by selectBestRecipe should
     * already prevent runaway, but better safe than crashed).
     */
    public ResolvedTree resolve(ItemStack target, int amount) {
        Map<Item, Integer> demand = new LinkedHashMap<>();
        Map<TagKey<Item>, Integer> tagDemand = new LinkedHashMap<>();
        Map<Item, RecipeProvider.FlatRecipe> chosenRecipe = new LinkedHashMap<>();
        Map<Item, Integer> craftsProvisioned = new LinkedHashMap<>();
        Set<Item> leaves = new LinkedHashSet<>();

        demand.merge(target.getItem(), amount, Integer::sum);

        boolean changed = true;
        int safety = 0;
        while (changed && safety++ < 10_000) {
            changed = false;
            // Snapshot keys to avoid concurrent modification on demand growth.
            List<Item> queue = new ArrayList<>(demand.keySet());
            for (Item item : queue) {
                int totalDemand = demand.getOrDefault(item, 0);
                int alreadyProvisioned = craftsProvisioned.getOrDefault(item, 0);

                if (leaves.contains(item)) continue;

                if (isLeaf(item)) {
                    leaves.add(item);
                    continue;
                }

                List<RecipeProvider.FlatRecipe> recipes = provider.recipesProducing(item);
                if (recipes.isEmpty()) {
                    leaves.add(item);
                    continue;
                }

                RecipeProvider.FlatRecipe recipe = chosenRecipe.computeIfAbsent(
                        item, k -> selectBestRecipe(recipes, k));
                if (recipe == null) {
                    leaves.add(item);
                    continue;
                }

                int perCraft = Math.max(1, recipe.output().getCount());
                int totalCraftsNeeded = (int) Math.ceil(totalDemand / (double) perCraft);
                int newCrafts = totalCraftsNeeded - alreadyProvisioned;
                if (newCrafts <= 0) continue;

                craftsProvisioned.put(item, totalCraftsNeeded);
                changed = true;

                for (RecipeProvider.Slot slot : recipe.inputs()) {
                    int needed = newCrafts * slotCount(slot);
                    if (slot instanceof RecipeProvider.Slot.Single s) {
                        demand.merge(s.item(), needed, Integer::sum);
                    } else if (slot instanceof RecipeProvider.Slot.Tag t) {
                        tagDemand.merge(t.tag(), needed, Integer::sum);
                    }
                }
            }
        }

        // Build totals: only leaves contribute item base requirements.
        // (Items that get crafted have their demand satisfied internally.)
        Totals totals = new Totals();
        for (Item leaf : leaves) {
            int d = demand.getOrDefault(leaf, 0);
            if (d > 0) totals.addItem(leaf, d);
        }
        for (var e : tagDemand.entrySet()) {
            totals.addTag(e.getKey(), e.getValue());
        }

        // Tree structure for display. The current ResolvedTree consumer only reads
        // baseRequirements() (RequirementCalculator), so we hand back a stub root.
        // If/when the UI walks the tree, this is where to reconstruct the parent/
        // child structure from chosenRecipe + craftsProvisioned.
        CraftingNode root = CraftingNode.intermediate(target, amount, List.of());
        return new ResolvedTree(root, totals.toList());
    }

    private RecipeProvider.FlatRecipe selectBestRecipe(List<RecipeProvider.FlatRecipe> recipes, Item target) {
        // Optional debug - keep or remove as you like
        String name = BuiltInRegistries.ITEM.getKey(target).toString();
        boolean debug = name.contains("storage_disk") || name.contains("storage_part")
                || name.contains("storage_housing") || name.contains("quartz_enriched");
        if (debug) {
            System.out.println("[CC SELECT] " + name + " has " + recipes.size() + " candidate recipes:");
            for (var r : recipes) {
                int total = 0;
                for (var s : r.inputs()) total += slotCount(s);
                System.out.println("  kind=" + r.kind() + " inputs=" + r.inputs().size()
                        + " totalCount=" + total + " out=" + r.output().getCount());
                for (var s : r.inputs()) System.out.println("    " + s);
            }
        }

        RecipeProvider.FlatRecipe best = null;
        int bestScore = Integer.MIN_VALUE;

        for (RecipeProvider.FlatRecipe recipe : recipes) {
            if (isDecomposition(recipe)) continue;
            int score = scoreRecipe(recipe);
            if (score > bestScore) {
                bestScore = score;
                best = recipe;
            }
        }

        if (debug && best != null) {
            System.out.println("[CC SELECT] -> picked: kind=" + best.kind()
                    + " inputs=" + best.inputs().size() + " out=" + best.output().getCount());
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
            case SMELTING -> 10000;
            case CRAFTING -> 5000;
            case STONECUTTING -> 1000;
            case GENERIC -> 100;
        };
        int totalInputs = 0;
        for (RecipeProvider.Slot s : recipe.inputs()) {
            totalInputs += slotCount(s);
        }
        // Penalize bloated input counts. A real crafting recipe has ≤ 9 inputs.
        score -= totalInputs * 10;
        if (totalInputs <= 9) score += 500;
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