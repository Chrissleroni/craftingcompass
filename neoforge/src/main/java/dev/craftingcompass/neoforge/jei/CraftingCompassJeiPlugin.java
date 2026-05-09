package dev.craftingcompass.neoforge.jei;

import dev.craftingcompass.CraftingCompassConstants;
import dev.craftingcompass.recipe.RecipeProvider;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@JeiPlugin
public final class CraftingCompassJeiPlugin implements IModPlugin {

    private static volatile IJeiRuntime RUNTIME;
    private static volatile RecipeProvider PROVIDER;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(CraftingCompassConstants.MOD_ID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RUNTIME = runtime;
        JeiBackedRecipeProvider provider = new JeiBackedRecipeProvider(runtime);
        provider.ensureBuilding();
        PROVIDER = provider;
    }

    @Override
    public void onRuntimeUnavailable() {
        RUNTIME = null;
        PROVIDER = null;
    }

    public static RecipeProvider provider() { return PROVIDER; }
    public static IJeiRuntime runtime() { return RUNTIME; }

    /**
     * Tags we explicitly DO NOT want to use as ingredient identifiers,
     * even if all items in a slot share them. These are usage/categorization
     * tags rather than "this kind of ingredient" tags.
     */
    private static final Set<String> UNWANTED_TAG_PATTERNS = Set.of(
            "horse_food", "wolf_food", "fox_food", "parrot_food", "panda_food",
            "axolotl_food", "cat_food", "frog_food", "camel_food", "goat_food",
            "chicken_food", "cow_food", "pig_food", "rabbit_food", "sheep_food",
            "ocelot_food", "armadillo_food", "hoglin_food", "piglin_food",
            "strider_food", "turtle_food", "llama_food", "creeper_igniters",
            "dampens_vibrations", "freeze_immune_wearables", "trim_materials",
            "trim_templates"
    );

    /**
     * Preferred tag namespaces, in order. We prefer tags from these namespaces
     * because they're more likely to represent "what the ingredient IS" rather
     * than "what an animal eats" or "what a brewing recipe accepts".
     */
    private static final List<String> PREFERRED_NAMESPACES = List.of("c", "minecraft");

    private static boolean isTransformationRecipe(List<ItemStack> inputs, ItemStack output) {
        String outName = BuiltInRegistries.ITEM.getKey(output.getItem()).getPath();
        String outNamespace = BuiltInRegistries.ITEM.getKey(output.getItem()).getNamespace();

        for (ItemStack in : inputs) {
            if (in.getItem() == output.getItem()) return true; // self-loop is always transformation
            var inKey = BuiltInRegistries.ITEM.getKey(in.getItem());
            if (!inKey.getNamespace().equals(outNamespace)) continue; // cross-mod = not a recolor

            String inName = inKey.getPath();
            // Strip common color/material prefixes from both and compare stems
            String inStem = stripColorPrefix(inName);
            String outStem = stripColorPrefix(outName);
            if (inStem.equals(outStem) && !inName.equals(outName)) {
                // Same shape, different color/variant prefix — recolor.
                return true;
            }
        }
        return false;
    }

    private static final Set<String> COLOR_PREFIXES = Set.of(
            "white_", "orange_", "magenta_", "light_blue_", "yellow_", "lime_",
            "pink_", "gray_", "light_gray_", "cyan_", "purple_", "blue_",
            "brown_", "green_", "red_", "black_"
    );

    private static String stripColorPrefix(String name) {
        for (String p : COLOR_PREFIXES) {
            if (name.startsWith(p)) return name.substring(p.length());
        }
        return name;
    }

    private static boolean isUsageTag(TagKey<Item> tag) {
        String path = tag.location().getPath();
        for (String pattern : UNWANTED_TAG_PATTERNS) {
            if (path.equals(pattern) || path.endsWith("/" + pattern)) return true;
        }
        return false;
    }

    /** Tags so generic they don't indicate identity. */
    private static boolean isUbiquitousTag(TagKey<Item> tag) {
        int size = 0;
        for (var ignored : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            size++;
            if (size > 200) return true; // very large tag, not identity
        }
        return false;
    }

