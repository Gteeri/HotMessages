package dev.gteeri.hotmessages.listener;

import dev.gteeri.hotmessages.HotMessages;
import dev.gteeri.hotmessages.antispam.SpamGuard;
import dev.gteeri.hotmessages.config.PluginConfig;
import dev.gteeri.hotmessages.filter.WordFilter;
import dev.gteeri.hotmessages.punishment.MuteManager;
import dev.gteeri.hotmessages.punishment.WarnManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Проверяет сообщения чата по цепочке: мут -> антиспам -> смягчение
 * (капс/растянутые буквы) -> фильтр запрещённых слов. Работает поверх
 * компонентного чат-события Paper.
 */
public final class ChatListener implements Listener {

    private final PluginConfig config;
    private final WordFilter wordFilter;
    private final SpamGuard spamGuard;
    private final WarnManager warnManager;
    private final MuteManager muteManager;

    public ChatListener(HotMessages plugin) {
        this.config = plugin.pluginConfig();
        this.wordFilter = plugin.wordFilter();
        this.spamGuard = plugin.spamGuard();
        this.warnManager = plugin.warnManager();
        this.muteManager = plugin.muteManager();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        if (!config.enabled()) return;

        Player player = event.getPlayer();
        if (player.hasPermission(config.bypassPermission())) return;

        if (muteManager.isMuted(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(config.msg("muted-chat",
                    "%time%", String.valueOf(muteManager.remainingSeconds(player.getUniqueId()))));
            return;
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (config.antiSpamEnabled()) {
            SpamGuard.Verdict verdict = spamGuard.check(player.getUniqueId(), plain);
            if (verdict == SpamGuard.Verdict.BLOCK_COOLDOWN) {
                event.setCancelled(true);
                player.sendMessage(config.msg("spam-cooldown"));
                return;
            }
            if (verdict == SpamGuard.Verdict.BLOCK_DUPLICATE) {
                event.setCancelled(true);
                player.sendMessage(config.msg("spam-duplicate"));
                return;
            }
            plain = spamGuard.soften(plain);
        }

        if (config.filterEnabled() && wordFilter.containsBannedWord(plain)) {
            if ("BLOCK".equals(config.filterMode())) {
                event.setCancelled(true);
                player.sendMessage(config.msg("word-blocked"));
            } else {
                plain = wordFilter.censor(plain, config.censorSymbol());
                player.sendMessage(config.msg("word-censored"));
            }

            boolean muted = warnManager.warn(player.getUniqueId());
            if (muted) {
                player.sendMessage(config.msg("auto-muted",
                        "%time%", String.valueOf(muteManager.remainingSeconds(player.getUniqueId()))));
            }

            if (event.isCancelled()) return;
        }

        event.message(Component.text(plain));
    }
}
