Audit Report: audit-gui-commands
================================
Scope: src/main/kotlin/net/lumalyte/lumasg/commands/SGCommand.kt
       src/main/kotlin/net/lumalyte/lumasg/gui/{ArenaSelectionMenu,GameBrowserMenu,LeaderboardMenu,MainMenu,MenuUtils,SetupMenu,SpectatorMenu,TeamSelectionMenu}.kt
       src/main/kotlin/net/lumalyte/lumasg/listeners/{AdminWandListener,ChestListener,CustomItemListener,FishingListener,PlayerListener}.kt
Files read: 14
Findings: 20

============================================================
CRITICAL
============================================================

### [SEV: crit] listeners/AdminWandListener.kt:88-99 — Wand edits to arenas are not persisted to the database
**What:** `onInteract` updates the arena via `arena.copy(spawnPoints = …)` and calls `arenaService.addToCache(updated)`. `addToCache` (ArenaService.kt:53) only mutates the in-memory `cache` map; it does not call `arenaRepo.save(...)`. The next time `ArenaService.loadAll()` runs (e.g., on `/sg admin reload` or server restart) the arena is re-read from the database, so every spawn point and arena center set with the wand is silently lost.
**Why it matters:** This is a data-loss bug reachable in normal use. Admins will spend time carefully placing spawn points, restart the server, and find the arena reverted. The companion `create` subcommand (SGCommand.kt:460) does persist because it uses the suspending `arenaService.createArena`; the wand path silently diverges.
**Suggested fix:** Make `addToCache` suspending and have it call `arenaRepo.save(arena)` (or expose a separate `saveArena(arena)` and call that from the listener). Better, replace the ad-hoc `addToCache` writes with a single suspend `arenaService.setSpawnPoint(...)` / `setCenter(...)` that persists.
**Confidence:** high

### [SEV: crit] gui/GameBrowserMenu.kt:65-77 — Players can join games already in Active/Deathmatch phase
**What:** The click handler only checks `isPlayerInGame` and the size cap; it never inspects `game.phase`. The lore even displays the current phase, but the button is enabled regardless. `Game.addPlayer` itself has no phase guard (Game.kt:105), so a click on an in-progress game teleports the player in, runs `playerStateManager.saveAndPrepare`, and adds them to a live match. Same risk in the per-arena join path exposed by `SGCommand.start` → click flow.
**Why it matters:** Game-integrity exploit. A player (or admin abusing `addplayer`) can join mid-fight, appearing at a fresh spawn inside the arena with their pre-game loadout. Spectators also gain the ability to re-enter as live players. Combined with the spectator/elimination handling, this is a complete state desync between game phase and player state.
**Suggested fix:** Reject the click unless `game.phase is GamePhase.Waiting || game.phase is GamePhase.Countdown`, and surface a message to the viewer. The browser should also visually grey out (or omit) buttons for games in Active/Deathmatch/Grace.
**Confidence:** high

============================================================
HIGH
============================================================

### [SEV: high] commands/SGCommand.kt:434-464 — `@Async` `create` command touches Bukkit API off the main thread
**What:** `create` is annotated `@Async` and `suspend`, but inside the body it reads `player.location` (line 460, passed to `arenaService.createArena(...)`) and calls `player.sendMessage(...)` at lines 446, 449, 457, 462, 463. The Nexus `@Async` annotation dispatches the coroutine to a worker pool, not the Bukkit main thread; reading `Player.location` and invoking `Player.sendMessage` from a non-Bukkit thread is a classic main-thread violation that Paper will eventually guard with hard exceptions.
**Why it matters:** Correctness. Under load this can throw `IllegalStateException("Player accessed from async thread")` or, worse, produce silently stale/half-applied state. The other `@Async` handlers in this file (`stats`, `leaderboard`) only touch `sender.sendMessage` after database calls, but `create` is far heavier on Bukkit API.
**Suggested fix:** Drop `@Async` (the operation is trivially fast and only writes one DB row), or wrap the Bukkit-touching code in `withContext(bukkitDispatcher) { ... }` before/after the repository call. The same pattern needs auditing in any future `@Async` subcommand.
**Confidence:** high

