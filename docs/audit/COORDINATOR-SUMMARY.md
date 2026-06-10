# LumaSG Mass Audit — Coordinator Summary

Generated: 2026-06-10
Coordinator model: deepseek/deepseek-v4-flash
Subagent model: minimax/minimax-m3
Branch: `fix/audit-lumasg`

## Per-batch finding counts by severity

| Batch | crit | high | med | low | Total |
|-------|------|------|-----|-----|-------|
| audit-game | 4 | 4 | 7 | 11 | 26 |
| audit-items | 0 | 5 | 7 | 3 | 15 |
| audit-gui-commands | 2 | 4 | 8 | 7 | 21 |
| audit-persistence | 0 | 5 | 11 | 9 | 25 |
| audit-domain-util | 1 | 2 | 9 | 9 | 21 |
| audit-hooks-misc | 0 | 3 | 6 | 10 | 19 |
| **Total** | **7** | **23** | **48** | **49** | **127** |

## Top 10 findings overall

1. **[CRIT] audit-game — `PlayerStateManager.kt:61`** — `saveLocation=false` on world-change corrupts saved player location, causing incorrect respawn on death (server restart may strand players outside the arena).

2. **[CRIT] audit-game — `Game.kt:164`** — `reconnectPlayer` overwrites the player's saved pre-game inventory with mid-game loot, enabling a rejoin dupe: die with inventory → disconnect → reconnect → pre-game inventory is now the death inventory, giving a second copy.

3. **[CRIT] audit-game — `GameManager.kt`** — `killCommand`/`winCommand` template substitution uses raw string replacement with no escaping. An admin-customised command containing `{player}` in a player name or argument would inject arbitrary console commands.

4. **[CRIT] audit-game — `TeamManager.kt:46`** + `TeamQueueManager.kt:82`** — Accepting a team invite does not remove the player from their previous team's `members` list, leaving the player simultaneously in two teams and enabling team-count exploits (e.g. 4+ players in a team-2 arena).

5. **[CRIT] audit-domain-util — `Arena.kt` `scanForChests()`** — Cubic scan with default radius 500 iterates ~1.4 billion positions, blocking the Bukkit main thread for many minutes. Any player with setup permission can trigger this via `wand right-click`.

6. **[HIGH] audit-gui-commands — `SGCommand.kt`** — `@Async` annotation on `SGCommand.create()` means Bukkit API calls (world loading, player teleportation, game state checks) run off the main thread, causing `IllegalStateException` under timing-sensitive conditions.

7. **[HIGH] audit-gui-commands — `SGCommand.kt`** — `/sg forcestart` is a no-op: the `forceStart` path calls `arenaService.startGame()` but that method requires a `GamePlayer` list that is never built, so the game never actually begins.

8. **[HIGH] audit-items — `ChestManager.kt`** — Double-chest refill only fills the first half (the block at the double-chest location), leaving the second chest half empty. Players learn which chests are "safe" and exploit the empty half.

9. **[HIGH] audit-persistence — `PlayerStatsRepository.kt`** — K/D and Win-Rate leaderboards order by raw `kills`/`wins` columns instead of computing `kills / deaths` or `wins / games`. Players pad kills/stats to top the board without actually improving their ratio.

10. **[HIGH] audit-hooks-misc — `PlayerDataCache.kt:74`** — On DB failure, `getCachedStats` returns and caches a zeroed `PlayerStats` with a 30-minute TTL. All downstream consumers (scoreboard, Discord embeds, leaderboards) show "Unknown Player" for half an hour. The DB outage is silently hidden.

## detekt

No detekt configuration file was found anywhere in the repository (`detekt*` / `.detekt*`). No static-analysis results to fold.

## Cross-cutting items flagged by multiple batches

- **Main-thread violations** — Appears across audit-game (`Game.kt`, `NameplateManager.kt`), audit-gui-commands (`SGCommand.kt`), audit-hooks-misc (`PlayerDataCache.kt`), and audit-domain-util (`InvitationManager.kt`). The project uses coroutines but several paths invoke Bukkit API off the main thread without `Bukkit.getScheduler().runTask()` or `ensureActiveOnMainThread()`.

- **Game-phase / player-state desync** — audit-game (`eliminate()` for unknown UUIDs, `Team.isAlive` false on disconnect), audit-gui-commands (`CustomItemListener` damage bypasses phase rules), audit-items (`ChestManager` fills during active game). Multiple batches identify the same pattern: game-phase checks are missing or inconsistent across event listeners.

- **Spectator inventory isolation** — audit-game (`reconnectPlayer` overwrites pre-game inventory) and audit-gui-commands (respawn spectator setup one tick late). The spectator inventory boundary is porous across reconnect and death paths.

- **Silent error swallowing** — audit-persistence (`ArenaRepository` swallows JSON parse errors, returning zero locations), audit-domain-util (`InventorySerializer` fallback returns empty valid bytes), audit-hooks-misc (`PlayerDataCache` caches zeroed stats on DB failure). The codebase consistently hides failures from admins with no logging.

- **Race conditions in caches** — audit-persistence (`CacheManager.getOrPut`, `StatisticsService.getOrCreate` have check-then-act races), audit-hooks-misc (`PlayerDataCache` Caffeine listener thread accesses Bukkit API). Demonstrated pattern of unsynchronised cache access across async boundaries.

- **Test-coverage crisis** — With 29 test files for 85 main sources (~34% ratio), every batch reported severe coverage gaps. Names the most critical untested behaviors: game lifecycle (phase transitions, reconnect), chest refill concurrency, SQL migration correctness, player permission caching, and arena scan-for-chests performance.