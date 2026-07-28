# LumaSG Mass Audit — Consolidated Report (2026-06-10)

Findings-only audit of all 86 main sources at commit `5baa40f`, run as one Hermes
coordinator (DeepSeek v4 Flash) with 6 subagents (MiniMax M3), one per disjoint batch.
Raw batch reports live alongside this file (`audit-*.md`, 127 raw findings). This
document is the **operator-triaged** consolidation: every critical/high finding was
manually verified against the source at `5baa40f`; severities were corrected and false
positives removed. Medium/low findings are summarized by theme — see the batch reports
for full detail.

Triage stats: 30 crit/high raw findings verified line-by-line → 3 false positives,
4 severity corrections. Verified false-positive rate ~15–20% (better than the 30–50%
typical of earlier DeepSeek audits).

---

## Critical (verified)

### C1. `game/Game.kt:164` — `reconnectPlayer` destroys the pre-game inventory snapshot
`reconnectPlayer` calls `playerStateManager.saveAndPrepare(...)`, which **re-snapshots
the player's current (mid-game) inventory** over the original pre-game snapshot taken on
first join. When the game ends, `restore` returns the mid-game loot instead of the
player's real items: the pre-game inventory is silently lost, and the player keeps a
copy of game loot — a disconnect/reconnect dupe.
**Fix direction:** on reconnect, skip the snapshot — teleport + re-prepare without
overwriting `saved[uuid]`.

### C2. `domain/Arena.kt:35-54` — `scanForChests()` freezes the main thread for minutes
Triple-nested loop over `(2r+1)² × world height` block lookups, documented main-thread.
With the default `radius = 500.0` that is ~385 million `getBlockAt` calls (Y is coerced
to world bounds, so less than the raw cube, but still minutes of full server freeze).
Reachable by anyone with setup permission via the wand/scan flow.
**Fix direction:** chunk-bounded async scan with a radius sanity cap.

### C3. `gui/GameBrowserMenu.kt:65-77` — players can join games in Active/Deathmatch
The click handler checks only "already in game" and the size cap — never `game.phase` —
and `Game.addPlayer` (Game.kt:105) has no phase guard either. A player can join a live
match at a fresh spawn with full health, including re-entry by eliminated spectators.
**Fix direction:** phase gate in `Game.addPlayer` itself (defence in depth) plus
greyed-out browser buttons. Same handler also has a TOCTOU on `maxPlayers` (size check
then `addPlayer`, no capacity check inside `addPlayer`).

---

## High (verified)

### H1. `game/PlayerStateManager.kt:61` — `saveLocation=false` saves the arena spawn as "restore location"
The saved location becomes the arena spawn the player was just teleported to, so restore
returns players to the arena instead of where they came from (or strands them if the
world unloads). Make the saved location nullable and treat null as "use lobby".

### H2. `game/Team.kt:19-21` — `Team.isAlive` is online-status, not elimination-status
A team whose members all briefly disconnect is instantly "dead" to
`checkWinCondition` — a lag spike or deliberate relog can hand the win to the
opposing team. `isAlive` should derive from `isEliminated`/alive-player tracking, not
`Bukkit.getPlayer(...)?.isOnline`.

### H3. `commands/SGCommand.kt:296-319` — `/sg forcestart` is a no-op
Validates arena/game/phase/players, then only sends "Force-started" — no code path
advances the phase or skips the countdown. (Related: `Game.skipGracePeriod()` at
Game.kt:445 is an empty stub.)

### H4. `commands/SGCommand.kt:437-464` — `@Async create` touches Bukkit API off the main thread
Reads `player.location` and calls `player.sendMessage` from the async dispatcher. Drop
`@Async` or wrap Bukkit access in `withContext(bukkitDispatcher)`.

### H5. `game/TeamQueueManager.kt:76-90` — accepting an invite doesn't leave the previous team
`accept` adds the player to the new pre-game team and overwrites the pointer while the
old team's `members` list still contains them — one player in two teams, breaking team
size and win-condition counts. (Note: the in-game `TeamManager.acceptInvite` is
**correct** — it calls `removeFromTeam` first. Only the queue path is broken.)

