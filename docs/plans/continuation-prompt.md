# LumaSG Kotlin Rewrite — Continuation Prompt

> **Paste this into a new Claude Code session to continue the work.**

---

## Context

You are continuing a Kotlin rewrite of the LumaSG Minecraft Survival Games plugin. The rewrite is on branch `feature/kotlin-rewrite` in a git worktree at `D:/BadgersMC-Dev/LumaSG/.worktrees/kotlin-rewrite/`.

The original Java source is on `remotes/origin/main`. You can read any Java file with:
```bash
git -C "D:/BadgersMC-Dev/LumaSG" show remotes/origin/main:src/main/java/net/lumalyte/PATH
```

## What Has Been Done

All 40 tasks from the Phase 2 plan (`docs/plans/2026-03-14-lumasg-kotlin-rewrite-phase2.md`) have been implemented. The project builds successfully. The branch is ~25 commits ahead of origin.

**However**, an exhaustive method-by-method audit revealed **26 must-have gaps** where Java functionality was not ported to Kotlin. The full gap analysis is at:

**`docs/plans/java-kotlin-gap-analysis.md`**

This document lists EVERY public method from EVERY Java class and marks each as ✅ (present), ❌ (missing), or 🔄 (different API). It is ~700 lines and covers all 11 packages.

## The Prime Directive

**"I want the only thing changed to be the underlying architecture and framework, and the language. The front end experience should be identical."**

This means:
- Every Java method must have a Kotlin equivalent
- The player-facing behavior must be identical
- The Kotlin architecture (coroutines, sealed classes, ports-adapters) stays — that IS the engine swap
- But nothing can be lost from the Java version

## Explicitly Approved Changes (the ONLY allowed deviations from Java)

1. **PoisonBomb** — Uses wind charge projectile (not splash potion)
2. **FireBomb** — Pure molotov (fire only, no knockback, no debris)
3. **BombItem** — NEW: TNT with knockback + visual FallingBlock debris
4. **Items package** — `net.lumalyte.lumasg.items` (NOT nested under `chest`)
5. **LumaGuilds** — Replaces KingdomsX. PvP override respects SG team membership
6. **Coroutine lifecycle** — Game uses CoroutineScope instead of BukkitScheduler
7. **Sealed classes** — GamePhase replaces GameState enum, GameMode sealed class
8. **Kotlin Exposed** — Replaces raw JDBC StatisticsDatabase
9. **Nexus DI** — Replaces manual singletons

**Everything else must match Java behavior exactly.**

## Your Task

### Phase 1: Verify the Gap Analysis (DO THIS FIRST)

Before writing any code, independently verify the gap analysis by:

1. Read `docs/plans/java-kotlin-gap-analysis.md`
2. For each "Must-Have" gap listed in the summary section, read the Java source file to confirm the method exists
3. Read the corresponding Kotlin file to confirm it's missing
4. If you find any gaps that were incorrectly identified (method actually exists in Kotlin), note them
5. If you find ADDITIONAL gaps that were missed, add them to your list
6. Present your verified findings before proceeding

### Phase 2: Prioritize and Implement

After verification, work through the gaps in this priority order:

**P0 — Core gameplay (breaks the game if missing):**
- Arena: `chestLocations`, `lobbySpawn`, `spectatorSpawn`, `allowedBlocks` properties
- PlayerStats: missing 9 fields (`losses`, `totalTimePlayed`, `bestPlacement`, `currentWinStreak`, `bestWinStreak`, `top3Finishes`, `chestsOpened`, `lastPlayed`, `lastUpdated`)
- StatType enum (needed for leaderboard filtering)
- Game: `getTimeRemaining()`, `broadcastMessage()`, `getSpectators()`, `isPvpEnabled()`
- GameManager: `findAvailableGame()`, `isPlayerInGame()`, `shutdown()`, `cleanupOrphanedGames()`
- TeamManager: `areTeammates()`, `disbandAllTeams()`, `eliminateTeam()`
- Team: `isEliminated`, `eliminate()`, `getOnlineMembers()`, `displayName`, `createdAt`
- TeamInvitation class (proper invitation data class)
- WorldManager: `clearAllDrops()`, `isBlockAllowed()`

**P1 — Important features (noticeable if missing):**
- RankPermissions — entire permission system
- HookManager — hook registry + convenience methods
- CustomItemsManager — item registry
- NameplateManager: `disableNameplateHiding()`/`enableNameplateHiding()`
- AdminWandListener: missing event handlers (item held, drop, inventory click, swap hands, quit)
- ChestListener: `onInventoryClick()` (prevent item theft)
- GameMode: `isTeamMode()`, `fromDisplayName()`, `getDescription()`, `getMaxTeams()`, `getIdealPlayerCount()`
- MenuUtils utility class
- MainMenu entry point
- LeaderboardMenu: `openLeaderboardTab(StatType)`
- NexoHook: `getNexoItem()` method

