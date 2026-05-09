package dev.craftingcompass.list;

import dev.craftingcompass.client.SidebarPanel;

public final class CraftingListHolder {
    private static final CraftingList INSTANCE = new CraftingList();

    static {
        INSTANCE.addListener(list -> CraftingListStorage.scheduleSave(SidebarPanel.INSTANCE::snapshot));
    }

    private CraftingListHolder() {}
    public static CraftingList get() { return INSTANCE; }
}