    private static final class JeiBackedRecipeProvider implements RecipeProvider {
        private final IJeiRuntime runtime;
        private final Map<Item, List<FlatRecipe>> index = new HashMap<>();
        private final AtomicBoolean built = new AtomicBoolean(false);
        private final AtomicBoolean building = new AtomicBoolean(false);

        JeiBackedRecipeProvider(IJeiRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public List<FlatRecipe> recipesProducing(Item item) {
            if (!built.get()) {
                ensureBuilding();
                return List.of();
            }
            return index.getOrDefault(item, List.of());
        }

        @Override
        public boolean hasRecipeFor(Item item) {
            return built.get() && index.containsKey(item);
        }

        @Override
        public int totalRecipeCount() {
            int total = 0;
            for (var v : index.values()) total += v.size();
            return total;
        }

        void ensureBuilding() {
            if (building.compareAndSet(false, true)) {
                System.out.println("[CraftingCompass] Building recipe index in background...");
                Thread t = new Thread(this::buildIndex, "CraftingCompass-IndexBuilder");
                t.setDaemon(true);
                t.start();
            }
        }

        private void buildIndex() {
            try {
                long start = System.currentTimeMillis();
                Map<Item, List<FlatRecipe>> local = new HashMap<>();
                var recipeManager = runtime.getRecipeManager();

                List<IRecipeCategory<?>> categories = recipeManager
                        .createRecipeCategoryLookup()
                        .get()
                        .toList();

                for (IRecipeCategory<?> category : categories) {
                    indexCategory(category, local);
                }

                index.putAll(local);
                built.set(true);
                long elapsed = System.currentTimeMillis() - start;

                Item qei = BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("refinedstorage", "quartz_enriched_iron"))
                        .orElseThrow().value();
                System.out.println("[CC QEI-INDEX] QEI has " + local.getOrDefault(qei, List.of()).size() + " indexed recipes");
                for (var r : local.getOrDefault(qei, List.of())) {
                    System.out.println("  " + r.kind() + " inputs=" + r.inputs().size() + " out=" + r.output().getCount());
                    for (var s : r.inputs()) System.out.println("    " + s);
                }

                Item silicon = BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("refinedstorage", "silicon"))
                        .orElseThrow().value();
                System.out.println("[CC SILICON-INDEX] silicon has " + local.getOrDefault(silicon, List.of()).size() + " indexed recipes");
                for (var r : local.getOrDefault(silicon, List.of())) {
                    System.out.println("  " + r.kind() + " inputs=" + r.inputs().size() + " out=" + r.output().getCount());
                    for (var s : r.inputs()) System.out.println("    " + s);
                }

                System.out.println("[CraftingCompass] Recipe index built: "
                        + totalRecipeCount() + " recipes across "
                        + index.size() + " output items in " + elapsed + "ms.");
            } catch (Throwable th) {
                System.err.println("[CraftingCompass] Index build failed:");
                th.printStackTrace();
                building.set(false);
            }
        }

        private void indexCategory(IRecipeCategory<?> category, Map<Item, List<FlatRecipe>> target) {
            indexCategoryTyped(category, target);
        }

        private <T> void indexCategoryTyped(IRecipeCategory<T> category, Map<Item, List<FlatRecipe>> target) {
            IRecipeType<T> type = category.getRecipeType();
            String typeUid = type.getUid().toString();
            RecipeKind kind = classifyCategory(typeUid);

            // Temporary debug — log all categories
            int[] count = {0};
            runtime.getRecipeManager().createRecipeLookup(type).get().forEach(r -> count[0]++);
            System.out.println("[CraftingCompass CAT] " + typeUid
                    + " (" + count[0] + " recipes)"
                    + (shouldSkipCategory(typeUid) ? " SKIPPED" : " indexing"));

            // Skip categories that aren't actually crafting/transformation recipes.
            // These are things like "Anvil repair", "Brewing" (we don't model fluids),
            // "Animal feeding" (which produces the "horse_food" type bug).
            if (shouldSkipCategory(typeUid)) return;

            var recipeManager = runtime.getRecipeManager();

            recipeManager.createRecipeLookup(type).get().forEach(recipe -> {
                try {
                    indexSingleRecipe(category, recipe, kind, target);
                } catch (Exception e) {
                    // Per-recipe failure shouldn't kill the whole indexing pass
                }
            });
        }