**P2 — Nice-to-have (can omit with user approval):**
- ArenaTemplate system
- Game setup flow (isSetupComplete, markSetupComplete, clearSetupComplete)
- GameBarrierManager as separate class
- Performance profiler, cache managers
- Exception hierarchy (LumaSGException + subclasses)

For P2 items, ask the user before implementing.

### Phase 3: Build Verification

After all P0 and P1 gaps are filled:
```bash
cd D:/BadgersMC-Dev/LumaSG/.worktrees/kotlin-rewrite
./gradlew build
```
Must be BUILD SUCCESSFUL.

## Tech Stack & API Notes

- **Paper 1.21.11** (NOT 1.21.1)
- `PlayerDeathEvent.killer` removed — use `event.damageSource.causingEntity as? Player`
- `WorldBorder.setSize(double, long)` deprecated — use `changeSize(double, ticks)` (ticks = seconds × 20)
- `WorldBorder.setWarningTime(int)` deprecated — use `setWarningTimeTicks(int)`
- Discord: `discordService?.announce(GameEmbed.gameStarted(...))` and `GameEmbed.gameEnded(...)`
- Nexus DI: `@Service`, `@PostConstruct`, `@PreDestroy`, constructor injection
- Adventure MiniMessage for all text formatting

## Tools & Workflow

### MANDATORY: Use mgrep for all searches
```bash
mgrep "natural language query"              # local semantic search
mgrep "query" src/main/kotlin              # scoped to directory
mgrep --web --answer "Paper 1.21 API question"  # web search with summary
```
Do NOT use built-in Grep or Glob for content searches.

### MANDATORY: Use Context7 for API verification
Before using any Paper, Adventure, Exposed, InvUI, or Nexus API, verify with Context7:
1. `mcp__Context7__resolve-library-id` with the library name
2. `mcp__Context7__get-library-docs` with the resolved ID and topic
If Context7 doesn't have docs for a library, ask the user to add them.

### Reading Java source
```bash
git -C "D:/BadgersMC-Dev/LumaSG" show remotes/origin/main:src/main/java/net/lumalyte/PACKAGE/FILE.java
```

### Current Kotlin file listing (57 files, ~4,800 total lines)
```
src/main/kotlin/net/lumalyte/lumasg/
├── LumaSGPlugin.kt (44 lines)
├── chest/ — ChestManager.kt (59), ChestTier.kt (3), LootEntry.kt (12), LootTable.kt (18)
├── commands/ — SGCommand.kt (179)
├── config/ — LumaSGConfig.kt (132), MessagesConfig.kt (15)
├── discord/ — DiscordService.kt (71), GameEmbed.kt (24)
├── domain/ — Arena.kt (31), GameMode.kt (7), GamePhase.kt (13), PlayerStats.kt (19)
├── game/ — Celebration.kt (258), DeathMessages.kt (106), Game.kt (471), GameManager.kt (79),
│          GamePlayer.kt (20), GameScoreboard.kt (145), NameplateManager.kt (88),
│          PlayerStateManager.kt (101), Team.kt (29), TeamManager.kt (74),
│          TeamQueueManager.kt (132), WorldManager.kt (182)
├── gui/ — ArenaSelectionMenu.kt (68), GameBrowserMenu.kt (64), LeaderboardMenu.kt (62),
│         SetupMenu.kt (61), SpectatorMenu.kt (52), TeamSelectionMenu.kt (47)
├── hooks/ — KingdomsXHook.kt (20), LumaGuildsHook.kt (64), NexoHook.kt (20),
│           PlaceholderAPIHook.kt (40), PluginHook.kt (6)
├── items/ — AirdropFlareItem.kt (391), BombItem.kt (35), CustomItem.kt (36),
│           FireBombItem.kt (35), KnockbackStickItem.kt (31), PlayerTrackerItem.kt (270),
│           PoisonBombItem.kt (35)
├── listeners/ — AdminWandListener.kt (45), ChestListener.kt (112), CustomItemListener.kt (307),
│               FishingListener.kt (160), PlayerListener.kt (238)
├── persistence/ — DatabaseExtensions.kt (9), DatabaseService.kt (54),
│                 repositories/ArenaRepository.kt (69), repositories/PlayerStatsRepository.kt (57),
│                 tables/ArenaTable.kt (18), tables/GameHistoryTable.kt (17), tables/PlayerStatsTable.kt (19)
├── service/ — ArenaService.kt (29)
└── statistics/ — StatisticsService.kt (44)
```

## Commit Strategy

- Batch related changes into logical commits
- Use conventional commit messages: `feat:`, `fix:`, `refactor:`
- Build-verify after each batch
- Do NOT push unless asked
