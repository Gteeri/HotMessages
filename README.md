# HotMessages

Chat guard plugin for Paper/Folia 1.21+. Filters banned words (with leetspeak/spacing evasion detection), throttles chat spam and flooding, and escalates repeat offenders from warnings to temporary mutes — all while trying to stay out of the way of normal players.

## Features

- **Smart word filter** — normalizes messages (case, leetspeak like `4`→`a`, separators like `b.a.d`, repeated letters like `baaaad`) before matching against `banned-words.yml`, so simple evasion tricks don't bypass the filter. Matching runs on an Aho-Corasick automaton, so performance stays O(message length) regardless of list size.
- **Censor or block** — configurable: replace offending words with `*` in place, or cancel the whole message.
- **Warnings → mute escalation** — repeated violations automatically mute chat for a configurable, optionally-doubling duration. Mutes persist across restarts.
- **Anti-spam** — per-player message cooldown and duplicate-message detection.
- **Anti-flood, low-friction** — excessive CAPS and stretched-out letters (`hiiiiiii`) are automatically softened instead of blocking the message outright, so casual players aren't punished for typing enthusiastically.
- **Staff bypass** — `hotmessages.bypass` permission skips all checks.
- **Folia-friendly scheduling** and periodic in-memory cleanup so tracking state doesn't grow unbounded on busy servers.
- Branded messages using the HopNeo palette (`#1f2428` / `#7fffbd`).

## Commands

| Command | Description | Permission |
|---|---|---|
| `/hotmessages reload` | Reload config, messages and word list | `hotmessages.admin` |
| `/hotmessages toggle` | Enable/disable the whole plugin | `hotmessages.admin` |
| `/hotmessages addword <word>` | Add a banned word/phrase | `hotmessages.admin` |
| `/hotmessages removeword <word>` | Remove a banned word/phrase | `hotmessages.admin` |
| `/hotmessages warnings <player>` | Show a player's current warning count | `hotmessages.admin` |
| `/hotmessages clearwarnings <player>` | Reset a player's warnings | `hotmessages.admin` |
| `/hotmessages mute <player> [minutes]` | Manually mute a player's chat | `hotmessages.admin` |
| `/hotmessages unmute <player>` | Remove a chat mute | `hotmessages.admin` |

Alias: `/hm`

## Permissions

| Permission | Description | Default |
|---|---|---|
| `hotmessages.admin` | Access to all admin subcommands | op |
| `hotmessages.bypass` | Skip word filter and anti-spam checks | op |

## Configuration

See `config.yml` for anti-spam/anti-flood/mute tuning, `banned-words.yml` for the word list, and `messages.yml` for all player-facing text and colors.

`banned-words.yml` ships with a small starter list focused on anti-advertising (links, invite codes). Add your server's actual profanity list via `/hotmessages addword` or by editing the file directly — one entry per line under `words:`.

## Installation

1. Download the JAR (see [Releases](../../releases) or the `Build` GitHub Action artifact)
2. Place it in your server's `plugins/` folder
3. Restart the server
4. Edit `plugins/HotMessages/config.yml`, `messages.yml` and `banned-words.yml`
5. Run `/hotmessages reload` to apply changes without restarting

## Requirements

- Paper or Folia 1.21+
- Java 21

## Building from source

```bash
mvn -B package
```

The compiled plugin will be at `target/HotMessages-<version>.jar`. A GitHub Actions workflow (`.github/workflows/build.yml`) also builds and uploads the jar automatically on every push.