        /**
         * Skip recipe categories that aren't real crafting/transformation recipes.
         * This is what was producing the "horse_food" bug — JEI's "info" categories
         * for animal feeding were being treated as recipes that produce the apple.
         */
        private static boolean shouldSkipCategory(String uid) {
            String lower = uid.toLowerCase();
            // Animal/entity interaction "recipes", JEI has these for info, but they
            // aren't real recipes that produce items
            if (lower.contains("info") || lower.contains("anvil")
                    || lower.contains("brewing") || lower.contains("fuel")
                    || lower.contains("compostable") || lower.contains("ingredient_info")
                    || lower.contains("tag_recipes")) {
                return true;
            }
            return false;
        }

        private <T> void indexSingleRecipe(IRecipeCategory<T> category, T recipe,
                                           RecipeKind kind, Map<Item, List<FlatRecipe>> target) {
            var recipeManager = runtime.getRecipeManager();
            IIngredientSupplier supplier = recipeManager.getRecipeIngredients(category, recipe);

            List<ItemStack> outputs = extractItemStacks(supplier, RecipeIngredientRole.OUTPUT);
            if (outputs.isEmpty()) return;

            List<ItemStack> inputItemStacks = extractItemStacks(supplier, RecipeIngredientRole.INPUT);

            List<Slot> inputs = extractSlotsFromJei(supplier);
            if (inputs.isEmpty()) return;
            if (inputs.size() > 25) return;

            Set<Item> singleInputItems = new HashSet<>();
            for (Slot s : inputs) {
                if (s instanceof Slot.Single si) singleInputItems.add(si.item());
            }

            for (ItemStack out : outputs) {
                if (out.isEmpty()) continue;
                if (isTransformationRecipe(inputItemStacks, out)) {
                    String name = BuiltInRegistries.ITEM.getKey(out.getItem()).toString();
                    if (name.contains("storage") || name.contains("housing")) {
                        System.out.println("[CC TRANSFORM] Skipping " + name + " — input shares identity tag");
                    }
                    continue;
                }
                if (singleInputItems.contains(out.getItem())) continue;

                FlatRecipe flat = new FlatRecipe(inputs, out, kind);
                target.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(flat);
            }
        }

        private static RecipeKind classifyCategory(String uid) {
            if (uid.contains("crafting")) return RecipeKind.CRAFTING;
            if (uid.contains("smelting") || uid.contains("blasting") || uid.contains("smoking"))
                return RecipeKind.SMELTING;
            if (uid.contains("stonecutting")) return RecipeKind.STONECUTTING;
            return RecipeKind.GENERIC;
        }

        private static List<Slot> extractSlotsFromJei(IIngredientSupplier supplier) {
            List<ItemStack> allStacks = new ArrayList<>();
            for (ITypedIngredient<?> typed : supplier.getIngredients(RecipeIngredientRole.INPUT)) {
                typed.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(allStacks::add);
            }
            if (allStacks.isEmpty()) return List.of();

            // Count occurrences of each unique item.
            LinkedHashMap<Item, Integer> itemCounts = new LinkedHashMap<>();
            for (ItemStack s : allStacks) {
                itemCounts.merge(s.getItem(), 1, Integer::sum);
            }

            // Bucket items by their occurrence count. Items in the same bucket
            // are candidates to be tag-variants of the same logical slot.
            Map<Integer, List<Item>> byCount = new LinkedHashMap<>();
            for (var e : itemCounts.entrySet()) {
                byCount.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            }

            List<Slot> result = new ArrayList<>();

            for (var bucket : byCount.entrySet()) {
                int count = bucket.getKey();
                List<Item> items = bucket.getValue();

                if (items.size() == 1) {
                    // Lone item with this count — literal slot.
                    result.add(new Slot.Single(items.get(0), count));
                    continue;
                }

                // Multiple items share this count. Try to tag-group them.
                // Repeatedly extract the largest tag-group from the bucket;
                // anything left over becomes literal singles.
                List<Item> remaining = new ArrayList<>(items);
                while (remaining.size() > 1) {
                    TagGroup g = findLargestTagGroup(remaining);
                    if (g == null || g.members.size() < 2) break;
                    result.add(new Slot.Tag(g.tag, count));
                    remaining.removeAll(g.members);
                }
                for (Item leftover : remaining) {
                    result.add(new Slot.Single(leftover, count));
                }
            }

            return result;
        }

