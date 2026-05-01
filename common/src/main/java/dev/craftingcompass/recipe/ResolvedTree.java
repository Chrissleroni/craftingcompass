package dev.craftingcompass.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public record ResolvedTree(CraftingNode root, List<ItemStack> baseRequirements) {}
