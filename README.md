# LumaSG — Survival Games for Paper

LumaSG is a feature-rich Survival Games plugin for Paper servers, delivering a polished battle royale experience with custom items, tiered loot, team play, and extensive admin tooling.

**Kotlin rewrite** of the original Java codebase — same features, cleaner architecture, powered by the [Nexus](https://github.com/BadgersMC/nexus) DI framework.

[![Codacy Badge](https://app.codacy.com/project/badge/Grade/079f7794fb864d5b879febbed82a4ebe)](https://app.codacy.com/gh/BadgersMC/LumaSG/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

## Requirements

- **Paper 1.21.1+** (will not work on Spigot/CraftBukkit)
- **Java 21+**
- **MariaDB/MySQL** for player statistics

## Game Features

### Core Mechanics
- **Multiple modes** — Solo, Teams, and Duos
- **Arena system** — multiple arenas with configurable spawn points, world border, and deathmatch shrinking
- **Grace period** — initial safe period with PvP disabled
- **Spectator mode** — eliminated players watch the ongoing game
- **Team system** — invitations, auto-balancing, team glow effects

### Custom Items
- **Player Tracker** — compass that points to nearby enemies
- **Knockback Stick** — non-lethal crowd control
- **Fire Bomb** — creates temporary fire zones
- **Poison Bomb** — releases toxic clouds
- **Airdrop Flare** — calls in supply drops with rare loot
- **Bomb** — explosive throwable

### Chest & Loot System
- **Tiered loot** — Common, Uncommon, Rare (custom tiers supported)
- **Distance-based loot** — better items near center (toggleable)
- **Auto-refill** — configurable chest refill timers
- **Nexo integration** — custom Nexo items in loot tables with their native mechanics

### Visual Features
- **Live scoreboard** — real-time game state
- **Death messages** — customizable elimination announcements
- **Winner celebrations** — fireworks and pixel art displays
- **Team glow** — see teammates through walls

### Admin Tooling
- **Admin wand** — golden sword for arena setup
  - Left-click blocks to set spawn points
  - Right-click to set arena center
  - **Particle beams** — END_ROD vertical beams on spawn points so admins can see placement (visible when holding wand)
- **GUI-based management** — create/browse/join games from menus

### Statistics & Leaderboards
- **Player stats** — kills, wins, deaths, damage dealt/taken, games played
- **Leaderboard GUI** — sortable global rankings
- **PlaceholderAPI** — custom placeholders for scoreboards, chat, tab

### Integrations
- **[Nexo](https://nexomc.com/)** — custom items in loot tables via direct API
- **[PlaceholderAPI](https://placeholderapi.com/)** — stat placeholders
- **[LumaGuilds](https://github.com/BadgersMC/LumaGuilds)** — guild-aware team behavior

## Architecture

```
net.lumalyte.lumasg/
├── chest/          ChestManager, ChestItem, loot resolution
├── commands/       SGCommand (@Command + @Subcommand via Nexus)
├── config/         LumaSGConfig, MessagesConfig (@ConfigFile)
├── game/           Game, GameManager, Celebration, Scoreboard, WorldManager
├── gui/            MainMenu, GameBrowserMenu, LeaderboardMenu (InvUI)
├── hooks/          NexoHook, LumaGuildsHook, PlaceholderAPIHook, HookManager
├── items/          Custom items (Bomb, FireBomb, Tracker, etc.)
├── listeners/      AdminWandListener, PlayerListener, ChestListener, etc.
├── persistence/    DatabaseService, ArenaRepository, PlayerStatsRepository (Exposed)
└── util/           Caches, ItemUtils, MiniMessage helpers, serialization
```

**Key tech choices:**
- **Nexus DI** — `@Service`, `@Repository`, `@PostConstruct`/`@PreDestroy` lifecycle, constructor injection
- **Nexus Paper** — `@Command`/`@Subcommand` with Paper Brigadier, `BukkitDispatcher` for main-thread coroutines
- **Jetbrains Exposed** — type-safe SQL for stats and arena persistence
- **InvUI 1.49** — declarative inventory GUIs
- **Kotlin Coroutines** — async database ops, timed game phases
- **Paper runtime deps** — Kotlin stdlib, Exposed, InvUI, JDA downloaded at startup via `LumaSGLoader` (not shaded)

## Commands

| Command | Description | Permission |
|---|---|---|
| `/sg` | Opens the main menu GUI | `lumasg.use` |
| `/sg create <name> <radius>` | Create a new arena | `lumasg.admin` |
| `/sg join [arena]` | Join a game (or via GUI) | `lumasg.join` |
| `/sg leave` | Leave current game | `lumasg.join` |
| `/sg list` | List available arenas | `lumasg.use` |
| `/sg stats [player]` | View player statistics | `lumasg.stats` |
| `/sg setup <arena>` | Enter arena setup mode | `lumasg.admin` |
| `/sg start <arena>` | Force-start a game | `lumasg.admin` |
| `/sg stop <arena>` | Force-stop a game | `lumasg.admin` |
| `/sg delete <arena>` | Delete an arena | `lumasg.admin` |
| `/sg reload` | Reload configuration | `lumasg.admin` |
| `/sg info <arena>` | View arena details | `lumasg.admin` |
| `/sg myinfo` | View your own game state | `lumasg.use` |

## Configuration

| File | Contents |
|---|---|
| `config.yaml` | Game settings, timers, border, scoreboard, performance tuning |
| `messages.yaml` | All player-facing messages (MiniMessage format) |
| `chest.yaml` | Loot tables, tiers, Nexo item IDs, refill settings |
| `custom-items.yaml` | Custom item definitions and behavior settings |

## Installation

1. Download the latest release JAR
2. Place in your server's `plugins/` folder
3. Start the server — config files are generated automatically
4. Configure arenas with `/sg create` and the admin wand

## Building

```bash
./gradlew shadowJar
# Output: build/libs/LumaSG-2.0.0-SNAPSHOT.jar
```

Requires `nexus-core` and `nexus-paper` in local Maven (`mavenLocal()`).

## Performance

- Caffeine caches for GUI components, loot tables, player data, scoreboards
- Concurrent chest filling with `ConcurrentChestFiller`
- Async database operations via coroutines + HikariCP connection pooling
- Virtual thread dispatchers via Nexus

## License

**Lumalyte Source Available License (LSAL)** — see LICENSE file.

- Source code freely available for learning and contributions
- Personal and educational use permitted
- Commercial distribution requires permission from Lumalyte
- Networks must disclose source and contribute critical bug fixes back within 30 days

## Credits

Developed by **LumaLyte** and **BadgersMC**

**Libraries:**
- [Nexus](https://github.com/BadgersMC/nexus) — DI framework
- [InvUI](https://github.com/NichtStudioCode/InvUI) — inventory GUI framework
- [Jetbrains Exposed](https://github.com/JetBrains/Exposed) — Kotlin SQL framework
- [HikariCP](https://github.com/brettwooldridge/HikariCP) — connection pooling
- [Caffeine](https://github.com/ben-manes/caffeine) — caching
- [JDA](https://github.com/discord-jda/JDA) — Discord integration

This plugin is a WIP — report issues or requests to **angrybadger#1** on Discord.
