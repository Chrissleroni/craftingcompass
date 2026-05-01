package dev.craftingcompass.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import dev.craftingcompass.CraftingCompassConstants;
import dev.craftingcompass.platform.Platform;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CraftingCompassConstants.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {

    public static final KeyMapping ADD_TO_LIST = new KeyMapping(
            "key.craftingcompass.add_to_list",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL,
            KeyMapping.Category.register(Identifier.parse("key.categories.craftingcompass"))
    );

    private ClientSetup() {}

    public static void init() {
        System.out.println("[CraftingCompass] Loader: " + Platform.get().loaderName());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ADD_TO_LIST);
    }
}