        private record TagGroup(TagKey<Item> tag, Set<Item> members) {}

        /**
         * Find the largest subset of the given items that share a common tag.
         * Returns null if no tag covers more than 1 item.
         */
        private static TagGroup findLargestTagGroup(List<Item> items) {
            // Collect all tags for each item
            Map<Item, Set<TagKey<Item>>> itemTags = new LinkedHashMap<>();
            for (Item item : items) {
                Optional<Holder.Reference<Item>> holderOpt = BuiltInRegistries.ITEM.get(
                        BuiltInRegistries.ITEM.getKey(item));
                if (holderOpt.isEmpty()) continue;
                itemTags.put(item, new HashSet<>(holderOpt.get().tags().toList()));
            }

            // For each tag that any item has, count how many of our items share it
            Map<TagKey<Item>, Set<Item>> tagMembers = new LinkedHashMap<>();
            for (var entry : itemTags.entrySet()) {
                for (TagKey<Item> tag : entry.getValue()) {
                    // Skip unwanted tags
                    String path = tag.location().getPath();
                    boolean unwanted = false;
                    for (String pattern : UNWANTED_TAG_PATTERNS) {
                        if (path.equals(pattern) || path.endsWith("/" + pattern)) {
                            unwanted = true;
                            break;
                        }
                    }
                    if (unwanted) continue;

                    tagMembers.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(entry.getKey());
                }
            }

            // Find the tag that covers the most of our items
            TagKey<Item> bestTag = null;
            Set<Item> bestMembers = Set.of();
            int bestScore = 0;

            for (var entry : tagMembers.entrySet()) {
                if (entry.getValue().size() <= 1) continue;
                int score = entry.getValue().size() * 100 + scoreTag(entry.getKey());
                if (score > bestScore) {
                    bestScore = score;
                    bestTag = entry.getKey();
                    bestMembers = entry.getValue();
                }
            }

            if (bestTag == null) return null;
            return new TagGroup(bestTag, bestMembers);
        }

        /**
         * Score a tag by how likely it is to represent "what the ingredient IS"
         * rather than "what role this item plays elsewhere".
         */
        private static int scoreTag(TagKey<Item> tag) {
            int score = 0;
            String namespace = tag.location().getNamespace();
            String path = tag.location().getPath();

            // Strongly prefer c: namespace (cross-mod ingredient categorization)
            if (namespace.equals("c")) score += 1000;
            else if (namespace.equals("minecraft")) score += 500;
            else score += 100; // mod-specific

            // Bonus for clearly "ingredient" paths
            if (path.contains("ingot") || path.contains("gem") || path.contains("dust")
                    || path.contains("plank") || path.contains("log") || path.contains("wood")
                    || path.contains("nugget") || path.contains("ore") || path.contains("raw_")
                    || path.contains("fuel")) {
                score += 200;
            }

            // Penalize tags with "/" in path (usually sub-categorizations like
            // "c:ingots/iron" — we want the parent "c:ingots" if available)
            int slashes = 0;
            for (int i = 0; i < path.length(); i++) {
                if (path.charAt(i) == '/') slashes++;
            }
            score -= slashes * 50;

            // Smaller tags (more specific) get a small bonus, but it's outweighed
            // by namespace and semantic matching
            int tagSize = 0;
            for (var ignored : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) tagSize++;
            if (tagSize > 0) score += Math.max(0, 50 - tagSize);

            return score;
        }

        private static List<ItemStack> extractItemStacks(IIngredientSupplier supplier,
                                                         RecipeIngredientRole role) {
            LinkedHashMap<Item, ItemStack> seen = new LinkedHashMap<>();
            for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
                typed.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(s -> seen.putIfAbsent(s.getItem(), s));
            }
            return new ArrayList<>(seen.values());
        }
    }
}