package dev.craftingcompass.recipe;

import java.util.List;

public record ResolvedTree(CraftingNode root, List<BaseRequirement> baseRequirements) {

    public sealed interface BaseRequirement {
        record ItemReq(net.minecraft.world.item.Item item, int count) implements BaseRequirement {}
        record TagReq(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag, int count)
                implements BaseRequirement {}
    }
}