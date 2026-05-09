package dev.craftingcompass.recipe;

import dev.craftingcompass.config.CraftingCompassConfig;
import dev.craftingcompass.list.CraftingList;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AggregateResolver {

    public record Aggregate(
            List<Map.Entry<Item, Integer>> items,
            List<Map.Entry<TagKey<Item>, Integer>> tags
    ) {
        public boolean isEmpty() { return items.isEmpty() && tags.isEmpty(); }
    }

    private final RecipeProvider provider;

    public AggregateResolver(RecipeProvider provider) {
        this.provider = provider;
    }

    public Aggregate resolveAll(CraftingList list) {
        var snap = list.snapshot();
        if (snap.isEmpty()) return new Aggregate(List.of(), List.of());

        // Merge all list entries into a single starting demand map.
        Map<Item, Integer> demand = new LinkedHashMap<>();
        for (var e : snap.entrySet()) {
            demand.merge(e.getKey(), e.getValue(), Integer::sum);
        }

        RecipeTreeResolver resolver = new RecipeTreeResolver(
                provider,
                IngredientChooser.FIRST,
                StopSet.defaultIntermediates(),
                CraftingCompassConfig.profile == null ? 8 : 8
        );

        // Display target is just for the tree's root — pick any list entry.
        var first = snap.entrySet().iterator().next();
        ItemStack display = new ItemStack(first.getKey(), first.getValue());

        ResolvedTree tree = resolver.resolveDemand(display, demand);

        Map<Item, Integer> totalsItems = new LinkedHashMap<>();
        Map<TagKey<Item>, Integer> totalsTags = new LinkedHashMap<>();
        for (var req : tree.baseRequirements()) {
            switch (req) {
                case ResolvedTree.BaseRequirement.ItemReq ir ->
                        totalsItems.merge(ir.item(), ir.count(), Integer::sum);
                case ResolvedTree.BaseRequirement.TagReq tr ->
                        totalsTags.merge(tr.tag(), tr.count(), Integer::sum);
            }
        }

        return new Aggregate(
                new ArrayList<>(totalsItems.entrySet()),
                new ArrayList<>(totalsTags.entrySet())
        );
    }
}
