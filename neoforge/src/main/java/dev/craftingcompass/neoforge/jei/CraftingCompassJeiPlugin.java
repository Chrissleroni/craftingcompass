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
            // Animal/entity interaction "recipes" — JEI has these for info, but they
            // aren't real recipes that produce items
            if (lower.contains("info") || lower.contains("anvil")
                    || lower.contains("brewing") || lower.contains("fuel")
                    || lower.contains("compostable") || lower.contains("ingredient_info")) {
                return true;
            }
            return false;
        }

        private <T> void indexSingleRecipe(IRecipeCategory<T> category, T recipe,
                                           RecipeKind kind, Map<Item, List<FlatRecipe>> target) {
            var recipeManager = runtime.getRecipeManager();
            IIngredientSupplier supplier = recipeManager.getRecipeIngredients(category, recipe);

            // Temporary debug — catch ALL refinedstorage recipes regardless of extraction
            List<ItemStack> allOutputs = new ArrayList<>();
            for (ITypedIngredient<?> typed : supplier.getIngredients(RecipeIngredientRole.OUTPUT)) {
                typed.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(allOutputs::add);
            }
            List<ItemStack> allInputs = new ArrayList<>();
            for (ITypedIngredient<?> typed : supplier.getIngredients(RecipeIngredientRole.INPUT)) {
                typed.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(allInputs::add);
            }
            for (ItemStack out : allOutputs) {
                String name = BuiltInRegistries.ITEM.getKey(out.getItem()).toString();
                if (name.contains("storage") || name.contains("housing")) {
                    System.out.println("[CraftingCompass FOUND] " + name + " x" + out.getCount()
                            + " in category " + category.getRecipeType().getUid()
                            + " inputs=" + allInputs.size());
                }
            }

            List<ItemStack> outputs = extractItemStacks(supplier, RecipeIngredientRole.OUTPUT);
            if (outputs.isEmpty()) return;

            List<Slot> inputs = extractSlotsFromJei(supplier);
            if (inputs.isEmpty()) return;
            if (inputs.size() > 25) {
                for (ItemStack out : outputs) {
                    String name = BuiltInRegistries.ITEM.getKey(out.getItem()).toString();
                    if (name.contains("refinedstorage")) {
                        System.out.println("[CraftingCompass SIZE] " + name
                                + " had " + inputs.size() + " parsed slots — skipped");
                        for (Slot s : inputs) {
                            System.out.println("  " + s);
                        }
                    }
                }
                return;
            }

            Set<Item> inputItems = new HashSet<>();
            for (Slot s : inputs) {
                if (s instanceof Slot.Single si) inputItems.add(si.item());
            }

            for (ItemStack out : outputs) {
                if (out.isEmpty()) continue;
                if (inputItems.contains(out.getItem())) continue;
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

            // Count occurrences of each unique item
            LinkedHashMap<Item, Integer> itemCounts = new LinkedHashMap<>();
            for (ItemStack s : allStacks) {
                itemCounts.merge(s.getItem(), 1, Integer::sum);
            }

            List<Slot> result = new ArrayList<>();

            // Separate items into "multi-occurrence" (definite single-item slots)
            // and "single-occurrence" (potential tag variants)
            List<Item> singleOccurrence = new ArrayList<>();

            for (var entry : itemCounts.entrySet()) {
                if (entry.getValue() > 1) {
                    // This item appears multiple times — it occupies that many slots
                    result.add(new Slot.Single(entry.getKey(), entry.getValue()));
                } else {
                    singleOccurrence.add(entry.getKey());
                }
            }

            if (singleOccurrence.isEmpty()) {
                return result;
            }

            // Among single-occurrence items, try to find groups that share a tag.
            // This handles cases like [andesite, diorite, granite, stone, deepslate, tuff]
            // all being variants of #c:stones in one slot.
            List<Item> ungrouped = new ArrayList<>(singleOccurrence);
            while (!ungrouped.isEmpty()) {
                if (ungrouped.size() == 1) {
                    // Only one item left — it's a single slot
                    result.add(new Slot.Single(ungrouped.get(0), 1));
                    break;
                }

                // Try to find a tag that covers a subset of the remaining items
                TagGroup best = findLargestTagGroup(ungrouped);
                if (best != null && best.members.size() > 1) {
                    // Found a group — emit as a tag slot
                    result.add(new Slot.Tag(best.tag, 1));
                    ungrouped.removeAll(best.members);
                } else {
                    // No tag group found — each remaining item is its own slot
                    for (Item item : ungrouped) {
                        result.add(new Slot.Single(item, 1));
                    }
                    break;
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