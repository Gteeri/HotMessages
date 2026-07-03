package ru.sitbix.hotmessages.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class HotMessagesHolder implements InventoryHolder {
    private final View view;
    private final int page;
    private Inventory inventory;

    public HotMessagesHolder(View view) {
        this(view, 0);
    }

    public HotMessagesHolder(View view, int page) {
        this.view = view;
        this.page = page;
    }

    public View view() {
        return view;
    }

    public int page() {
        return page;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public enum View {
        MAIN,
        SETTINGS,
        DOMAINS,
        BANNED_WORDS,
        LOGS
    }
}
