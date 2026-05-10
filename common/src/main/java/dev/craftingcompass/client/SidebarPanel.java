package dev.craftingcompass.client;

import dev.craftingcompass.config.CraftingCompassConfig;
import dev.craftingcompass.list.CraftingList;
import dev.craftingcompass.list.CraftingListHolder;
import dev.craftingcompass.list.CraftingListStorage;
import dev.craftingcompass.recipe.AggregateResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.stream.Collectors;

public final class SidebarPanel {

    public enum Tab { LIST, INGREDIENTS }

    public static final SidebarPanel INSTANCE = new SidebarPanel();

    private static final int WIDTH = 140;
    private static final int ROW_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 18;
    private static final int TAB_HEIGHT = 16;
    private static final int PADDING = 4;
    private static final int X_SIZE = 9;
    private static final int X_INSET = 2;

    private static final int CLEAR_BTN_SIZE = 10;
    private static final int CLEAR_BTN_MARGIN = 4;
    private static final int CLEAR_BG       = 0xFF402020;
    private static final int CLEAR_BG_HOVER = 0xFF8B3030;
    private static final int CLEAR_FG       = 0xFFFFE0E0;

    private static final int BG_COLOR        = 0xCC101010;
    private static final int BORDER_COLOR    = 0xFF303030;
    private static final int HEADER_BG       = 0xFF1A1A1A;
    private static final int TAB_BG          = 0xFF222222;
    private static final int TAB_BG_ACTIVE   = 0xFF353535;
    private static final int TEXT_COLOR      = 0xFFE0E0E0;
    private static final int TEXT_DIM        = 0xFF808080;
    private static final int ROW_HOVER       = 0x40FFFFFF;
    private static final int X_BG            = 0xFFB02020;
    private static final int X_BG_HOVER      = 0xFFFF3030;
    private static final int X_FG            = 0xFFFFFFFF;
    private static final int EDITOR_BG       = 0xFF202830;
    private static final int EDITOR_BORDER   = 0xFF6080A0;

    private static final long TAG_CYCLE_PERIOD_MS = 1000L;

    private final java.util.Set<Item> completedItems = new java.util.HashSet<>();
    private final java.util.Set<TagKey<Item>> completedTags = new java.util.HashSet<>();

    private long clearArmedAt = 0;
    private static final long CLEAR_ARM_WINDOW_MS = 3000L;

    private boolean visible = false;
    private Tab activeTab = Tab.LIST;
    private int scrollOffset = 0;

    // Layout snapshot, rebuilt each render frame so mouse handlers can hit-test
    // against the same coordinates that were drawn.
    private volatile LayoutSnapshot lastLayout = new LayoutSnapshot(0, 0, 0, 0, List.of(), List.of(), -1, -1, -1, -1, 0, 0, 0, 0);

    // Inline quantity editor state (right-click prompt). Null when not editing.
    private Item editingItem = null;
    private StringBuilder editorBuffer = new StringBuilder();

