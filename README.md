# HotMessages

Advanced Folia-ready chat filter/guard for the HopNeo server: domain & IP blocking, anti-spam, escalating mutes, an in-game GUI, filtered private messages, and an optional local/global chat system.

## Features

- **Chat filter** — blacklist/whitelist fragments, "compact" (de-obfuscated) fragment matching, and regex rules for IPv4 addresses, IP:port, domains, obfuscated Discord/Telegram invites and spaced-out domains (`d i s c o r d . g g`).
- **Dynamic domain list** — built-in TLDs (`.ru`, `.com`, `.gg`, `.net`, ...) plus admin-added domains, all toggleable without restarting.
- **In-game GUI** (`/hm gui`) — toggle the filter, reload config, browse recent blocked-message logs, and manage domains/banned words directly from an inventory menu.
- **Anti-spam** — per-player burst limit and duplicate-message detection.
- **Escalation** — repeated violations within a time window trigger an automatic mute (duration configurable).
- **Mutes** — manual `/mute` and `/unmute`, persisted in SQLite or MySQL via HikariCP, with automatic migration from a legacy `mutes.yml`.
- **Private messages** (`/msg`, `/tell`, `/w`, `/pm`, `/m`, `/r`, `/reply`) — filtered the same way as public chat, plus a staff "ghost" mode (`/hm ghost`) to monitor DMs.
- **Optional local/global chat** — radius-based local chat with a `!`-prefixed global channel.
- **Sound feedback** for GUI actions, blocks, mutes and unmutes.
- **Persistent logs** — console, rotating file (`logs/blocked-messages.log`), and database, with configurable retention.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/hm` or `/hm gui` | Open the main GUI | `hotmessages.admin` |
| `/hm reload` | Reload config.yml without restarting | `hotmessages.admin` |
| `/hm toggle` | Enable/disable the filter | `hotmessages.admin` |
| `/hm logs` | Show recent blocked messages | `hotmessages.admin` |
| `/hm test <text>` | Check whether a message would be blocked | `hotmessages.admin` |
| `/hm ghost` | Toggle monitoring of all private messages | `hotmessages.ghost` (via admin check) |
| `/mute <player> [10m\|1h\|1d\|perm] [reason]` | Mute a player's chat | `hotmessages.admin` |
| `/unmute <player>` | Remove a mute | `hotmessages.admin` |
| `/msg`, `/tell`, `/w`, `/pm`, `/m <player> <message>` | Send a private message | — |
| `/r`, `/reply <message>` | Reply to the last private message | — |

Aliases for the admin command: `/hotmessages`, `/hmchat`, `/chatfilter`.

## Permissions

| Permission | Description | Default |
|---|---|---|
| `hotmessages.admin` | Full access to commands and GUI | op |
| `hotmessages.notify` | Receive alerts when a message is blocked | op |
| `hotmessages.bypass` | Bypass all filters | false |
| `hotmessages.ghost` | Use `/hm ghost` to see private messages | op |

## Configuration

Everything lives in `config.yml`: `settings`, `chat` (local/global), `database`, `spam`, `escalation`, `normalization` (leetspeak/Cyrillic-lookalike replacements, repeated-character collapsing), `private-message-commands`, `domains`, `whitelist`, `blacklist` (+ `compact-fragments`), `regex-rules`, `messages`, `gui` (item materials), and `sounds`.

Domains added via the in-game GUI are stored separately in `domains.yml` so they survive `config.yml` edits/reloads.

## Requirements

- Paper or Folia 1.21.11+
- Java 21

## Building from source

```bash
mvn package
```

Produces a shaded `target/HotMessages-1.0.0.jar` with HikariCP and the SQLite driver relocated to avoid classpath conflicts with other plugins.
