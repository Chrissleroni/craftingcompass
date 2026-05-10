package dev.craftingcompass.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import dev.craftingcompass.CraftingCompassConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(CraftingCompassConstants.MOD_ID, "general")
    );

    public static final KeyMapping ADD_TO_LIST = new KeyMapping(
            "key.craftingcompass.add_to_list",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL,
            CATEGORY
    );

    public static final KeyMapping TOGGLE_SIDEBAR = new KeyMapping(
            "key.craftingcompass.toggle_sidebar",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    );

    private ClientSetup() {}

    public static void init() {
        KeyMappingHelper.registerKeyMapping(ADD_TO_LIST);
        KeyMappingHelper.registerKeyMapping(TOGGLE_SIDEBAR);
    }
}