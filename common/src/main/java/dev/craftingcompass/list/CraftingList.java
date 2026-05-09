package dev.craftingcompass.list;

import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class CraftingList {

    private final LinkedHashMap<Item, Integer> entries = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<CraftingList>> listeners = new CopyOnWriteArrayList<>();

    public synchronized Map<Item, Integer> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public synchronized int quantity(Item item) {
        return entries.getOrDefault(item, 0);
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    public synchronized int size() {
        return entries.size();
    }

    public void add(Item item, int amount) {
        if (item == null || amount <= 0) return;
        synchronized (this) {
            entries.merge(item, amount, Integer::sum);
        }
        fire();
    }

    public void setQuantity(Item item, int amount) {
        if (item == null) return;
        synchronized (this) {
            if (amount <= 0) {
                if (entries.remove(item) == null) return;
            } else {
                entries.put(item, amount);
            }
        }
        fire();
    }

    public void remove(Item item) {
        if (item == null) return;
        synchronized (this) {
            if (entries.remove(item) == null) return;
        }
        fire();
    }

    public void clear() {
        synchronized (this) {
            if (entries.isEmpty()) return;
            entries.clear();
        }
        fire();
    }

    public void replaceAll(Map<Item, Integer> next) {
        synchronized (this) {
            entries.clear();
            for (var e : next.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
                    entries.put(e.getKey(), e.getValue());
                }
            }
        }
        fire();
    }

    public void addListener(Consumer<CraftingList> l) { listeners.add(l); }
    public void removeListener(Consumer<CraftingList> l) { listeners.remove(l); }

    private void fire() {
        for (var l : listeners) {
            try { l.accept(this); }
            catch (Throwable t) { t.printStackTrace(); }
        }
    }
}
