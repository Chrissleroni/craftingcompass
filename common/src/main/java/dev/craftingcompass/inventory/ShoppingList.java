package dev.craftingcompass.inventory;

import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public final class ShoppingList {
    public record Entry(Item item, int needed, int have, int missing) {}

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
