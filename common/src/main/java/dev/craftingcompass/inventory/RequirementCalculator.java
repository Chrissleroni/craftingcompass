package dev.craftingcompass.inventory;

import dev.craftingcompass.recipe.ResolvedTree;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.Map;

public final class RequirementCalculator {
    private RequirementCalculator() {}

    public static ShoppingList compute(ResolvedTree tree, Player player) {
        Map<Item, Integer> have = InventoryScanner.scan(player);
        ShoppingList list = new ShoppingList();

        for (ResolvedTree.BaseRequirement req : tree.baseRequirements()) {
            switch (req) {
                case ResolvedTree.BaseRequirement.ItemReq ir ->
                        list.add(ir.item(), ir.count(), have.getOrDefault(ir.item(), 0));
                case ResolvedTree.BaseRequirement.TagReq tr ->
                        list.addTag(tr.tag(), tr.count());
            }
        }

        return list;
    }
}