### [SEV: high] commands/SGCommand.kt:296-319 — `/sg forcestart` is a complete no-op
**What:** `forceStart` validates that the game exists, is in `Waiting`, and has at least one player, then sends a confirmation message. There is no code path that changes `game.phase`, cancels the countdown, or otherwise advances the game. The inline comment even concedes the command "doesn't" do anything beyond a message.
**Why it matters:** Logic bug that breaks a feature. The `/sg forcestart` command is documented in the help text (line 599) and listed in the admin guide; running it is indistinguishable from a successful start to the operator even though nothing happened. This also defeats the "min players" gating that is presumably the whole point of the command.
**Suggested fix:** Implement the actual force-start. `Game` exposes `cancelCountdown()` (Game.kt:225) and a `skipGracePeriod()` stub (Game.kt:445, currently empty). Either skip directly to the countdown's terminal tick and set `phase = GamePhase.Grace`, or call into a new `Game.forceStart()` that bypasses the countdown loop.
**Confidence:** high

### [SEV: high] listeners/CustomItemListener.kt:140-179 — Bomb damage bypasses friendly-fire / phase rules
**What:** `onBombExplode` calls `world.getNearbyEntities(...).filterIsInstance<Player>()` and applies `target.damage(damage)` and a custom `target.velocity` knockback, filtering only by `throwerId`. It does not check `game.phase`, `isPvpEnabled()`, or `config.game.teams.friendlyFire`. The general `PlayerListener.onEntityDamage` guard (PlayerListener.kt:51) is bypassed entirely because the damage originates from `Entity.damage()`, not from a Bukkit damage event of the right class.
**Why it matters:** Game-integrity exploit. In a team game with `friendlyFire = false`, throwing a bomb at a teammate deals damage and knockback that the normal friendly-fire handler would have cancelled. In Waiting/Countdown/Grace phases, bombs still hurt and shove players around. The same is true for the Poison Bomb handler (line 196-217) and the Fire Bomb (line 75-110), though fire is indirect.
**Suggested fix:** Resolve the thrower's `Game` and the target's `Game` from `gameManager`, then enforce the same rules `PlayerListener` uses (phase gate, friendly-fire, target-is-alive). Skip damage/knockback when the rules say no PvP. Apply damage via a synthetic `EntityDamageByEntityEvent` (or use Bukkit's damage source API) so existing listeners can intercept it.
**Confidence:** high

### [SEV: high] listeners/ChestListener.kt:57-88 — Cross-arena chest filling when multiple arenas share a world
**What:** `onChunkLoad` finds "the game running in this world" by scanning `getAllActiveGames()` for any game whose `arena.worldName == worldName`. If two arenas (e.g., a 50-player "lobby" arena and a 24-player "duels" arena) are both loaded and active in the same world, the loop uses the *first* matching game to call `determineTier(state, game)` and `fillChestSync(state, tier, game.lootMode)` for every chest in the chunk, including chests in the *other* arena's bounds.
**Why it matters:** Game-integrity. Chests in arena B get filled with arena A's loot table and tier. The same bug applies to `onChestOpen` (line 96-115), which uses `gameManager.getGameForPlayer(player)` to find the game, but the world-arena collision is most visible on chunk load because chunk loading is a server-wide event, not per-player. The `filledChests` set is also global to the listener, so two games in the same world share the same "already filled" tracking.
**Suggested fix:** Scope the fill to chests whose block location is actually inside the game's arena (e.g., `chest.location.distanceSquared(arena.center.toBukkit()!!) <= arena.radius^2`). Pass the world via the chunk and resolve the correct game by arena, not by world. Consider keying `filledChests` by `Pair<arenaName, location>` to keep games isolated.
**Confidence:** high

============================================================
MEDIUM
============================================================

### [SEV: med] listeners/ChestListener.kt:165-166 — `toBlockKey` has hash collisions that cause chests to be skipped
**What:** `Location.toBlockKey()` returns `(blockX shl 32) or (blockZ and 0xFFFFFFFF) xor (blockY shl 48)`. Mixing `|` and `^` at the same precedence means the expression parses as `((x shl 32) | (z & mask)) xor (y shl 48)`. The top 16 bits of the result are `xUpper XOR y` — so two distinct triples such as `(x=0, y=5, z=0)` and `(x=5, y=0, z=0)` produce the same key. Chests at those locations collide in `filledChests` (a `Set`), and the second one to be considered is treated as "already filled" and silently skipped.
**Why it matters:** Chests can end up empty inside a round, with no error. The bug only triggers for positions whose y differs from some xUpper by XOR, but those positions are common in normal arenas.
**Suggested fix:** Replace the key with a stable, collision-free encoding — e.g. `(x.toLong() and 0xFFFFFFL) shl 40 or ((z.toLong() and 0xFFFFFFL) shl 16) or (y.toLong() and 0xFFFFL)`, or better, use `Location.toBlockKey()` from Paper directly, or use a `Set<Location>` (block locations are short-lived) or a nested `Map<String, ConcurrentHashMap.KeySetView<Location>>` keyed by arena name.
**Confidence:** high

### [SEV: med] listeners/PlayerListener.kt:107-131 — `onPlayerDeath` schedules elimination in a coroutine that the game scope may have already cancelled
**What:** `event.isCancelled = true; event.drops.clear(); game.scope.launch { game.eliminate(...); ... }`. `game.scope` is the per-game `SupervisorJob` (Game.kt:55-59). If the game has been stopped (`/sg stop` or end of game) between the death event firing and the coroutine resuming, `scope.launch` will throw `CancellationException` and the body — including kill recording, rewards, spectator mode — never runs. The death message has already been cleared, so the player just dies silently with no record of the kill, no reward, and (depending on the timing) potentially stuck in non-spectator mode mid-air waiting for the respawn handler.
**Why it matters:** Edge-case bug. During the brief window where the game scope is being torn down, deaths are lost. It also makes the "kill commands" reward unreliable — a kill command can vanish.
**Suggested fix:** Drive the death bookkeeping on the main thread (it's already running there) for the parts that must succeed (`drops.clear()`, `eliminate`'s state mutations), and only defer the async parts (stats save, reward dispatch) using a scope that survives game teardown, e.g. `plugin.scope` or a dedicated executor. At minimum, wrap the coroutine launch in a try/catch and log a warning.
**Confidence:** med

### [SEV: med] listeners/PlayerListener.kt:135-147 — Spectator mode applied one tick after respawn; player briefly vulnerable
**What:** `onPlayerRespawn` only sets `event.respawnLocation`; the actual `player.gameMode = GameMode.SPECTATOR` is scheduled with `runTaskLater(..., 1L)`. For that one tick the player is alive (game mode `SURVIVAL`), at a spawn point inside the arena, with their pre-death inventory. If anything in that window fires a damage event or pickup, the normal in-game handlers (pvp, item pickup, etc.) will treat them as a live participant.
**Why it matters:** Edge-case bug. With Paper's typical 50ms tick, the window is short, but a fast attacker or a delayed projectile can damage/kill the "spectator" during it, which is also a state desync between the death event and the spectator mode.
**Suggested fix:** Set `player.gameMode = GameMode.SPECTATOR` synchronously in the respawn handler, or use `event.player.setGameMode(GameMode.SPECTATOR)` before the method returns. Respawn location can stay scheduled if needed.
**Confidence:** med

### [SEV: med] listeners/PlayerListener.kt:51-89 — Eliminated/spectator players still receive damage events
**What:** `eliminate` flips `isAlive = false` and moves the UUID into `spectators`, but the UUID stays in `_players`, so `gameManager.getGameForPlayer(uuid)` still returns the game. The `onEntityDamage` and `onDamage` handlers therefore run for dead/spectating players. Spectator mode prevents most damage at the Paper level, but the `onDamage` tracker still increments `damageTaken`/`damageDealt` (PlayerListener.kt:99-103) on any event that slips through (e.g., direct `target.damage(damage)` from CustomItemListener).
**Why it matters:** Game-integrity. Combined with the bomb/poison findings, the stats counters are polluted by post-elimination damage. The friendly-fire skip in the `else` branch (line 80-86) also runs for dead players, which is meaningless but harmless.
**Suggested fix:** Early-out in both handlers if `game.players[uuid]?.isAlive == false` (or `uuid in game.spectators`). This also makes the custom-item findings above easier to write correctly.
**Confidence:** med

### [SEV: med] gui/GameBrowserMenu.kt:71-76 — TOCTOU race allows `maxPlayers` overflow on click
**What:** The click handler reads `game.players.size >= game.arena.maxPlayers` and then calls `game.addPlayer(viewer)` unconditionally. Between the check and the add, other concurrent clicks (each running on the main thread but interleaved at tick boundaries) can push the player count over `maxPlayers`. There is no synchronized capacity check inside `Game.addPlayer` either (Game.kt:105).
**Why it matters:** Edge-case bug. The arena's `maxPlayers` invariant can be violated by N-1 ms bursts of clicks. With N=24 a single tick of ~50ms can over-fill the game.
**Suggested fix:** Add a capacity check inside `Game.addPlayer` (e.g., `require(_players.size < arena.maxPlayers) { "Game is full" }`) and have callers handle the exception or pre-check atomically. Better, gate the join through a single synchronized entry point on `Game`.
**Confidence:** med

### [SEV: med] gui/TeamSelectionMenu.kt:33-35 — `team.add(...)` return value ignored; "Joined" message sent even on full team
**What:** `Team.add` (Team.kt:27) returns `false` if `isFull`, but `TeamSelectionMenu.open` ignores the return value and unconditionally sends `§aJoined Team ${team.id}!`. The player is not added (the mutation is rejected by the `if (isFull) return false`), but they get the success message and their inventory close fires.
**Why it matters:** Logic bug. Players believe they joined a team when they did not. With 9 teams shown in a 9-slot GUI there's no way to know which team is full without clicking, and clicking on a full team leaves them confused.
**Suggested fix:** Capture the boolean, send a different message on `false`, and ideally re-open the menu or refresh the icons. Also consider filtering the displayed teams to those with `!isFull`.
**Confidence:** med

### [SEV: med] gui/SpectatorMenu.kt:17-51 — Target list captured at open time; cross-world teleport not handled
**What:** `open` iterates `game.alivePlayers` once and captures `Bukkit.getPlayer(gp.uuid)` and binds it in the click handler. If the target disconnects before the click, the teleport uses the player's last known `target.location` (which can be a world that has since been unloaded, leading to silent teleport failure or a teleport-into-void). The `spectator.teleport(target.location)` does not check `target.world == spectator.world`, so cross-world teleports may be rejected by Bukkit or land the spectator in a totally different map.
**Why it matters:** Edge-case bug. Spectator UX is broken for the common "target died/went to lobby while menu was open" case and for "target teleported to arena hub."
**Suggested fix:** Re-resolve `Bukkit.getPlayer(gp.uuid)` at click time; if the player is offline or in a different world, send a friendly message and do nothing. Optionally, gate by `target.world == spectator.world` before teleporting.
**Confidence:** med

============================================================
LOW
============================================================

### [SEV: low] listeners/CustomItemListener.kt:40-71 — Cooldown is per-player, not per custom item
**What:** `cooldowns` is `ConcurrentHashMap<UUID, Long>` and the only key is the player UUID. Using any custom item sets the cooldown for every other custom item. The 1-second window is enough to make a player feel "stuck" if they rapidly switch between a Fire Bomb and a regular custom item in the same hand.
**Why it matters:** Minor design flaw. Not a security or data issue, but the cooldown does not match its apparent purpose.
**Suggested fix:** Key the cooldown by the `CustomItem` key (e.g., `NamespacedKey`) so each item has its own timer. Use `cooldowns.compute(...)` or a `Map<NamespacedKey, Long>` nested under the player UUID.
**Confidence:** med

### [SEV: low] listeners/ChestListener.kt:38 — Unused `MiniMessage` field
**What:** `private val mm = MiniMessage.miniMessage()` is declared and initialized but never read. Dead field.
**Why it matters:** Style/quality.
**Suggested fix:** Remove the field and the import.
**Confidence:** high

### [SEV: low] listeners/ChestListener.kt:154-163 — `scheduleRefill` task is not tracked or cancelled on plugin/game teardown
**What:** `runTaskLater(...)` returns a `BukkitTask` whose handle is dropped on the floor. If the game ends (scope cancellation, server shutdown, `cleanup`) within `refillTimeSeconds`, the task still fires later and calls `filledChests.clear()` and `game.broadcastRefillMessage()` on a game whose scope is dead. The clear itself is harmless, but the broadcast is a no-op against a torn-down state and can spam logs / confuse any plugin hooking broadcasts.
**Why it matters:** Defensive-coding nit. The leak doesn't break the feature, but it makes the listener fragile against server reloads and rapid game restarts.
**Suggested fix:** Store the `BukkitTask` in a per-game or per-listener map and cancel it in `@PreDestroy` and in `Game.cleanup()`. Or schedule via `game.scope.launch { delay(...); withContext(bukkitDispatcher) { ... } }` so the task is cancelled automatically when the game scope dies.
**Confidence:** med

### [SEV: low] listeners/AdminWandListener.kt:38-40 — `beamTasks` values are mutable `MutableList` not protected for concurrent access
**What:** `ConcurrentHashMap<UUID, MutableList<BukkitTask>>` is concurrent on the map, but `MutableList` itself is not. `showSpawnBeams` mutates the list (lines 169-176) and `hideBeams` iterates and removes it (line 180-182). Today both are called from the Bukkit main thread (events and `setSelectedArena`), so this is single-threaded in practice. The use of `ConcurrentHashMap` invites future contributors to call into this from off-thread code.
**Why it matters:** Defensive-coding nit. Latent footgun for future refactors.
**Suggested fix:** Either remove the `ConcurrentHashMap` (if everything stays main-thread) and document it, or wrap the values in a thread-safe structure (`CopyOnWriteArrayList` or a `synchronized` block).
**Confidence:** low

### [SEV: low] gui/ArenaSelectionMenu.kt:22-67 — `getAllActiveGames()` is scanned per arena to find the active game
**What:** The map call is `getAllActiveGames().firstOrNull { it.arena.name == arena.name }`, which is O(arenas × games) per menu open. With dozens of arenas and concurrent games this becomes a non-trivial allocation on every GUI open. The team is also bucketing by name equality but `ArenaService.getArena(name)` is case-insensitive (`name.lowercase()`) while `arena.name` in `getGameByArena` is also lowercased (GameManager.kt:82), so this happens to work — but the case-sensitivity contract is implicit and easy to break.
**Why it matters:** Performance / readability nit, not a correctness bug.
**Suggested fix:** Build a `Map<arenaName, Game>` once before the loop (e.g., `getAllActiveGames().associateBy { it.arena.name }`) and look up by key. Add a `findGameInArena(arenaName)` to `GameManager` so the equality rule lives in one place.
**Confidence:** med

### [SEV: low] commands/SGCommand.kt:203-226 — `/sg start` doesn't validate arena enabled or world loaded
**What:** `start` resolves the arena, refuses to start if a game already exists, then calls `createGame`. It does not check `arena.enabled` (used by `ArenaService.getAvailableArenas`) nor that `arena.worldName` is currently loaded. Starting a game whose world is not loaded leaves `arena.center.toBukkit()` returning null and silently falls back to the player's location.
**Why it matters:** Minor. A disabled arena can be started; an arena in an unloaded world lands the game at a useless location. Both are operator errors, but the command should at least warn.
**Suggested fix:** Check `arena.enabled` and `Bukkit.getWorld(arena.worldName) != null`; fail with a clear message.
**Confidence:** low

### [SEV: low] commands/SGCommand.kt:245-273 — `addplayer` produces a malformed error if `currentGame` is null
**What:** After checking `isPlayerInGame`, the error message string-interpolates `currentGame?.arena?.name`, which is `null` if the lookup returned null (a theoretical race between the two queries). The player sees `arena 'null'`.
**Why it matters:** Cosmetic. Indicates a small TOCTOU between `isPlayerInGame` and `getGameForPlayer`.
**Suggested fix:** Either re-check after re-fetching, or use `currentGame?.arena?.name ?: "<unknown>"` so the message stays sensible.
**Confidence:** low

============================================================
TEST-COVERAGE GAPS
============================================================

### [SEV: med] (Test gap) — No tests exist for any of the 14 files in this batch
**What:** `src/test/kotlin/...` contains no `*CommandTest`, `*ListenerTest`, `*MenuTest`, `*GuiTest`, or `*ServiceTest` for the GUI, command, or listener layer. All 14 files in this batch have zero direct unit or integration coverage. A grep across `src/test` for `SGCommand`, `AdminWand`, `ChestListener`, `PlayerListener`, `CustomItemListener`, `FishingListener`, `GameBrowser` returns 0 matches.
**Why it matters:** Several of the findings above (mid-game join, friendly-fire bypass, toBlockKey collisions, async create) are exactly the kind of behaviour that is easy to test in isolation with MockBukkit or paper-mock, and impossible to catch reliably in production.
**Suggested fix:** Add at minimum:
- `GameBrowserMenuTest` — clicking a game in Active/Deathmatch must reject; clicking a full game must reject.
- `AdminWandListenerTest` — left-click adds a spawn, right-click sets center, persistence is verifiable.
- `ChestListenerTest` — `toBlockKey` is bijective for plausible Y range; cross-arena fill does not occur; phase gating in `onChunkLoad` works.
- `CustomItemListenerTest` — bomb damage respects `friendlyFire`, respects `isPvpEnabled` / `GamePhase`; poison bomb likewise; cooldown is per-item.
- `PlayerListenerTest` — death during scope cancellation does not lose kill rewards; respawn sets spectator synchronously; eliminated players do not count toward damage stats.
- `SGCommandTest` — `forcestart` actually changes phase; `create` is main-thread safe; race between `isPlayerInGame` and `addPlayer` is rejected.
- `SpectatorMenuTest`, `TeamSelectionMenuTest`, `LeaderboardMenuTest`, `FishingListenerTest` — basic open/click and edge cases (offline target, full team, missing config).
**Confidence:** high

============================================================
END OF REPORT
============================================================
