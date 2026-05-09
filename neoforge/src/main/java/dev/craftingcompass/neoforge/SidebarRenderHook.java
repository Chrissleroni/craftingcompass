package dev.craftingcompass.neoforge;

import dev.craftingcompass.CraftingCompassConstants;
import dev.craftingcompass.client.SidebarPanel;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Renders the SidebarPanel:
 *   - Over the in-game HUD when no Screen is open (RenderGuiEvent.Post).
 *   - Over any open Screen (ScreenEvent.Render.Post).
 * Combined, this gives the panel "always visible" semantics without making
 * it a Screen (which would pause singleplayer).
 */
@EventBusSubscriber(modid = CraftingCompassConstants.MOD_ID, value = Dist.CLIENT)
public final class SidebarRenderHook {

    private SidebarRenderHook() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        SidebarPanel.INSTANCE.render(
                event.getGuiGraphics(),
                -1,
                1,
                0f
        );
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (shouldHideForScreen(event.getScreen())) return;
        int[] mouse = currentMouse();
        SidebarPanel.INSTANCE.render(
                event.getGuiGraphics(),
                mouse[0],
                mouse[1],
                0f
        );
    }

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (shouldHideForScreen(event.getScreen())) return;
        int mx = (int) event.getMouseX();
        int my = (int) event.getMouseY();
        if (SidebarPanel.INSTANCE.handleMouseClick(mx, my, event.getButton())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (shouldHideForScreen(event.getScreen())) return;
        int mx = (int) event.getMouseX();
        int my = (int) event.getMouseY();
        // 26.1's API likely has getScrollDeltaY(); fall back to getScrollDelta() if your IDE flags
        double delta = event.getScrollDeltaY();
        if (SidebarPanel.INSTANCE.handleMouseScroll(mx, my, delta)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (shouldHideForScreen(event.getScreen())) return;
        if (SidebarPanel.INSTANCE.handleKeyPress(event.getKeyCode())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (shouldHideForScreen(event.getScreen())) return;
        if (SidebarPanel.INSTANCE.handleCharTyped(event.getCodePoint())) {
            event.setCanceled(true);
        }
    }

    private static boolean shouldHideForScreen(Screen screen) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return true;  // any menu before/between worlds
        if (screen == null) return false;
        if (screen instanceof PauseScreen) return true;
        if (screen instanceof OptionsSubScreen) return true;
        if (screen instanceof OptionsScreen) return true;
        if (screen instanceof ChatScreen) return true;
        return false;
    }

    private static int[] currentMouse() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var win = mc.getWindow();
        double sx = Math.max(1.0, win.getScreenWidth());
        double sy = Math.max(1.0, win.getScreenHeight());
        int mx = (int) (mc.mouseHandler.xpos() * win.getGuiScaledWidth() / sx);
        int my = (int) (mc.mouseHandler.ypos() * win.getGuiScaledHeight() / sy);
        return new int[]{mx, my};
    }
}