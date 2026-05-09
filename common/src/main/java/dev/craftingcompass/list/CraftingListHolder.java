package dev.craftingcompass.list;

public final class CraftingListHolder {
    private static final CraftingList INSTANCE = new CraftingList();

    static {
        INSTANCE.addListener(CraftingListStorage::scheduleSave);
    }

    private CraftingListHolder() {}
    public static CraftingList get() { return INSTANCE; }
}
