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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            var recipeManager = runtime.getRecipeManager();

            recipeManager.createRecipeLookup(type).get().forEach(recipe -> {
                IIngredientSupplier supplier = recipeManager.getRecipeIngredients(category, recipe);

                List<ItemStack> outputs = extractStacks(supplier, RecipeIngredientRole.OUTPUT);
                if (outputs.isEmpty()) return;

                List<ItemStack> inputs = extractStacks(supplier, RecipeIngredientRole.INPUT);

                // Skip non-crafting recipes: too many inputs or circular (output in inputs)
                if (inputs.size() > 9) return;

                java.util.Set<Item> inputItems = new java.util.HashSet<>();
                for (ItemStack s : inputs) inputItems.add(s.getItem());

                for (ItemStack out : outputs) {
                    if (out.isEmpty()) continue;
                    // Skip if the output item is also one of the inputs — wrong recipe type
                    if (inputItems.contains(out.getItem())) continue;
                    FlatRecipe flat = new FlatRecipe(inputs, out);
                    target.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(flat);
                }
            });
        }

        private static List<ItemStack> extractStacks(IIngredientSupplier supplier, RecipeIngredientRole role) {
            // Deduplicate by Item - IIngredientSupplier returns one entry per variant
            // (e.g. 8 plank types × 8 slots = 64 entries for a chest). We only need
            // one representative per unique Item for the resolver.
            java.util.LinkedHashMap<Item, ItemStack> seen = new java.util.LinkedHashMap<>();
            for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
                typed.getIngredient(VanillaTypes.ITEM_STACK).ifPresent(s -> seen.putIfAbsent(s.getItem(), s));
            }
            return new ArrayList<>(seen.values());
        }
    }
}
