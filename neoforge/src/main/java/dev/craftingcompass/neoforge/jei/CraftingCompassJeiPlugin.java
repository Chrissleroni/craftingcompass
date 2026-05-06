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
            // Animal/entity interaction "recipes" — JEI has these for info but they
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

            List<ItemStack> outputs = extractItemStacks(supplier, RecipeIngredientRole.OUTPUT);
            if (outputs.isEmpty()) return;

            List<Slot> inputs = extractSlotsFromJei(supplier);
            if (inputs.isEmpty()) return;
            if (inputs.size() > 9) return;

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

            LinkedHashMap<Item, Integer> itemCounts = new LinkedHashMap<>();
            for (ItemStack s : allStacks) {
                itemCounts.merge(s.getItem(), 1, Integer::sum);
            }

            int uniqueItems = itemCounts.size();

            if (uniqueItems > 1) {
                TagKey<Item> commonTag = findBestCommonTag(itemCounts.keySet());
                if (commonTag != null) {
                    int slotsUsed = allStacks.size() / uniqueItems;
                    return List.of(new Slot.Tag(commonTag, Math.max(1, slotsUsed)));
                }

                List<Slot> slots = new ArrayList<>();
                for (var entry : itemCounts.entrySet()) {
                    slots.add(new Slot.Single(entry.getKey(), entry.getValue()));
                }
                return slots;
            }

            var only = itemCounts.entrySet().iterator().next();
            return List.of(new Slot.Single(only.getKey(), only.getValue()));
        }

        /**
         * Find the best tag that all items share. Improvements over the previous
         * version:
         *   - Excludes "usage" tags (horse_food, trim_materials, etc.)
         *   - Prefers tags from c: or minecraft: namespace over mod-specific tags
         *   - Prefers tags whose name semantically matches "ingredient" categories
         *     (planks, ingots, gems) over arbitrary categorization tags
         */
        private static TagKey<Item> findBestCommonTag(Set<Item> items) {
            if (items.isEmpty()) return null;

            // Build the set of tags shared by all items
            List<Set<TagKey<Item>>> perItemTags = new ArrayList<>();
            for (Item item : items) {
                Optional<Holder.Reference<Item>> holderOpt = BuiltInRegistries.ITEM.get(
                        BuiltInRegistries.ITEM.getKey(item));
                if (holderOpt.isEmpty()) return null;
                Set<TagKey<Item>> tags = new HashSet<>(holderOpt.get().tags().toList());
                perItemTags.add(tags);
            }

            // Intersect all tag sets
            Set<TagKey<Item>> common = new HashSet<>(perItemTags.get(0));
            for (int i = 1; i < perItemTags.size(); i++) {
                common.retainAll(perItemTags.get(i));
            }

            if (common.isEmpty()) return null;

            // Filter out unwanted "usage" tags
            List<TagKey<Item>> candidates = new ArrayList<>();
            for (TagKey<Item> tag : common) {
                String path = tag.location().getPath();
                boolean unwanted = false;
                for (String pattern : UNWANTED_TAG_PATTERNS) {
                    if (path.equals(pattern) || path.endsWith("/" + pattern)) {
                        unwanted = true;
                        break;
                    }
                }
                if (!unwanted) candidates.add(tag);
            }

            if (candidates.isEmpty()) return null;

            // Score each candidate tag and pick the highest
            TagKey<Item> best = null;
            int bestScore = Integer.MIN_VALUE;
            for (TagKey<Item> tag : candidates) {
                int score = scoreTag(tag);
                if (score > bestScore) {
                    bestScore = score;
                    best = tag;
                }
            }
            return best;
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