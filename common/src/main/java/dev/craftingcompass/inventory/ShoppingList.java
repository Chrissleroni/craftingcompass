package dev.craftingcompass.inventory;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public final class ShoppingList {
    sealed interface Entry {
        record ItemEntry(Item item, int needed, int have, int missing) implements Entry {}
        record TagEntry(TagKey<Item> tag, int needed) implements Entry {} // can't compute "have" easily
    }

    private final List<Entry> entries = new ArrayList<>();

    public void add(Item item, int needed, int have) {
        int missing = Math.max(0, needed - have);
        entries.add(new Entry(item, needed, have, missing));
    }

    public List<Entry> entries() { return entries; }

    public boolean isComplete() {
        return entries.stream().allMatch(e -> e.missing() == 0);
    }
}
