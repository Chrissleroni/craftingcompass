package dev.craftingcompass.client;

import dev.craftingcompass.config.CraftingCompassConfig;
import dev.craftingcompass.list.CraftingList;
import dev.craftingcompass.list.CraftingListHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The left-edge crafting list overlay. Pure renderable, not a Screen — so it
 * can draw on top of the HUD AND on top of any open Screen without pausing
 * the game.
 *
 * State (visibility, scroll) lives here. Mutations to the underlying CraftingList
 * trigger no re-layout because layout is computed every frame from a snapshot.
 */
public final class SidebarPanel {

    public static final SidebarPanel INSTANCE = new SidebarPanel();

    private static final int WIDTH = 140;
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 18;
    private static final int PADDING = 4;

    private static final int BG_COLOR        = 0xCC101010;
    private static final int BORDER_COLOR    = 0xFF303030;
    private static final int HEADER_BG       = 0xFF1A1A1A;
    private static final int TEXT_COLOR      = 0xFFE0E0E0;
    private static final int TEXT_DIM        = 0xFF808080;
    private static final int ROW_HOVER       = 0x40FFFFFF;

    private boolean visible = false;

    @SuppressWarnings("unused") private int scrollOffset = 0;
    @SuppressWarnings("unused") private int hoveredRow = -1;

    private SidebarPanel() {}

    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { this.visible = v; }
    public void toggle() { this.visible = !this.visible; }

    public void render(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partial) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        CraftingList list = CraftingListHolder.get();
        List<DisplayRow> rows = buildRows(list);

        int maxRows = Math.max(1, CraftingCompassConfig.sidebarMaxRows);
        int visibleRows = Math.min(rows.size(), maxRows);
        int contentHeight = HEADER_HEIGHT + Math.max(ROW_HEIGHT, visibleRows * ROW_HEIGHT) + PADDING;

        int screenH = gfx.guiHeight();
        int x = 0;
        int y = (screenH - contentHeight) / 2;

        // Background + border
        gfx.fill(x, y, x + WIDTH, y + contentHeight, BG_COLOR);
        gfx.fill(x, y, x + WIDTH, y + 1, BORDER_COLOR);
        gfx.fill(x, y + contentHeight - 1, x + WIDTH, y + contentHeight, BORDER_COLOR);
        gfx.fill(x + WIDTH - 1, y, x + WIDTH, y + contentHeight, BORDER_COLOR);

        // Header
        gfx.fill(x, y, x + WIDTH, y + HEADER_HEIGHT, HEADER_BG);
        gfx.fill(x, y + HEADER_HEIGHT - 1, x + WIDTH, y + HEADER_HEIGHT, BORDER_COLOR);
        Component title = Component.literal("Crafting List").withStyle(ChatFormatting.BOLD);
        gfx.text(font, title, x + PADDING + 2, y + (HEADER_HEIGHT - font.lineHeight) / 2 + 1, TEXT_COLOR, false);

        // Rows
        int rowsAreaY = y + HEADER_HEIGHT;
        if (rows.isEmpty()) {
            String empty = "(empty)";
            int tx = x + (WIDTH - font.width(empty)) / 2;
            int ty = rowsAreaY + (ROW_HEIGHT - font.lineHeight) / 2;
            gfx.text(font, empty, tx, ty, TEXT_DIM, false);
            return;
        }

        for (int i = 0; i < visibleRows; i++) {
            DisplayRow row = rows.get(i);
            int rowY = rowsAreaY + i * ROW_HEIGHT;
            renderRow(gfx, font, row, x, rowY, mouseX, mouseY);
        }
    }

    private void renderRow(GuiGraphicsExtractor gfx, Font font, DisplayRow row, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT;
        if (hovered) {
            gfx.fill(x + 1, y, x + WIDTH - 1, y + ROW_HEIGHT, ROW_HOVER);
        }

        int iconX = x + PADDING;
        int iconY = y + (ROW_HEIGHT - 16) / 2;
        gfx.item(row.stack, iconX, iconY);
        gfx.itemDecorations(font, row.stack, iconX, iconY, null);

        int nameX = iconX + 16 + 4;
        int nameMaxWidth = WIDTH - (nameX - x) - 4 - 30;
        Component name = row.stack.getHoverName();
        String nameStr = name.getString();
        if (font.width(nameStr) > nameMaxWidth) {
            nameStr = truncate(font, nameStr, nameMaxWidth);
        }
        int nameColor = row.satisfied ? TEXT_DIM : TEXT_COLOR;
        gfx.text(font, nameStr, nameX, y + (ROW_HEIGHT - font.lineHeight) / 2, nameColor, false);

        String count = "x" + row.amount;
        int countX = x + WIDTH - PADDING - font.width(count);
        gfx.text(font, count, countX, y + (ROW_HEIGHT - font.lineHeight) / 2, nameColor, false);

        if (row.satisfied) {
            int strikeY = y + ROW_HEIGHT / 2;
            gfx.fill(iconX + 16 + 2, strikeY, x + WIDTH - PADDING, strikeY + 1, TEXT_DIM);
        }
    }

    /** Truncate a string to fit a max width, with "..." suffix. */
    private static String truncate(Font font, String s, int maxWidth) {
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (font.width(s) <= maxWidth) return s;
        // Linear shrink — simple and correct.
        for (int i = s.length() - 1; i > 0; i--) {
            String candidate = s.substring(0, i);
            if (font.width(candidate) + ellipsisWidth <= maxWidth) {
                return candidate + ellipsis;
            }
        }
        return ellipsis;
    }

    private static List<DisplayRow> buildRows(CraftingList list) {
        Map<Item, Integer> snap = list.snapshot();
        List<DisplayRow> rows = new ArrayList<>(snap.size());
        for (var e : snap.entrySet()) {
            ItemStack stack = new ItemStack(e.getKey(), 1);
            rows.add(new DisplayRow(stack, e.getValue(), false));
        }
        rows.sort(Comparator
                .comparing((DisplayRow r) -> r.satisfied)
                .thenComparing((DisplayRow r) -> -r.amount));
        return rows;
    }

    public boolean isMouseOver(int mouseX, int mouseY, int screenH) {
        if (!visible) return false;
        int rowsCount = Math.max(1, CraftingListHolder.get().size());
        int visibleRows = Math.min(rowsCount, Math.max(1, CraftingCompassConfig.sidebarMaxRows));
        int contentHeight = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + PADDING;
        int y = (screenH - contentHeight) / 2;
        return mouseX >= 0 && mouseX < WIDTH && mouseY >= y && mouseY < y + contentHeight;
    }

    private record DisplayRow(ItemStack stack, int amount, boolean satisfied) {}
}