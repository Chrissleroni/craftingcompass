package dev.craftingcompass.inventory;

import dev.craftingcompass.recipe.ResolvedTree;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class RequirementCalculator {
    private RequirementCalculator() {}

    public static ShoppingList compute(ResolvedTree tree, Player player) {
        Map<Item, Integer> have = InventoryScanner.scan(player);
        Map<Item, Integer> need = new HashMap<>();
        for (ItemStack stack : tree.baseRequirements()) {
            need.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        ShoppingList list = new ShoppingList();
        need.forEach((item, n) -> list.add(item, n, have.getOrDefault(item, 0)));
        return list;
    }
}
