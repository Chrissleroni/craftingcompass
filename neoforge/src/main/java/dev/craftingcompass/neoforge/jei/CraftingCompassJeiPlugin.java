package dev.craftingcompass.neoforge.jei;

import dev.craftingcompass.CraftingCompassConstants;
import dev.craftingcompass.recipe.RecipeProvider;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.List;

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
        PROVIDER = new StubProvider();
    }

    @Override
    public void onRuntimeUnavailable() {
        RUNTIME = null;
        PROVIDER = null;
    }

    /** Resolver-side accessor. Returns null if JEI isn't ready yet. */
    public static RecipeProvider provider() {
        return PROVIDER;
    }

    public static IJeiRuntime runtime() {
        return RUNTIME;
    }

    /** Empty placeholder so the plugin compiles. We'll fill in JEI traversal as a next step. */
    private static final class StubProvider implements RecipeProvider {
        @Override public List<FlatRecipe> recipesProducing(Item item) { return List.of(); }
        @Override public int totalRecipeCount() { return 0; }
    }
}