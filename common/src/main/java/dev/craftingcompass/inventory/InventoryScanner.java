package dev.craftingcompass.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.HashMap;
import java.util.Map;

public final class InventoryScanner {

    public record Snapshot(Map<Item, Integer> counts) {

        public int countOf(Item item) {
            return counts.getOrDefault(item, 0);
        }

        /**
         * Sum the counts of every item that's a member of this tag.
         * Returns 0 if the tag is empty or no inventory items match.
         */
        public int countOfTag(TagKey<Item> tag) {
            int total = 0;
            for (var entry : counts.entrySet()) {
                if (entry.getKey().builtInRegistryHolder().is(tag)) {
                    total += entry.getValue();
                }
            }
            return total;
        }

        public boolean isEmpty() { return counts.isEmpty(); }
    }

    private InventoryScanner() {}

    /** Legacy entrypoint — keeps RequirementCalculator working. */
    public static Map<Item, Integer> scan(Player player) {
        return scanFull(player).counts();
    }

    /** Full snapshot including shulker contents, with tag-aware lookups. */
    public static Snapshot scanFull(Player player) {
        Map<Item, Integer> counts = new HashMap<>();
        if (player == null) return new Snapshot(counts);

        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            // If the stack is a shulker box (or any container with item-contents), drill in.
            addContainerContents(stack, counts);
        }
        return new Snapshot(counts);
    }

    /**
     * If the stack carries a CONTAINER component (vanilla shulker boxes do),
     * read the items it holds and add them to counts. We don't recurse — a
     * shulker inside a shulker is rare and not worth chasing.
     */
    private static void addContainerContents(ItemStack stack, Map<Item, Integer> counts) {
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container == null) return;
        container.nonEmptyItemCopyStream().forEach(inner ->
                counts.merge(inner.getItem(), inner.getCount(), Integer::sum));
    }
}
