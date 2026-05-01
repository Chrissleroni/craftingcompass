package dev.craftingcompass.recipe;

import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

public final class StopSet {
    private final Set<Identifier> ids = new HashSet<>();

    public void add(String id) {
        ids.add(Identifier.parse(id));
    }

    public boolean contains(Identifier id) {
        return ids.contains(id);
    }

    public static StopSet defaultIntermediates() {
        StopSet s = new StopSet();
        s.add("minecraft:stick");
        s.add("minecraft:oak_planks");
        s.add("minecraft:iron_ingot");
        s.add("minecraft:gold_ingot");
        s.add("minecraft:copper_ingot");
        return s;
    }
}
