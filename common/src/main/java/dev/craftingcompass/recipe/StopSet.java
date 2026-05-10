package dev.craftingcompass.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class StopSet {
    private final Set<Identifier> ids = new HashSet<>();

    /**
     * Common-namespace tag prefixes that almost always indicate raw materials.
     * Mods following Forge/NeoForge conventions register their raw items
     * under #c:ingots/<name>, #c:gems/<name>, #c:dusts/<name>, etc.
     */
    private static final List<String> RAW_MATERIAL_TAG_PREFIXES = List.of(
            "c:gems",
            "c:dusts",
            "c:raw_materials",
            "c:nuggets",
            "c:ores",
            "c:rods",
            "c:gunpowders",
            "c:leathers",
            "c:wires",
            "c:slimeballs",
            "c:enderpearls",
            "c:obsidians"
    );

    public void add(String id) {
        ids.add(Identifier.parse(id));
    }

    public boolean contains(Identifier id) {
        return ids.contains(id);
    }

    /**
     * Returns true if the item should be treated as a terminal ingredient.
     * Either it's in the manual stop set, or it's tagged as a known raw material.
     */
    public boolean containsItem(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null && ids.contains(id)) return true;
        return isCommonRawMaterial(item);
    }

    /**
     * Check if this item is tagged with any common "raw material" tag.
     * This catches mod items that follow the c:* tag convention without
     * us having to hardcode them.
     */
    private static boolean isCommonRawMaterial(Item item) {
        var holderOpt = BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getKey(item));
        if (holderOpt.isEmpty()) return false;

        var holder = holderOpt.get();
        for (TagKey<Item> tag : holder.tags().toList()) {
            String tagPath = tag.location().toString();
            for (String prefix : RAW_MATERIAL_TAG_PREFIXES) {
                if (tagPath.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    public int size() {
        return ids.size();
    }

    public static StopSet defaultIntermediates() {
        StopSet s = new StopSet();

        // Ingots / nuggets / raw ores
        s.add("minecraft:iron_ingot");
        s.add("minecraft:gold_ingot");
        s.add("minecraft:copper_ingot");
        s.add("minecraft:iron_nugget");
        s.add("minecraft:gold_nugget");
        s.add("minecraft:raw_iron");
        s.add("minecraft:raw_gold");
        s.add("minecraft:raw_copper");
        s.add("minecraft:netherite_scrap");
        s.add("minecraft:ancient_debris");

        // Gems & minerals
        s.add("minecraft:diamond");
        s.add("minecraft:emerald");
        s.add("minecraft:redstone");
        s.add("minecraft:lapis_lazuli");
        s.add("minecraft:coal");
        s.add("minecraft:charcoal");
        s.add("minecraft:quartz");
        s.add("minecraft:amethyst_shard");
        s.add("minecraft:prismarine_shard");
        s.add("minecraft:prismarine_crystals");
        s.add("minecraft:nether_star");
        s.add("minecraft:echo_shard");

        // Mob drops
        s.add("minecraft:slime_ball");
        s.add("minecraft:magma_cream");
        s.add("minecraft:ender_pearl");
        s.add("minecraft:blaze_rod");
        s.add("minecraft:blaze_powder");
        s.add("minecraft:ghast_tear");
        s.add("minecraft:phantom_membrane");
        s.add("minecraft:rabbit_hide");
        s.add("minecraft:rabbit_foot");
        s.add("minecraft:leather");
        s.add("minecraft:feather");
        s.add("minecraft:string");
        s.add("minecraft:gunpowder");
        s.add("minecraft:bone");
        s.add("minecraft:bone_meal");
        s.add("minecraft:ink_sac");
        s.add("minecraft:glow_ink_sac");
        s.add("minecraft:spider_eye");
        s.add("minecraft:fermented_spider_eye");
        s.add("minecraft:rotten_flesh");
        s.add("minecraft:nautilus_shell");
        s.add("minecraft:heart_of_the_sea");
        s.add("minecraft:totem_of_undying");
        s.add("minecraft:shulker_shell");
        s.add("minecraft:dragon_breath");

        // Plant / food materials
        s.add("minecraft:sugar_cane");
        s.add("minecraft:sugar");
        s.add("minecraft:wheat");
        s.add("minecraft:cocoa_beans");
        s.add("minecraft:apple");
        s.add("minecraft:carrot");
        s.add("minecraft:potato");
        s.add("minecraft:beetroot");
        s.add("minecraft:melon_slice");
        s.add("minecraft:pumpkin");
        s.add("minecraft:egg");
        s.add("minecraft:milk_bucket");
        s.add("minecraft:water_bucket");
        s.add("minecraft:lava_bucket");
        s.add("minecraft:honeycomb");
        s.add("minecraft:honey_bottle");
        s.add("minecraft:glass_bottle");
        s.add("minecraft:kelp");
        s.add("minecraft:dried_kelp");
        s.add("minecraft:sweet_berries");
        s.add("minecraft:glow_berries");

        // Items that have no crafting recipe - chest loot only
        s.add("minecraft:enchanted_golden_apple");
        s.add("minecraft:music_disc_5");

        // Raw materials
        s.add("minecraft:clay_ball");
        s.add("minecraft:flint");
        s.add("minecraft:obsidian");
        s.add("minecraft:crying_obsidian");
        s.add("minecraft:nether_wart");
        s.add("minecraft:glowstone_dust");
        s.add("minecraft:gravel");
        s.add("minecraft:sand");
        s.add("minecraft:red_sand");
        s.add("minecraft:soul_sand");
        s.add("minecraft:soul_soil");

        // Stones (raw blocks)
        s.add("minecraft:stone");
        s.add("minecraft:smooth_stone");
        s.add("minecraft:cobblestone");
        s.add("minecraft:deepslate");
        s.add("minecraft:cobbled_deepslate");
        s.add("minecraft:granite");
        s.add("minecraft:diorite");
        s.add("minecraft:andesite");
        s.add("minecraft:tuff");
        s.add("minecraft:calcite");
        s.add("minecraft:blackstone");
        s.add("minecraft:basalt");
        s.add("minecraft:smooth_basalt");
        s.add("minecraft:netherrack");
        s.add("minecraft:end_stone");

        // Dyes
        s.add("minecraft:white_dye");
        s.add("minecraft:orange_dye");
        s.add("minecraft:magenta_dye");
        s.add("minecraft:light_blue_dye");
        s.add("minecraft:yellow_dye");
        s.add("minecraft:lime_dye");
        s.add("minecraft:pink_dye");
        s.add("minecraft:gray_dye");
        s.add("minecraft:light_gray_dye");
        s.add("minecraft:cyan_dye");
        s.add("minecraft:purple_dye");
        s.add("minecraft:blue_dye");
        s.add("minecraft:brown_dye");
        s.add("minecraft:green_dye");
        s.add("minecraft:red_dye");
        s.add("minecraft:black_dye");

        // Planks
//        s.add("minecraft:oak_planks");
//        s.add("minecraft:birch_planks");
//        s.add("minecraft:spruce_planks");
//        s.add("minecraft:jungle_planks");
//        s.add("minecraft:acacia_planks");
//        s.add("minecraft:dark_oak_planks");
//        s.add("minecraft:mangrove_planks");
//        s.add("minecraft:cherry_planks");
//        s.add("minecraft:pale_oak_planks");
//        s.add("minecraft:bamboo_planks");
//        s.add("minecraft:crimson_planks");
//        s.add("minecraft:warped_planks");

        return s;
    }
}