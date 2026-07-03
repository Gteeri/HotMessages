package ru.sitbix.hotmessages.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.sitbix.hotmessages.HotMessagesPlugin;
import ru.sitbix.hotmessages.domain.DomainEntry;
import ru.sitbix.hotmessages.log.LogEntry;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiManager implements Listener {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    private static final int DOMAINS_PER_PAGE = 45;

    private final HotMessagesPlugin plugin;
    private final Map<UUID, Boolean> awaitingInput = new ConcurrentHashMap<>();
    private final Map<UUID, String> inputAction = new ConcurrentHashMap<>();

    public GuiManager(HotMessagesPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        plugin.soundService().play(player, "gui-open");
        HotMessagesHolder holder = new HotMessagesHolder(HotMessagesHolder.View.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, 27, colorless(plugin.messages().rawText("gui-title")));
        holder.inventory(inventory);

        boolean enabled = plugin.filterConfig().settings().enabled();
        inventory.setItem(10, item(
            enabled ? plugin.filterConfig().guiMaterials().enabled() : plugin.filterConfig().guiMaterials().disabled(),
            enabled ? "&aФильтр включён" : "&cФильтр выключен",
            List.of("&7Нажмите, чтобы переключить состояние.")
        ));
        inventory.setItem(12, item(
            plugin.filterConfig().guiMaterials().reload(),
            "&eПерезагрузить",
            List.of("&7Обновить config.yml без рестарта.")
        ));
        inventory.setItem(14, item(
            plugin.filterConfig().guiMaterials().logs(),
            "&bПоследние логи",
            List.of("&7Открыть последние блокировки.")
        ));
        inventory.setItem(16, item(
            Material.REDSTONE,
            "&eНастройки",
            List.of("&7Домены, запрещённые слова и другие настройки.")
        ));

        player.openInventory(inventory);
    }

    public void openSettings(Player player) {
        HotMessagesHolder holder = new HotMessagesHolder(HotMessagesHolder.View.SETTINGS);
        Inventory inventory = Bukkit.createInventory(holder, 27, colorless(plugin.messages().rawText("gui-settings-title")));
        holder.inventory(inventory);

        int domainCount = plugin.domainService().all().size();
        inventory.setItem(11, item(
            Material.BOOK,
            "&eСписок доменов",
            List.of("&7Заблокированные домены: &f" + domainCount, "&7Нажмите для управления.")
        ));
        inventory.setItem(15, item(
            Material.BARRIER,
            "&cЗапрещённые слова",
            List.of("&7Управление чёрным списком слов.")
        ));
        inventory.setItem(22, item(
            Material.ARROW,
            "&eНазад",
            List.of("&7Вернуться в главное меню.")
        ));

        player.openInventory(inventory);
    }

    public void openDomains(Player player, int page) {
        List<DomainEntry> allDomains = plugin.domainService().all();
        int totalPages = Math.max(1, (int) Math.ceil((double) allDomains.size() / DOMAINS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        HotMessagesHolder holder = new HotMessagesHolder(HotMessagesHolder.View.DOMAINS, page);
        Inventory inventory = Bukkit.createInventory(holder, 54, colorless(plugin.messages().rawText("gui-domains-title") + " (" + (page + 1) + "/" + totalPages + ")"));
        holder.inventory(inventory);

        int start = page * DOMAINS_PER_PAGE;
        int end = Math.min(start + DOMAINS_PER_PAGE, allDomains.size());
        int slot = 0;

        for (int i = start; i < end; i++) {
            DomainEntry entry = allDomains.get(i);
            boolean isDefault = plugin.domainService().isDefault(entry.domain());
            Material mat = entry.enabled() ? Material.LIME_DYE : Material.GRAY_DYE;
            List<String> lore = new ArrayList<>();
            lore.add("&7Статус: " + (entry.enabled() ? "&aвключён" : "&cвыключен"));
            if (isDefault) {
                lore.add("&7Тип: &fвстроенный");
            }
            lore.add("");
            lore.add("&eЛКМ &7- вкл/выкл");
            if (!isDefault) {
                lore.add("&cПКМ &7- удалить");
            }
            inventory.setItem(slot, item(
                mat,
                (entry.enabled() ? "&a" : "&c") + entry.domain() + (isDefault ? " &8[default]" : ""),
                lore,
                domainTag(entry.domain())
            ));
            slot++;
        }

        if (allDomains.isEmpty()) {
            inventory.setItem(22, item(Material.PAPER, "&7Доменов пока нет", List.of("&8Нажмите + чтобы добавить первый домен.")));
        }

        inventory.setItem(45, item(Material.ARROW, "&eНазад", List.of("&7Вернуться в настройки.")));
        inventory.setItem(49, item(Material.LIME_DYE, "&aДобавить домен", List.of("&7Нажмите и введите домен в чат.")));
        inventory.setItem(50, item(Material.ARROW, "&e<", List.of("&7Предыдущая страница.")));
        inventory.setItem(51, item(Material.ARROW, "&e>", List.of("&7Следующая страница.")));

        player.openInventory(inventory);
    }

    public void openBannedWords(Player player) {
        HotMessagesHolder holder = new HotMessagesHolder(HotMessagesHolder.View.BANNED_WORDS);
        Inventory inventory = Bukkit.createInventory(holder, 54, colorless(plugin.messages().rawText("gui-banned-words-title")));
        holder.inventory(inventory);

        List<String> fragments = plugin.filterConfig().blacklistFragments();
        int slot = 0;
        for (String fragment : fragments) {
            if (slot >= 45) break;
            inventory.setItem(slot, item(
                Material.RED_DYE,
                "&c" + fragment,
                List.of("&7Нажмите, чтобы удалить.", "&eПКМ &7- удалить")
            ));
            slot++;
        }

        if (fragments.isEmpty()) {
            inventory.setItem(22, item(Material.PAPER, "&7Запрещённых слов нет", List.of()));
        }

        inventory.setItem(45, item(Material.ARROW, "&eНазад", List.of("&7Вернуться в настройки.")));
        inventory.setItem(49, item(Material.LIME_DYE, "&aДобавить слово", List.of("&7Нажмите и введите слово в чат.")));

        player.openInventory(inventory);
    }

    public void openLogs(Player player) {
        HotMessagesHolder holder = new HotMessagesHolder(HotMessagesHolder.View.LOGS);
        Inventory inventory = Bukkit.createInventory(holder, 54, colorless(plugin.messages().rawText("gui-logs-title")));
        holder.inventory(inventory);

        List<LogEntry> logs = plugin.logService().recent();
        if (logs.isEmpty()) {
            inventory.setItem(22, item(Material.PAPER, "&7Логов пока нет", List.of("&8Фильтр ещё ничего не блокировал.")));
        } else {
            int slot = 0;
            for (LogEntry log : logs) {
                if (slot >= 45) break;
                inventory.setItem(slot++, logItem(log));
            }
        }

        inventory.setItem(49, item(Material.ARROW, "&eНазад", List.of("&7Вернуться в главное меню.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HotMessagesHolder holder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!player.hasPermission(plugin.filterConfig().settings().adminPermission())) {
            player.closeInventory();
            player.sendMessage(plugin.messages().component("no-permission"));
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0) return;
        switch (holder.view()) {
            case MAIN -> handleMainClick(player, slot);
            case SETTINGS -> handleSettingsClick(player, slot);
            case DOMAINS -> handleDomainsClick(player, slot, holder.page(), event.isRightClick());
            case BANNED_WORDS -> handleBannedWordsClick(player, slot, event.isRightClick());
            case LOGS -> {
                if (slot == 49) openMain(player);
            }
        }
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (awaitingInput.remove(player.getUniqueId()) == null) {
            return;
        }

        event.setCancelled(true);
        String action = inputAction.remove(player.getUniqueId());
        if (action == null) return;

        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer plain =
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        String text = plain.serialize(event.message()).trim();

        if (action.startsWith("add-domain:")) {
            if (text.isBlank()) {
                player.sendMessage(plugin.messages().component("gui-domain-empty"));
                return;
            }
            if (plugin.domainService().add(text)) {
                player.sendMessage(plugin.messages().component("gui-domain-added", Map.of("domain", text)));
            } else {
                player.sendMessage(plugin.messages().component("gui-domain-exists", Map.of("domain", text)));
            }
            openDomains(player, 0);
        } else if (action.startsWith("add-word:")) {
            if (text.isBlank()) {
                player.sendMessage(plugin.messages().component("gui-word-empty"));
                return;
            }
            plugin.configService().addBlacklistFragment(text);
            plugin.reloadHotMessages();
            player.sendMessage(plugin.messages().component("gui-word-added", Map.of("word", text)));
            openBannedWords(player);
        }
    }

    public void cancelInput(UUID uuid) {
        awaitingInput.remove(uuid);
        inputAction.remove(uuid);
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // Small delay to allow re-opening of inventory from within click handlers
            player.getScheduler().runDelayed(plugin, task -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof HotMessagesHolder) {
                    return; // Still in a HotMessages GUI, don't cancel
                }
                cancelInput(player.getUniqueId());
            }, null, 2L);
        }
    }

    private void handleMainClick(Player player, int slot) {
        plugin.soundService().play(player, "gui-click");
        if (slot == 10) {
            boolean enabled = plugin.getConfig().getBoolean("settings.enabled", true);
            plugin.getConfig().set("settings.enabled", !enabled);
            plugin.saveConfig();
            plugin.reloadHotMessages();
            plugin.soundService().play(player, !enabled ? "toggle-on" : "toggle-off");
            player.sendMessage(plugin.messages().component(!enabled ? "enabled" : "disabled"));
            openMain(player);
        } else if (slot == 12) {
            plugin.reloadHotMessages();
            player.sendMessage(plugin.messages().component("reloaded"));
            openMain(player);
        } else if (slot == 14) {
            openLogs(player);
        } else if (slot == 16) {
            openSettings(player);
        }
    }

    private void handleSettingsClick(Player player, int slot) {
        plugin.soundService().play(player, "gui-click");
        if (slot == 11) {
            openDomains(player, 0);
        } else if (slot == 15) {
            openBannedWords(player);
        } else if (slot == 22) {
            openMain(player);
        }
    }

    private void handleDomainsClick(Player player, int slot, int page, boolean rightClick) {
        plugin.soundService().play(player, "gui-click");
        if (slot == 45) {
            openSettings(player);
            return;
        }
        if (slot == 49) {
            awaitingInput.put(player.getUniqueId(), true);
            inputAction.put(player.getUniqueId(), "add-domain:");
            player.closeInventory();
            player.sendMessage(plugin.messages().component("gui-domain-input"));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> cancelInput(player.getUniqueId()), 600L); // 30 seconds
            return;
        }
        if (slot == 50) {
            openDomains(player, page - 1);
            return;
        }
        if (slot == 51) {
            openDomains(player, page + 1);
            return;
        }

        List<DomainEntry> allDomains = plugin.domainService().all();
        int start = page * DOMAINS_PER_PAGE;
        int index = start + slot;
        if (index < allDomains.size()) {
            DomainEntry entry = allDomains.get(index);
            if (rightClick) {
                if (plugin.domainService().isDefault(entry.domain())) {
                    player.sendMessage(plugin.messages().component("gui-domain-cannot-remove"));
                } else {
                    plugin.domainService().remove(entry.domain());
                    player.sendMessage(plugin.messages().component("gui-domain-removed", Map.of("domain", entry.domain())));
                }
            } else {
                plugin.domainService().toggle(entry.domain());
                player.sendMessage(plugin.messages().component("gui-domain-toggled", Map.of("domain", entry.domain())));
            }
            openDomains(player, page);
        }
    }

    private void handleBannedWordsClick(Player player, int slot, boolean rightClick) {
        plugin.soundService().play(player, "gui-click");
        if (slot == 45) {
            openSettings(player);
            return;
        }
        if (slot == 49) {
            awaitingInput.put(player.getUniqueId(), true);
            inputAction.put(player.getUniqueId(), "add-word:");
            player.closeInventory();
            player.sendMessage(plugin.messages().component("gui-word-input"));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> cancelInput(player.getUniqueId()), 600L); // 30 seconds
            return;
        }

        List<String> fragments = new ArrayList<>(plugin.filterConfig().blacklistFragments());
        if (slot < fragments.size()) {
            String word = fragments.get(slot);
            if (rightClick) {
                plugin.configService().removeBlacklistFragment(word);
                plugin.reloadHotMessages();
                player.sendMessage(plugin.messages().component("gui-word-removed", Map.of("word", word)));
                openBannedWords(player);
            }
        }
    }

    private ItemStack logItem(LogEntry log) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Время: &f" + TIME_FORMAT.format(log.time()));
        lore.add("&7Канал: &f" + log.channel());
        lore.add("&7Причина: &c" + log.reason());
        lore.add("&7Правило: &f" + trim(log.ruleId(), 36));
        lore.add("&7Сообщение:");
        lore.add("&f" + trim(log.message(), 70));
        return item(Material.PAPER, "&e" + log.playerName(), lore);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        return item(material, name, lore, null);
    }

    private ItemStack item(Material material, String name, List<String> lore, String nbtTag) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.messages().rawComponent(name));
            meta.lore(lore.stream().map(plugin.messages()::rawComponent).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String domainTag(String domain) {
        return "hm-domain:" + domain;
    }

    private String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }

    private String colorless(String value) {
        return value == null ? "" : value.replaceAll("(?i)&[0-9A-FK-ORX]", "");
    }
}
