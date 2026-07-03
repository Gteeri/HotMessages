# Changelog

## [1.0.0]

### Added
- Chat filter: blacklist/whitelist fragments, compact fragment matching, regex rules (IPv4, IP:port, domains, obfuscated Discord/Telegram invites, spaced-out domains)
- Dynamic, in-game-editable domain list
- In-game GUI for filter toggle, reload, logs, domain and banned-word management
- Anti-spam (burst limit + duplicate detection)
- Violation escalation with automatic mutes
- Manual mute/unmute with SQLite/MySQL persistence and legacy mutes.yml migration
- Filtered private messages with staff ghost mode
- Optional local/global chat system
- Sound feedback for GUI and moderation actions
- Persistent logs (console, file, database) with retention purging
