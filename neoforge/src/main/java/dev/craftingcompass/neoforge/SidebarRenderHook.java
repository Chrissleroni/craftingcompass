package dev.craftingcompass.neoforge;

import dev.craftingcompass.CraftingCompassConstants;
import dev.craftingcompass.client.SidebarPanel;
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
        int[] mouse = currentMouse();
        SidebarPanel.INSTANCE.render(
                event.getGuiGraphics(),
                mouse[0],
                mouse[1],
                0f
        );
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        int[] mouse = currentMouse();
        SidebarPanel.INSTANCE.render(
                event.getGuiGraphics(),
                mouse[0],
                mouse[1],
                0f
        );
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