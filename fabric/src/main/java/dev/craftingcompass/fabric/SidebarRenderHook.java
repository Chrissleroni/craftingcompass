package dev.craftingcompass.fabric;

import dev.craftingcompass.client.SidebarPanel;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric render + input hooks for the SidebarPanel.
 *
 * 26.1-specific notes:
 *  - HudRenderCallback → HudElementRegistry
 *  - ScreenEvents.afterRender → afterExtract (5-arg: screen, gfx, x, y, tickProgress)
 *  - Mouse / key events use MouseButtonEvent / KeyEvent value objects
 *  - KeyMapping.isActiveAndMatches is a NeoForge extension; use vanilla matches(key, scancode)
 *  - charTyped has no fabric-screen-api-v1 event; qty editor char input deferred
 */
public final class SidebarRenderHook {

    private static final Identifier HUD_LAYER_ID =
            Identifier.fromNamespaceAndPath("craftingcompass", "sidebar");

    private SidebarRenderHook() {}

    public static void register() {
        // In-world HUD render (no Screen open). Mouse position not available; pass -1,-1.
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                HUD_LAYER_ID,
                (gfx, deltaTracker) -> SidebarPanel.INSTANCE.render(gfx, -1, -1)
        );

        // Per-screen hooks. Registered fresh each time a screen is initialised.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (shouldHideForScreen(screen)) return;

            // Render sidebar + tooltip after the screen finishes extracting its own draw state
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, tickProgress) -> {
                SidebarPanel.INSTANCE.render(graphics, mouseX, mouseY);
                SidebarPanel.INSTANCE.renderTooltip(graphics, mouseX, mouseY);
            });

            // Mouse click — single event object, x() / y() / button()
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                int mx = (int) event.x();
                int my = (int) event.y();
                if (SidebarPanel.INSTANCE.handleMouseClick(mx, my, event.button())) {
                    return false;
                }
                return true;
            });

            // Mouse scroll — raw doubles
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, hScroll, vScroll) -> {
                if (SidebarPanel.INSTANCE.handleMouseScroll((int) mouseX, (int) mouseY, vScroll)) {
                    return false;
                }
                return true;
            });

            // Key press — KeyEvent has .key() .scancode() .modifiers()
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (ClientSetup.ADD_TO_LIST.matches(event)) {
                    boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
                    KeyHandler.onAddPressedInScreen(shift);
                    return false;
                }
                if (ClientSetup.TOGGLE_SIDEBAR.matches(event)) {
                    cycleSidebarFromScreen();
                    return false;
                }
                if (SidebarPanel.INSTANCE.handleKeyPress(event.key())) {
                    return false;
                }
                return true;
            });
        });
    }

    private static void cycleSidebarFromScreen() {
        var panel = SidebarPanel.INSTANCE;
        if (!panel.isVisible()) {
            panel.setVisible(true);
            panel.setActiveTab(SidebarPanel.Tab.LIST);
        } else if (panel.getActiveTab() == SidebarPanel.Tab.LIST) {
            panel.setActiveTab(SidebarPanel.Tab.INGREDIENTS);
        } else {
            panel.setVisible(false);
        }
    }

    private static boolean shouldHideForScreen(Screen screen) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return true;
        if (screen == null) return false;
        if (screen instanceof PauseScreen) return true;
        if (screen instanceof OptionsSubScreen) return true;
        if (screen instanceof OptionsScreen) return true;
        if (screen instanceof ChatScreen) return true;
        return false;
    }
}