### H6. `game/Game.kt:138-151` — `eliminate(uuid)` mutates state for unknown UUIDs
No membership precondition: a stale/double call pushes ghost entries into
`eliminationOrder` and `spectators` and can remove a real player from a team. Add an
early return when `_players[uuid] == null` and make eliminate idempotent.

### H7. `persistence/repositories/PlayerStatsRepository.kt:64-65` — K/D and Win-Rate leaderboards are fake
`KILL_DEATH_RATIO` orders by raw `kills`, `WIN_RATE` by raw `wins`. 100k/100d ranks
above 50k/1d. Compute the ratio in SQL (`CAST(kills AS DOUBLE)/NULLIF(deaths,0)`).

### H8. `util/cache/PlayerDataCache.kt` — three related cache bugs
- `:192-197` — DB failure returns and caches a **zeroed `PlayerStats`** ("Unknown
  Player") for up to 30 min; scoreboards/placeholders/Discord show a stats reset while
  the outage is hidden.
- `:199-208` — `loadPermissionFromBukkit` runs `getPlayer`/`hasPermission` on a Caffeine
  worker thread; on Paper the exception is swallowed and **`false` is cached for 10
  min** — an admin silently loses permissions.
- `:101-115` — `getCachedDisplayName` calls `Bukkit.getPlayer` on `supplyAsync`'s
  executor, same main-thread violation.

### H9. `items/AirstrikeItem.kt:293-298` — friendly-fire immunity is inverted
The caller's team is added to the immune set, then the **caller is removed** — teammates
are immune, the caller takes their own meteor damage. Delete the `remove(callerUuid)`
line (or document the intended policy).

### H10. `items/AirstrikeItem.kt:183-189` — item consumed before the game lookup
`held.amount--` runs before `getGameForPlayer(uuid) ?: return` — if the game resolution
fails (player left/game ended in the same tick), the airstrike is consumed with no
strike. Resolve the game first, then consume.

### H11. `items/CustomItemsManager.kt:99-104` — `reload()` double-registers listeners
`shutdownItems()` doesn't `HandlerList.unregisterAll`; `registerDefaults()` re-calls
`item.register()` → duplicate event handling and doubled periodic tasks after every
`/sg reload`.

### H12. `listeners/AdminWandListener.kt:88-99` — wand edits persist only via the shutdown flush
Spawn/center edits go through `arenaService.addToCache` (memory only). They ARE flushed
by `onDisable → saveAll()` on clean shutdown, but a crash, kill, or `/sg admin reload`
before shutdown loses all wand work. (Downgraded from the batch report's critical; the
shutdown flush exists.) The new sneak-right-click removal path has the same gap.
**Fix direction:** persist on each edit (suspend `saveArena`) or a periodic flush.

### H13. `listeners/ChestListener.kt:57-115` — cross-arena chest fill when arenas share a world
Chunk-load fill resolves "the game in this world" as the *first* match — with two active
arenas in one world, arena B's chests fill with arena A's loot tier, and `filledChests`
tracking is shared. Scope fills to arena bounds.

### H14. `listeners/CustomItemListener.kt:75-217` — bomb/poison/fire damage bypasses phase & friendly-fire rules
Direct `target.damage(...)` + velocity, filtered only by thrower — works on teammates
with `friendlyFire=false` and during Waiting/Countdown/Grace. Enforce the same gates
`PlayerListener.onEntityDamage` applies.

### H15. `items/AirdropFlareItem.kt:99-108` — airdrop chest placed with no safety checks
Chest placed at the player's *current* (post-explosion) position, silently overwriting
any block (signs, chests with contents), possibly inside the player or floating at y=1.
Snapshot the target at use time; verify air + arena bounds before placing.

### H16. `listeners/PlayerListener.kt:121-125` (+ win/celebration paths) — console command injection via name substitution
`killCommand`/`winCommand` templates substitute `<player>` raw into a console dispatch.
Vanilla names are charset-limited, but Floodgate/Geyser (and display-name refactors)
allow spaces — argument injection into a console-privileged command. (Downgraded from
critical: separator injection (`;`) doesn't apply to Bukkit dispatch; argument injection
does.) Validate the substituted name or restrict substitution to a single token.

---

## Medium — dominant themes (see batch reports for the full list of 48)

- **Check-then-act races in caches** — `CacheManager.getOrPut`,
  `StatisticsService.getOrCreate` (duplicate DB work under burst; the batch report's
  "duplicate rows" claim is wrong — the `(uuid, lootMode)` PK upsert can't duplicate —
  but redundant queries and a lost `playerName` on race are real),
  `LootTableCache.getPreGeneratedChest` double-generation,
  `InvitationManager.createInvitation`.
- **Silent error swallowing** — `ArenaRepository` coerces bad JSON to empty
  spawn/chest lists with no log (arena silently breaks);
  `InventorySerializer.serializeInventory` falls back to a *valid empty payload* on
  failure (player inventories silently wiped on restore); `UUID.fromString` on stored
  columns can kill whole queries; `ArenaRepository` rotates a corrupt arena `id` to a
  random UUID on every load.
- **Main-thread violations off the happy path** — fire-and-forget
  `NameplateManager.stop()` racing scope cancellation; `MeteorUtils` helpers with no
  thread contract; `ConcurrentChestFiller` error-path `chest.location` access;
  `DiscordService.awaitReady()` blocking `onEnable`.
- **Shutdown/migration robustness** — `DatabaseService` legacy migration has no
  `try/finally` around `PRAGMA foreign_keys = OFF` (a failed rebuild leaves FK
  enforcement off on a pooled connection); `WorldManager.cleanup()` restores a 60M
  default border if `setup()` early-returned; `ArenaService.removeArena` is cache-first
  (DB failure resurrects the arena on restart).
- **Stats integrity** — ragequit overwrites `bestPlacement` with `totalPlayers`;
  `getTotalPlayerCount` counts (uuid, mode) rows (3× actual);
  `getAggregatedStats` returns a fake `lootMode=MODERN` record that would corrupt the
  MODERN row if ever passed back to `upsert`; per-kill stats are 2 DB round-trips each
  on a 1-connection SQLite pool.

## Low

49 findings — style, dead code (`removePlayer(teleportToLobby,…)` partially dead,
`gameTime` unused, `skipGracePeriod` stub), unclosed `URLConnection` stream in
`Celebration`, plaintext Discord token in config, unregistered hooks on reload, cooldown
granularity, hardcoded radii. See the batch reports.

---

## False positives removed during triage

| Batch claim | Why it's wrong |
|---|---|
| `TeamManager.kt:46` — acceptInvite leaves player in two teams (crit) | `acceptInvite` calls `removeFromTeam(invitee)` at line 50 **before** `team.add`. Correct as written. (The `TeamQueueManager` half of the finding is real → H5.) |
| `ChestManager.kt:154` — double chests only half-filled (high) | `org.bukkit.block.Chest.getInventory()` returns the merged `DoubleChestInventory` for double chests — clear + fill operate on all 54 slots. (Residual nit: `ChestListener` keys `filledChests` per half, so a double chest can be cleared/refilled twice on chunk load.) |
| `StatisticsService.kt:23` — concurrent `getOrCreate` creates duplicate PK rows (high) | `upsert` on PK `(uuid, lootMode)` updates on conflict; duplication is impossible. Race wastes queries and can clobber `playerName` → downgraded to medium. |
| `Game.kt:181` — `removePlayer(Player,…)` "uses neither parameter" (low) | `restoreState` **is** used (line 185); only `teleportToLobby` is dead. |

## Test coverage

29 test files vs 86 main sources, concentrated in persistence/domain. Zero coverage of:
game lifecycle (reconnect, eliminate, win condition), all listeners, all GUIs, all 11
custom items, chest filling, and every cache. Each batch report ends with a prioritized
test list; the highest-value five:

1. `reconnectPlayer` preserves the pre-game snapshot (catches C1).
2. `GameBrowserMenu`/`Game.addPlayer` rejects joins outside Waiting/Countdown (C3).
3. `Team.isAlive` with all members offline but not eliminated (H2).
4. KDR/Win-Rate leaderboard ordering with adversarial stat rows (H7).
5. `CustomItemsManager.reload()` does not duplicate listener registration (H11).

## Run metadata

- Audited revision: `5baa40f` (origin/main, 2026-06-10)
- Coordinator: `deepseek/deepseek-v4-flash` · Subagents: `minimax/minimax-m3`
- Branch: `fix/audit-lumasg` on the Hermes fork; no source files modified
- Operator triage: Claude (Fable 5), all crit/high verified against source
