package dev.craftingcompass.inventory;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public final class ShoppingList {
    sealed public interface Entry {
        record ItemEntry(Item item, int needed, int have, int missing) implements Entry {}
        record TagEntry(TagKey<Item> tag, int needed) implements Entry {} // can't compute "have" easily
    }

    private final List<Entry> entries = new ArrayList<>();

    public void add(Item item, int needed, int have) {
        int missing = Math.max(0, needed - have);
        entries.add(new Entry.ItemEntry(item, needed, have, missing));
    }

    public void addTag(TagKey<Item> tag, int needed) {
        entries.add(new Entry.TagEntry(tag, needed));
    }

    public List<Entry> entries() { return entries; }

    public boolean isComplete() {
        return entries.stream().allMatch(e -> switch (e) {
            case Entry.ItemEntry ie -> ie.missing() == 0;
            case Entry.TagEntry te -> false;
        });
    }
}