    private SidebarPanel() {}

    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) {
        this.visible = v;
        if (!v) cancelEditor();
    }
    public Tab getActiveTab() { return activeTab; }
    public void setActiveTab(Tab t) {
        this.activeTab = t;
        this.scrollOffset = 0;
        cancelEditor();
    }

    /**
     * If the mouse is over a row, ask the GuiGraphics to render the standard
     * Minecraft tooltip for that row's content. Called by the render hook *after*
     * the panel has rendered, so the tooltip lands on the top stratum.
     */
    public void renderTooltip(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        if (!visible) return;
        var l = lastLayout;
        if (mouseX < l.x0 || mouseX >= l.x1 || mouseY < l.y0 || mouseY >= l.y1) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Clear button tooltip
        if (mouseX >= l.clearX0 && mouseX < l.clearX1
                && mouseY >= l.clearY0 && mouseY < l.clearY1) {
            Component line = Component.literal("Click to delete list").withStyle(ChatFormatting.RED);
            showTooltip(gfx, font, List.of(line.getVisualOrderText()), mouseX, mouseY);
            return;
        }

        for (RowLayout rl : l.rows) {
            if (mouseX < rl.x0 || mouseX >= rl.x1 || mouseY < rl.y0 || mouseY >= rl.y1) continue;
            DisplayRow row = rl.row;

            if (row.itemStack != null && !row.itemStack.isEmpty()) {
                // Standard item tooltip — pull the same lines vanilla uses for inventory.
                List<Component> lines = net.minecraft.client.gui.screens.Screen
                        .getTooltipFromItem(mc, row.itemStack);
                List<FormattedCharSequence> formatted = lines.stream()
                        .map(Component::getVisualOrderText)
                        .collect(Collectors.toList());
                showTooltip(gfx, font, formatted, mouseX, mouseY);
            } else if (row.tag != null) {
                renderTagTooltip(gfx, font, row.tag, mouseX, mouseY);
            }
            return;
        }
    }

    private static void renderTagTooltip(GuiGraphicsExtractor gfx, Font font, TagKey<Item> tag, int mouseX, int mouseY) {
        Component header = Component.literal("#" + tag.location()).withStyle(ChatFormatting.GOLD);

        List<Component> lines = new ArrayList<>();
        lines.add(header);

        List<Component> memberLines = new ArrayList<>();
        int total = 0;
        for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            if (h.value() == Items.AIR) continue;
            total++;
            if (memberLines.size() < 12) {
                memberLines.add(Component.literal("• ")
                        .append(new ItemStack(h.value()).getHoverName())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        lines.addAll(memberLines);
        if (total > memberLines.size()) {
            lines.add(Component.literal("…and " + (total - memberLines.size()) + " more")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (total == 0) {
            lines.add(Component.literal("(no members)").withStyle(ChatFormatting.DARK_GRAY));
        }

        List<FormattedCharSequence> formatted = lines.stream()
                .map(Component::getVisualOrderText)
                .collect(Collectors.toList());
        showTooltip(gfx, font, formatted, mouseX, mouseY);
    }

    /**
     * Display a tooltip *replacing* whatever the underlying screen already set.
     * Without replaceExisting=true, our tooltip silently no-ops because the
     * screen has already populated GuiGraphics.deferredTooltip with its own
     * (e.g. an inventory slot's tooltip).
     */
    private static void showTooltip(GuiGraphicsExtractor gfx, Font font,
                                    List<FormattedCharSequence> lines,
                                    int mouseX, int mouseY) {
        if (lines.isEmpty()) return;
        List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components =
                lines.stream()
                        .map(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent::create)
                        .collect(Collectors.toList());
        gfx.tooltip(
                font,
                components,
                mouseX,
                mouseY,
                net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE,
                null  // no custom style
        );
    }

    public void render(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        List<DisplayRow> rows = (activeTab == Tab.LIST)
                ? buildListRows(CraftingListHolder.get())
                : buildIngredientRows(SidebarController.INSTANCE.latest());

        int maxRows = Math.max(1, CraftingCompassConfig.sidebarMaxRows);
        int totalRows = rows.size();
        int visibleRows = Math.min(totalRows, maxRows);
        if (scrollOffset > Math.max(0, totalRows - visibleRows)) {
            scrollOffset = Math.max(0, totalRows - visibleRows);
        }

        int contentHeight = HEADER_HEIGHT + TAB_HEIGHT
                + Math.max(ROW_HEIGHT, visibleRows * ROW_HEIGHT) + PADDING;

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
        if (activeTab == Tab.INGREDIENTS && SidebarController.INSTANCE.isComputing()) {
            String dot = "...";
            int dx = x + WIDTH - PADDING - font.width(dot);
            gfx.text(font, dot, dx, y + (HEADER_HEIGHT - font.lineHeight) / 2 + 1, TEXT_DIM, false);
        }

        // Clear button (right side of header, before the spinner area)
        int clearX = x + WIDTH - PADDING - CLEAR_BTN_SIZE - CLEAR_BTN_MARGIN;
        int clearY = y + (HEADER_HEIGHT - CLEAR_BTN_SIZE) / 2;
        boolean clearHover = mouseX >= clearX && mouseX < clearX + CLEAR_BTN_SIZE
                && mouseY >= clearY && mouseY < clearY + CLEAR_BTN_SIZE;
        boolean armed = clearArmedAt > 0 && (System.currentTimeMillis() - clearArmedAt) < CLEAR_ARM_WINDOW_MS;
        if (!armed) clearArmedAt = 0;

        int clearBg = (clearHover || armed) ? CLEAR_BG_HOVER : CLEAR_BG;
        gfx.fill(clearX, clearY, clearX + CLEAR_BTN_SIZE, clearY + CLEAR_BTN_SIZE, clearBg);
        // Trash can: top lid + body
        gfx.fill(clearX + 2, clearY + 2, clearX + CLEAR_BTN_SIZE - 2, clearY + 3, CLEAR_FG);
        gfx.fill(clearX + 3, clearY + 3, clearX + CLEAR_BTN_SIZE - 3, clearY + CLEAR_BTN_SIZE - 2, CLEAR_FG);
        // "Body" stripes (subtle vertical lines so it reads as a trash can)
        gfx.fill(clearX + 4, clearY + 4, clearX + 5, clearY + CLEAR_BTN_SIZE - 3, clearBg);
        gfx.fill(clearX + CLEAR_BTN_SIZE - 5, clearY + 4, clearX + CLEAR_BTN_SIZE - 4, clearY + CLEAR_BTN_SIZE - 3, clearBg);

        // Tabs
        int tabsY = y + HEADER_HEIGHT;
        int halfW = WIDTH / 2;
        int tabListX = x;
        int tabIngrX = x + halfW;
        renderTab(gfx, font, "List", tabListX, tabsY, halfW, activeTab == Tab.LIST);
        renderTab(gfx, font, "Ingredients", tabIngrX, tabsY, WIDTH - halfW, activeTab == Tab.INGREDIENTS);
        gfx.fill(x, tabsY + TAB_HEIGHT - 1, x + WIDTH, tabsY + TAB_HEIGHT, BORDER_COLOR);

        // Rows
        int rowsAreaY = tabsY + TAB_HEIGHT;
        List<RowLayout> rowLayouts = new ArrayList<>();

        if (rows.isEmpty()) {
            String empty = activeTab == Tab.LIST ? "(empty)" : "(no ingredients)";
            int tx = x + (WIDTH - font.width(empty)) / 2;
            int ty = rowsAreaY + (ROW_HEIGHT - font.lineHeight) / 2;
            gfx.text(font, empty, tx, ty, TEXT_DIM, false);
        } else {
            for (int i = 0; i < visibleRows; i++) {
                int rowIdx = i + scrollOffset;
                if (rowIdx >= totalRows) break;
                DisplayRow row = rows.get(rowIdx);
                int rowY = rowsAreaY + i * ROW_HEIGHT;
                renderRow(gfx, font, row, x, rowY, mouseX, mouseY);
                rowLayouts.add(new RowLayout(row, x, rowY, x + WIDTH, rowY + ROW_HEIGHT));
            }
        }

        lastLayout = new LayoutSnapshot(
                x, y, x + WIDTH, y + contentHeight,
                List.of(
                        new TabLayout(Tab.LIST, tabListX, tabsY, tabListX + halfW, tabsY + TAB_HEIGHT),
                        new TabLayout(Tab.INGREDIENTS, tabIngrX, tabsY, tabIngrX + (WIDTH - halfW), tabsY + TAB_HEIGHT)
                ),
                rowLayouts,
                rowsAreaY,
                rowsAreaY + visibleRows * ROW_HEIGHT,
                totalRows,
                visibleRows,
                clearX, clearY, clearX + CLEAR_BTN_SIZE, clearY + CLEAR_BTN_SIZE
        );
    }

    private void renderTab(GuiGraphicsExtractor gfx, Font font, String label, int x, int y, int w, boolean active) {
        int bg = active ? TAB_BG_ACTIVE : TAB_BG;
        int textColor = active ? TEXT_COLOR : TEXT_DIM;
        gfx.fill(x, y, x + w, y + TAB_HEIGHT, bg);
        if (!active) gfx.fill(x, y + TAB_HEIGHT - 1, x + w, y + TAB_HEIGHT, BORDER_COLOR);
        gfx.fill(x + w - 1, y, x + w, y + TAB_HEIGHT, BORDER_COLOR);
        int tx = x + (w - font.width(label)) / 2;
        int ty = y + (TAB_HEIGHT - font.lineHeight) / 2;
        gfx.text(font, label, tx, ty, textColor, false);
    }

    private void renderRow(GuiGraphicsExtractor gfx, Font font, DisplayRow row, int x, int y, int mouseX, int mouseY) {
        boolean rowHovered = mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT;
        if (rowHovered) {
            gfx.fill(x + 1, y, x + WIDTH - 1, y + ROW_HEIGHT, ROW_HOVER);
        }

        // Inline quantity editor, replaces row content for the editing item
        if (editingItem != null && row.itemStack != null && row.itemStack.getItem() == editingItem) {
            renderEditor(gfx, font, x, y);
            return;
        }

        int iconX = x + PADDING;
        int iconY = y + (ROW_HEIGHT - 16) / 2;
        ItemStack displayStack = row.resolveDisplayStack();
        if (!displayStack.isEmpty()) {
            gfx.item(displayStack, iconX, iconY);
            gfx.itemDecorations(font, displayStack, iconX, iconY, null);
        }

        int nameX = iconX + 16 + 4;
        int nameMaxWidth = WIDTH - (nameX - x) - 4 - 30;
        String nameStr = row.label;
        if (font.width(nameStr) > nameMaxWidth) {
            nameStr = truncate(font, nameStr, nameMaxWidth);
        }
        int nameColor = row.satisfied ? TEXT_DIM : TEXT_COLOR;
        gfx.text(font, nameStr, nameX, y + (ROW_HEIGHT - font.lineHeight) / 2, nameColor, false);

        int have = (activeTab == Tab.INGREDIENTS) ? countInInventory(row) : 0;
        String count = (have > 0)
                ? "x" + row.amount + " (" + have + ")"
                : "x" + row.amount;
        int countX = x + WIDTH - PADDING - font.width(count);
        int countColor = row.satisfied ? TEXT_DIM : TEXT_COLOR;
        gfx.text(font, count, countX, y + (ROW_HEIGHT - font.lineHeight) / 2, countColor, false);

        if (row.satisfied) {
            int strikeY = y + ROW_HEIGHT / 2;
            gfx.fill(iconX + 16 + 2, strikeY, x + WIDTH - PADDING, strikeY + 1, TEXT_DIM);
        }

        // X button, only on hover, only on List tab, only for items (not tags)
        if (rowHovered && activeTab == Tab.LIST && row.itemStack != null) {
            int xBtnX = x + X_INSET;
            int xBtnY = y + X_INSET;
            boolean xHover = mouseX >= xBtnX && mouseX < xBtnX + X_SIZE
                    && mouseY >= xBtnY && mouseY < xBtnY + X_SIZE;
            gfx.fill(xBtnX, xBtnY, xBtnX + X_SIZE, xBtnY + X_SIZE, xHover ? X_BG_HOVER : X_BG);
            // Draw an "x" shape with two diagonal pixel lines
            for (int i = 2; i < X_SIZE - 2; i++) {
                gfx.fill(xBtnX + i, xBtnY + i, xBtnX + i + 1, xBtnY + i + 1, X_FG);
                gfx.fill(xBtnX + (X_SIZE - 1 - i), xBtnY + i, xBtnX + (X_SIZE - 1 - i) + 1, xBtnY + i + 1, X_FG);
            }
        }
    }

    private void renderEditor(GuiGraphicsExtractor gfx, Font font, int x, int y) {
        int eX = x + PADDING;
        int eY = y + 2;
        int eW = WIDTH - PADDING * 2;
        int eH = ROW_HEIGHT - 4;
        gfx.fill(eX, eY, eX + eW, eY + eH, EDITOR_BG);
        gfx.fill(eX, eY, eX + eW, eY + 1, EDITOR_BORDER);
        gfx.fill(eX, eY + eH - 1, eX + eW, eY + eH, EDITOR_BORDER);
        gfx.fill(eX, eY, eX + 1, eY + eH, EDITOR_BORDER);
        gfx.fill(eX + eW - 1, eY, eX + eW, eY + eH, EDITOR_BORDER);

        String text = editorBuffer.length() == 0 ? "_" : editorBuffer.toString() + "_";
        gfx.text(font, "Qty: " + text, eX + 4, eY + (eH - font.lineHeight) / 2, TEXT_COLOR, false);
    }

    private static String truncate(Font font, String s, int maxWidth) {
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (font.width(s) <= maxWidth) return s;
        for (int i = s.length() - 1; i > 0; i--) {
            String candidate = s.substring(0, i);
            if (font.width(candidate) + ellipsisWidth <= maxWidth) {
                return candidate + ellipsis;
            }
        }
        return ellipsis;
    }

    private static List<DisplayRow> buildListRows(CraftingList list) {
        Map<Item, Integer> snap = list.snapshot();
        List<DisplayRow> rows = new ArrayList<>(snap.size());
        for (var e : snap.entrySet()) {
            ItemStack stack = new ItemStack(e.getKey(), 1);
            rows.add(DisplayRow.ofItem(stack, e.getValue(), false));
        }
        rows.sort(Comparator
                .comparing((DisplayRow r) -> r.satisfied)
                .thenComparing((DisplayRow r) -> -r.amount));
        return rows;
    }

    private List<DisplayRow> buildIngredientRows(AggregateResolver.Aggregate agg) {
        var snap = dev.craftingcompass.inventory.InventoryCache.INSTANCE.get();
        boolean invCheck = dev.craftingcompass.config.CraftingCompassConfig.inventoryCheckEnabled;

        List<DisplayRow> rows = new ArrayList<>(agg.items().size() + agg.tags().size());
        for (var e : agg.items()) {
            ItemStack stack = new ItemStack(e.getKey(), 1);
            boolean manualDone = completedItems.contains(e.getKey());
            boolean haveEnough = invCheck && snap.countOf(e.getKey()) >= e.getValue();
            rows.add(DisplayRow.ofItem(stack, e.getValue(), manualDone || haveEnough));
        }
        for (var e : agg.tags()) {
            boolean manualDone = completedTags.contains(e.getKey());
            boolean haveEnough = invCheck && snap.countOfTag(e.getKey()) >= e.getValue();
            rows.add(DisplayRow.ofTag(e.getKey(), e.getValue(), manualDone || haveEnough));
        }
        rows.sort(Comparator
                .comparing((DisplayRow r) -> r.satisfied)
                .thenComparing((DisplayRow r) -> -r.amount));
        return rows;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        if (!visible) return false;
        var l = lastLayout;
        return mouseX >= l.x0 && mouseX < l.x1 && mouseY >= l.y0 && mouseY < l.y1;
    }

    /**
     * Mouse click dispatch. Returns true if the click was consumed by the panel
     * (caller should cancel the event so it doesn't reach the underlying screen).
     *
     * @param button GLFW mouse button (0=left, 1=right)
     */
    public boolean handleMouseClick(int mouseX, int mouseY, int button) {
        if (!visible) return false;
        var l = lastLayout;
        if (!isMouseOver(mouseX, mouseY)) {
            cancelEditor();
            return false;
        }

        if (mouseX >= l.clearX0 && mouseX < l.clearX1
                && mouseY >= l.clearY0 && mouseY < l.clearY1) {
            if (button == 0) handleClearClick();
            return true;
        }

        // Tab clicks
        for (TabLayout tab : l.tabs) {
            if (mouseX >= tab.x0 && mouseX < tab.x1 && mouseY >= tab.y0 && mouseY < tab.y1) {
                if (button == 0) {
                    setActiveTab(tab.tab);
                }
                return true;
            }
        }

        // Row clicks
        for (RowLayout rl : l.rows) {
            if (mouseX < rl.x0 || mouseX >= rl.x1 || mouseY < rl.y0 || mouseY >= rl.y1) continue;
            DisplayRow row = rl.row;

            // X button (List tab only)
            int xBtnX = rl.x0 + X_INSET;
            int xBtnY = rl.y0 + X_INSET;
            boolean onX = activeTab == Tab.LIST
                    && mouseX >= xBtnX && mouseX < xBtnX + X_SIZE
                    && mouseY >= xBtnY && mouseY < xBtnY + X_SIZE;

            if (button == 0 && onX) {
                CraftingListHolder.get().remove(row.itemStack.getItem());
                return true;
            }
            if (button == 1 && activeTab == Tab.LIST && row.itemStack != null) {
                // Right-click on List tab: open quantity editor
                editingItem = row.itemStack.getItem();
                editorBuffer.setLength(0);
                editorBuffer.append(CraftingListHolder.get().quantity(editingItem));
                return true;
            }
            if (button == 1 && activeTab == Tab.INGREDIENTS) {
                // Right-click on Ingredients tab: toggle completion mark
                if (row.itemStack != null) {
                    Item it = row.itemStack.getItem();
                    if (!completedItems.add(it)) completedItems.remove(it);
                } else if (row.tag != null) {
                    if (!completedTags.add(row.tag)) completedTags.remove(row.tag);
                }
                CraftingListStorage.scheduleSave(this::snapshot);
                return true;
            }
            return true; // consume all clicks that landed on a row
        }
        return true; // consume any click inside the panel even if it didn't hit anything
    }

    public boolean handleMouseScroll(int mouseX, int mouseY, double delta) {
        if (!visible || !isMouseOver(mouseX, mouseY)) return false;
        int totalRows = lastLayout.totalRows;
        int visibleRows = lastLayout.visibleRows;
        if (totalRows <= visibleRows) return true;
        if (delta > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        else if (delta < 0) scrollOffset = Math.min(totalRows - visibleRows, scrollOffset + 1);
        return true;
    }

    public boolean handleKeyPress(int keyCode) {
        if (editingItem == null) return false;
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER -> {
                commitEditor();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> {
                cancelEditor();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                if (editorBuffer.length() > 0) editorBuffer.deleteCharAt(editorBuffer.length() - 1);
                return true;
            }
        }
        return false;
    }

    public boolean handleCharTyped(int codePoint) {
        if (editingItem == null) return false;
        if (codePoint >= '0' && codePoint <= '9' && editorBuffer.length() < 7) {
            editorBuffer.append((char) codePoint);
            return true;
        }
        return false;
    }

    private void handleClearClick() {
        CraftingListHolder.get().clear();
        completedItems.clear();
        completedTags.clear();
        CraftingListStorage.scheduleSave(this::snapshot);
    }

    private void commitEditor() {
        if (editingItem == null) return;
        try {
            int qty = editorBuffer.length() == 0 ? 0 : Integer.parseInt(editorBuffer.toString());
            CraftingListHolder.get().setQuantity(editingItem, qty);
        } catch (NumberFormatException ignored) {}
        cancelEditor();
    }

    private void cancelEditor() {
        editingItem = null;
        editorBuffer.setLength(0);
    }

    private static int countInInventory(DisplayRow row) {
        if (!dev.craftingcompass.config.CraftingCompassConfig.inventoryCheckEnabled) return 0;
        var snap = dev.craftingcompass.inventory.InventoryCache.INSTANCE.get();
        if (row.itemStack != null) return snap.countOf(row.itemStack.getItem());
        if (row.tag != null) return snap.countOfTag(row.tag);
        return 0;
    }

    private record DisplayRow(ItemStack itemStack, TagKey<Item> tag, String label, int amount, boolean satisfied) {

        static DisplayRow ofItem(ItemStack stack, int amount, boolean satisfied) {
            return new DisplayRow(stack, null, stack.getHoverName().getString(), amount, satisfied);
        }

        static DisplayRow ofTag(TagKey<Item> tag, int amount, boolean satisfied) {
            return new DisplayRow(null, tag, "#" + tag.location().getPath(), amount, satisfied);
        }

        ItemStack resolveDisplayStack() {
            if (itemStack != null) return itemStack;
            if (tag == null) return ItemStack.EMPTY;
            List<Holder<Item>> members = new ArrayList<>();
            BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(members::add);
            if (members.isEmpty()) return ItemStack.EMPTY;
            int idx = (int) ((System.currentTimeMillis() / TAG_CYCLE_PERIOD_MS) % members.size());
            return new ItemStack(members.get(idx).value(), 1);
        }
    }

    public CraftingListStorage.SaveData snapshot() {
        return new CraftingListStorage.SaveData(
                CraftingListHolder.get().snapshot(),
                new java.util.HashSet<>(completedItems),
                new java.util.HashSet<>(completedTags)
        );
    }

    public void restore(CraftingListStorage.SaveData data) {
        completedItems.clear();
        completedItems.addAll(data.completedItems());
        completedTags.clear();
        completedTags.addAll(data.completedTags());
    }

    private record TabLayout(Tab tab, int x0, int y0, int x1, int y1) {}
    private record RowLayout(DisplayRow row, int x0, int y0, int x1, int y1) {}
    private record LayoutSnapshot(
            int x0, int y0, int x1, int y1,
            List<TabLayout> tabs,
            List<RowLayout> rows,
            int rowsTop, int rowsBottom,
            int totalRows, int visibleRows,
            int clearX0, int clearY0, int clearX1, int clearY1
    ) {}
}