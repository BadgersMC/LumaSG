# Audit Report: audit-game

Scope: `src/main/kotlin/net/lumalyte/lumasg/game/*.kt` (16 files). Focus: dupe-exploits, game-state desync, save/restore correctness, test coverage.

Existing test coverage in this package: only `GameLifecycleTest` (2 trivial data-model tests) and `GameProfilerTest`. The rest of the game/ package is untested.

---

### [SEV: crit] game/PlayerStateManager.kt:61 — `saveLocation=false` corrupts the saved location field
**What:** When `config.game.saveLocation` is false, the saved `location` is set to `spawnLocation` (the arena spawn the player was just teleported to), not the player's pre-game position. On restore, the player is teleported to the arena spawn — not their original location.
**Why it matters:** An admin who disables `saveLocation` expects the lobby destination to take over; instead, players are dumped back at an arbitrary arena spawn. If the game world is unloaded by the time restore runs, `player.teleport(spawnLocation)` may NPE or teleport to a now-invalid world, or strand the player inside a still-loaded arena. The field silently lies about what it represents.
**Suggested fix:** Make `SavedState.location` nullable; only populate it when `saveLocation=true`. In `restore`, treat `null` as "use lobby".
**Confidence:** high

---

### [SEV: crit] game/Game.kt:164 — `reconnectPlayer` overwrites the player's pre-game inventory with mid-game loot
**What:** `reconnectPlayer` calls `playerStateManager.saveAndPrepare(player, spawn)`. `saveAndPrepare` writes the player's *current* inventory (mid-game loot) into the `saved` map, overwriting the original pre-game snapshot taken on first join.
**Why it matters:** When the game later ends, `restoreAllPlayers` calls `restore`, which returns the player to the snapshot — which is now their mid-game loot, not the pre-game inventory. The player's real items are silently lost.
**Suggested fix:** Skip `saveAndPrepare` on reconnect — the original saved state is already in the map. If a fresh teleport is needed, do it without re-snapshotting. Consider a separate `respawn` method that does only teleport + permission refresh.
**Confidence:** high

---

### [SEV: crit] listeners/PlayerListener.kt:121-125 (game batch: same pattern in game/Game.kt:240-245 and game/Celebration.kt:240-245) — server-console command injection via `killCommand` / `winCommand`
**What:** `config.rewards.killCommand` and `config.rewards.winCommand` are templates with `<player>` and `<kills>` replaced via naive `.replace("<player>", player.name)`. The resulting string is dispatched with `plugin.server.dispatchCommand(consoleSender, cmd)`.
**Why it matters:** Player names flow into a server-console command without quoting or sanitisation. A name containing `;`, ` `, or other command separators causes the rest of the name to be interpreted as additional commands, executed as the *console* (highest privilege). The same pattern is used in three places (kill reward, win reward, celebration), all via the same template. Minecraft's name validation partially mitigates this, but nicknames/display-names/imported sources are not always validated.
**Suggested fix:** Reject names containing `;`, ` `, or quote characters before substitution; or, better, split the command on the first space and only substitute into the first token, treating the rest as opaque arguments.
**Confidence:** med

---

### [SEV: crit] game/TeamManager.kt:46-53 and game/TeamQueueManager.kt:82-83 — accepted invite can leave a player in two teams (dupe-exploit / oversized team)
**What:** `TeamManager.acceptInvite` calls `team.add(invitee)` and `playerTeams[invitee] = team.id` without first removing the invitee from any existing team. `TeamQueueManager.accept` does the equivalent for the pre-game queue — `invitation.team.add(player.uniqueId)` is unconditional, and `preGameTeams[player.uniqueId] = invitation.team` overwrites the pointer while the old team's `members` list still contains the player.
**Why it matters:** A player can belong to two teams at once. Both teams' `isAlive` / `isFull` checks can see the same player, breaking the `aliveTeams.size <= 1` win condition and effectively allowing a team to exceed `maxSize`. This is a direct dupe-exploit.
**Suggested fix:** Before adding the player to the new team, iterate all teams (and `preGameTeams`) and remove the player's UUID. `TeamManager.acceptInvite` already calls `removeFromTeam(invitee)` on line 50, but only after `pendingInvites.remove` — verify the order is right. In `TeamQueueManager.accept`, add an explicit `for (t in preGameTeams.values) t.remove(player.uniqueId)` and remove from `preGameTeams` for any previous entry.
**Confidence:** high

---

### [SEV: high] game/Team.kt:19-21 — `Team.isAlive` is false whenever a member is offline, even if not eliminated
**What:** `val isAlive: Boolean get() = !isEliminated && members.any { Bukkit.getPlayer(uuid)?.isOnline == true }`. A team is "alive" only if at least one member is currently online. `eliminated` is not the deciding factor; online status is.
**Why it matters:** `Game.checkWinCondition` (Game.kt:438-441) uses `teamManager.getAliveTeams()` to end the game. If all members of a team briefly disconnect (lag spike, server restart, network blip), the team becomes "dead" instantly and the opponent wins — even though the members are still in the game and will reconnect. A player can deliberately disconnect to force a 1-team-remaining win for the opposing side, or to grief their own team by making them appear eliminated. The team's `eliminate()` is never called, so `isEliminated` stays false; the alive state simply gets re-derived as false the moment the last member goes offline.
**Suggested fix:** Track a per-team eliminated flag (set by `eliminate()`) and base `isAlive` only on `!isEliminated`. Disconnect should *not* mark the team as dead; the alive player set is something the game tracks separately from the `Team` value object.
**Confidence:** high

---

### [SEV: high] game/Game.kt:138-151 — `eliminate(uuid)` mutates state for UUIDs that are not in the game
**What:** The function unconditionally removes from `disconnectedPlayers`, prepends to `eliminationOrder`, calls `teamManager.removeFromTeam(uuid)`, and either adds to `spectators` or calls `removePlayer(player)` — even when `_players[uuid]` is null.
**Why it matters:** A buggy caller (or a double-eliminate, or a stale event after the player was already removed) leaves ghost entries in `eliminationOrder`, `spectators`, and possibly removes a real member from a team. `allParticipants()` (Game.kt:188) returns `_players.keys + spectators`, so ghost spectators are sent death/border messages and included in celebration `participants`. Persistence then records a winner for a ghost UUID. The class has no precondition to assert the UUID is a member.
**Suggested fix:** Add `if (_players[uuid] == null) return` at the top. Idempotent eliminate should also short-circuit when `_players[uuid]?.isAlive == false`.
**Confidence:** high

---

### [SEV: high] game/WorldManager.kt:33, 46-47, 192-199 — cleanup overwrites world border with the uninitialised default if `setup()` is skipped
**What:** `originalBorderSize` is initialised to `60_000_000.0` (line 33). If `setup()` early-returns (e.g., `arenaWorld()` or `arenaCenter()` returns null), the snapshot is never taken. `cleanup()` then unconditionally calls `restoreWorldSettings`, which sets the border to `60M` — clobbering whatever border the world actually had.
**Why it matters:** State corruption reachable when an arena's world fails to load (renamed, unloaded, worldName typo). A second, unrelated game on the same world would have its border reset to 60M. The "no-op" path is destructive.
**Suggested fix:** Add a `private var snapshotTaken: Boolean = false` set to `true` in `setup()` after the snapshot, and guard `restoreWorldSettings` on it.
**Confidence:** med

---

### [SEV: high] game/NameplateManager.kt:30-35, 44-49 — `stop()` and `disableNameplateHiding()` launch fire-and-forget coroutines
**What:** Both methods call `scope.launch { withContext(bukkitDispatcher) { restoreAll() } }` without awaiting. The launched coroutine is decoupled from the caller.
**Why it matters:** `stop()` is called from `Game.endGame` and `Game.cleanup` (Game.kt:452, 481). After `endGame` returns, `scope.cancel("Game ended")` is invoked (Game.kt:475). The fire-and-forget restore coroutine may be cancelled before it runs, leaving players with `hideEntity` applied. The next time they enter a normal server context, they can see other players, but the `visibility` map is also not cleared (the `visibility.clear()` is inside `restoreAll`), so the next game's `start` would observe stale state.
**Suggested fix:** Make `stop()` a `suspend` function (or expose a `suspend fun stopSuspending()`) and await the restore. If the caller can't suspend, use a `CompletableDeferred<Unit>` and wait. Or, since the main thread is required, call `restoreAll()` synchronously from the caller when the caller is already on the main thread.
**Confidence:** med

---

### [SEV: med] game/Game.kt:162 — `reconnectPlayer` picks a non-deterministic spawn point
**What:** `val spawn = arena.spawnPoints.getOrNull(_players.keys.toList().indexOf(player.uniqueId))?.toBukkit()`. The `.toList()` on a `ConcurrentHashMap` is non-deterministic; the order is unspecified. The reconnecting player can spawn at any of the configured spawn points (or fall back to center) regardless of where they originally were.
**Why it matters:** A player who legitimately disconnects and reconnects can respawn at a different team's spawn, into a danger zone, or in the deathmatch inner ring. The "spawn point" is effectively a coin flip.
**Suggested fix:** Persist the player's original spawn index on the `GamePlayer` value (e.g., `var spawnIndex: Int = -1`) when they first join, and use that on reconnect.
**Confidence:** high

---

### [SEV: med] game/Game.kt:106-108 — `addPlayer` spawn index is total count, including eliminated-in-map players
**What:** The spawn index is `arena.spawnPoints.getOrNull(_players.size)`. `_players` includes eliminated-but-spectating players (their `isAlive` is false but they remain in the map). If 12 spawn points are configured and the 12th player is eliminated (still in `_players`), the 13th joiner gets `arena.center` (the fallback) instead of an actual spawn point.
**Why it matters:** Two players end up at the same fallback (center) when there are still free spawn points. Centre-fallback usually overlaps with the deathmatch inner ring or a hazard — minor gameplay bug, but it's a state desync with the configured layout.
**Suggested fix:** Index off `players.count { it.value.isAlive }` or store a monotonic `nextSpawnIndex` counter on the game.
**Confidence:** med

---

### [SEV: med] game/Game.kt:66, 141 — `eliminationOrder` is a non-thread-safe `mutableListOf`
**What:** `private val eliminationOrder = mutableListOf<UUID>()` (line 66). `eliminate()` calls `eliminationOrder.add(0, uuid)` (line 141) from coroutine code that may be on any dispatcher, including the main thread during event handlers and the Bukkit dispatcher in other paths.
**Why it matters:** Two near-simultaneous eliminations (e.g., a multi-kill, AoE damage killing two players, or events from different threads) can race on the underlying `ArrayList`. Resulting behaviour ranges from silent corruption to `IndexOutOfBoundsException` (the add-at-zero shifts the whole backing array).
**Suggested fix:** Use `java.util.Collections.synchronizedList(mutableListOf())` or switch to a `CopyOnWriteArrayList` (writes are rare relative to reads, fits the access pattern).
**Confidence:** med

---

### [SEV: med] game/Celebration.kt:80-102 — unbounded image fetch and decode from external URL
**What:** `renderPixelArtHead` calls `URL(apiUrl).openConnection().getInputStream()` and decodes the result with `ImageIO.read()`. The URL is templated from `config.rewards.winnerAnnouncement.pixelArt.apiUrl` (admin-controlled), but the substitution does not URL-encode `<name>`. No size cap, no content-type check, no domain pinning.
**Why it matters:** ImageIO is a known DoS surface (decompression bombs, oversized image allocations). With a 5-second read timeout a slow trickle is bounded, but a single multi-megapixel PNG or animated GIF can OOM the JVM. SSRF is also possible if the admin mistypes the URL: `http://internal-service/...` would be fetched from the game server.
**Suggested fix:** URL-encode `<name>` and `<uuid>`; pin the host to the configured skin service; cap the download with `conn.setRequestProperty("Range", "bytes=0-1048576")` and reject anything past 1 MB; verify `Content-Type` starts with `image/`.
**Confidence:** med

---

### [SEV: med] game/Game.kt:552-555 — `broadcastDeathMessage` ignores configured death/kill templates
**What:** `broadcastDeathMessage` calls `deathMessage(victim, killer)` with the default `config = null` parameter. The `deathMessage` function then uses its hardcoded fallback templates instead of `config.messages.deathNatural` and `config.messages.deathByPlayer`.
**Why it matters:** Server admins who customised the death/kill message templates in config will silently see the hardcoded defaults. The configured template is read nowhere. `killNotification` has the same problem.
**Suggested fix:** Pass `config`: `val msg = deathMessage(victim, killer, config)`. Same for the kill notification.
**Confidence:** high

---

### [SEV: med] game/GameBarrierManager.kt:23-28 and game/WorldManager.kt:73-78 — `for` over a `ConcurrentHashMap` followed by `clear()` is correct but subtle
**What:** Both barrier managers iterate `barriers.entries` to restore blocks, then call `barriers.clear()`. The iteration is weakly consistent on a `ConcurrentHashMap`, so a concurrent `placeBarrier` (which can happen on the main thread from `runLifecycle`) can interleave.
**Why it matters:** If a `placeBarrier` runs concurrently with `removeAll`, the new barrier is stored in the map but is not restored (because the iteration has already passed that key), and `clear()` removes it from the map — leaving a permanent `Material.BARRIER` block in the world. The race is small (both call sites are wrapped in `withContext(bukkitDispatcher)`, so they should not overlap), but it depends on every caller honouring that contract.
**Suggested fix:** Document the main-thread requirement with `@MainThread` and add a `check(Bukkit.isPrimaryThread())` assertion. Or, swap the data structure for one that supports atomic swap-and-iterate (e.g., copy-on-write snapshot).
**Confidence:** med

---

### [SEV: med] game/GameScoreboard.kt:46, 67-75 — non-thread-safe `participants` mutated from multiple threads
**What:** `private val participants = mutableSetOf<UUID>()` (line 46). `addPlayer`/`removePlayer` (lines 67-75) mutate it directly. `start()`'s render coroutine reads it on the Bukkit dispatcher, and `addPlayer` is called from listeners on the main thread — but `addPlayer` is also called from `reconnectPlayer` and from `addSpectator` inside coroutine code dispatched to the main thread. In practice everything lands on the main thread, but the type gives no guarantee.
**Why it matters:** A future refactor that calls `addPlayer` from a non-main thread (e.g., a coroutine resumed off-thread) would cause `ConcurrentModificationException` or silent loss of scoreboard membership. The `GameScoreboard` class doesn't enforce main-thread access.
**Suggested fix:** Switch to `Collections.newSetFromMap(ConcurrentHashMap())` and add a `check(Bukkit.isPrimaryThread())` in mutation methods.
**Confidence:** low

---

### [SEV: low] game/Game.kt:181-186 — `removePlayer(Player, Boolean, Boolean)` accepts parameters it never reads
**What:** `removePlayer(player: Player, teleportToLobby: Boolean = true, restoreState: Boolean = true)` declares two parameters. The body uses neither; teleport/restore is controlled entirely by `config.lobby.teleportOnEnd` and `config.game.restoreInventory` in `PlayerStateManager.restore`.
**Why it matters:** Misleading API. Callers think they can override the policy per call but cannot. Dead parameters become load-bearing if someone "fixes" the implementation by actually honouring them, breaking the existing flow.
**Suggested fix:** Remove the parameters, or wire them through to `PlayerStateManager.restore` and the teleport decision.
**Confidence:** high

---

### [SEV: low] game/Celebration.kt:83-88 — `URLConnection.getInputStream()` is not closed
**What:** The `try` block returns `ImageIO.read(conn.getInputStream())` and the catch returns `null`. The stream is never closed in a `finally` block. The connection's underlying socket is eventually finalised, but not promptly.
**Why it matters:** Resource leak. Under high frequency (many short games), file descriptors and sockets can accumulate. A common minor finding, especially with HTTP keep-alive.
**Suggested fix:** Use Kotlin's `.use { }` extension on the stream; or wrap in try/finally with explicit `close()`.
**Confidence:** med

---

### [SEV: low] game/TeamQueueManager.kt:125-138 — `broadcastQueueStatus` is O(online × activeGames) per tick
**What:** The inner loop checks `gameManager.getGameForPlayer(player.uniqueId) == null` for every online player. `getGameForPlayer` iterates `activeGames.values`. With N online players and M games, this is O(N×M) per broadcast.
**Why it matters:** Performance, not correctness. With many simultaneous games and a busy server, the broadcast tick can be slow.
**Suggested fix:** Build a `Set<UUID>` of all in-game UUIDs once per tick (or once per `activeGames` change), then check membership in O(1) per player.
**Confidence:** low

---

### [SEV: low] game/Game.kt:101-103 — `addDummy` is a public, production-visible testing API
**What:** `addDummy` adds a non-`Player` entry to `_players` directly, bypassing the scoreboard, state save, and `PlayerStateManager` setup that real joiners get. The function is `fun` (public) with no annotation marking it test-only.
**Why it matters:** A bug elsewhere (a misconfigured command handler, a stale admin script) could call `addDummy(realUuid, realName)`, double-counting that player in `alivePlayers`, `getPlayerCount`, and the win-condition check. There's no guard rejecting the call when real players are present.
**Suggested fix:** Mark it `internal` (Kotlin's module-scoped) or wrap in an `if (Bukkit.getPlayer(uuid) == null) { ... }` guard. Better: gate on a `dev` mode flag.
**Confidence:** low

---

### [SEV: low] game/WorldManager.kt:183-190 — `clearDrops` uses hardcoded 300-block radius
**What:** `clearDrops` filters by `it.location.distanceSquared(center) <= 300.0 * 300.0`, ignoring `arena.radius`. The sibling `clearAllDrops` (line 141) correctly uses `arena.radius`.
**Why it matters:** For arenas with `radius > 300`, items outside 300 but inside the arena persist past game end. For arenas with `radius < 300`, items in the unselected band are also cleared (probably desired). The two methods disagree about scope, which is a smell.
**Suggested fix:** Use `arena.radius` in both, or delete `clearAllDrops` if `clearDrops` is the only caller.
**Confidence:** low

---

### [SEV: low] game/GameScoreboard.kt:103-105 — scoreboard re-assigned on every render tick
**What:** `render()` iterates `participants.toList()` and writes `Bukkit.getPlayer(id)?.scoreboard = scoreboard` every 2 seconds. This is the *same* scoreboard object the player already has.
**Why it matters:** Setting the scoreboard repeatedly forces the client to re-render the sidebar, causing visible flicker on some clients and wasting packets. There's no `if (p.scoreboard != scoreboard)` guard.
**Suggested fix:** Only assign when the current player's scoreboard differs from the game's.
**Confidence:** low

---

### [SEV: low] game/TeamManager.kt:41 — cryptic `?.isFull != false` check
**What:** `if (getTeamForPlayer(inviter)?.isFull != false) return false`. Reads as "if inviter has no team, return false; if inviter's team is full, return false" but is hard to verify.
**Why it matters:** Style/maintainability. Easy to misread; future refactors could invert the logic accidentally.
**Suggested fix:** Rewrite as `val team = getTeamForPlayer(inviter) ?: return false; if (team.isFull) return false`.
**Confidence:** high

---

### [SEV: low] game/GameSetupValidator.kt:58-64 — `lobbySpawn`/`spectatorSpawn` issues are reported but don't gate `isSetupComplete`
**What:** `validate` adds issues for missing `lobbySpawn` and `spectatorSpawn`, but `isSetupComplete` (lines 70-77) doesn't check them, and no other code rejects an arena that has no lobby.
**Why it matters:** Admins see a warning but can still host games that lack a lobby spawn. Players will spawn at "arena center" per the message, but that's a surprise, not a planned fallback. Inconsistent validation.
**Suggested fix:** Decide if these are warnings (keep in `validate`, exclude from `isSetupComplete`) or blocking (add to `isSetupComplete`). Be explicit.
**Confidence:** low

---

### [SEV: low] game/Game.kt:445-447 — `skipGracePeriod` is a no-op stub
**What:** `skipGracePeriod` has an empty body with a comment "sets grace remaining to 1". It doesn't actually do anything.
**Why it matters:** A debug command calling this will appear to work (no exception) but the grace period will not skip. Operators will be confused. Dead method.
**Suggested fix:** Either implement it (set a flag the `runGracePhase` loop checks) or delete the function and the calling command.
**Confidence:** high

---

### [SEV: low] game/GameManager.kt:144-150 — `shutdown` doesn't await coroutine completion before clearing the registry
**What:** `shutdown` cancels every game's scope and then immediately clears `activeGames`. The `invokeOnCompletion` handlers may still be queued, but `activeGames` is already empty by the time they run — their `activeGames.remove(game.id)` is a no-op. Cancellation is asynchronous; cleanup coroutines (including `Celebration`'s fireworks) can be in-flight when the JVM proceeds to stop the plugin.
**Why it matters:** A reload right after shutdown may see partial state. Fireworks spawned during shutdown may leak entities. Defensive-coding nit.
**Suggested fix:** After cancelling all scopes, run a brief `runBlocking { activeGames.values.forEach { it.scope.coroutineContext[Job]?.join() } }` (with a timeout) before clearing.
**Confidence:** low

---

### [SEV: low] game/Game.kt:188, 222, 562-565 — `allParticipants` and `broadcast` allocate new collections on every call
**What:** `allParticipants()` returns `_players.keys + spectators`, which constructs a new `LinkedHashSet` on every call. `broadcast` iterates it. Hot paths (every render tick, every death message) pay this cost.
**Why it matters:** Minor allocation churn. Not a bug, just defensive-coding nit.
**Suggested fix:** Cache the set, invalidate on `addPlayer`/`removePlayer`/`eliminate`.
**Confidence:** low

---

## Test-coverage gaps

The following untested behaviour is the most important missing coverage in this batch. Each would catch a finding above.

1. **`PlayerStateManager.saveAndPrepare` with `saveLocation=false`** — assert the saved `location` is null/center (per the intended fix), not the arena spawn. Would catch the critical bug at PlayerStateManager.kt:61.
2. **`Game.reconnectPlayer` preserves the original saved state** — call `addPlayer`, set a marker item in the player's inventory, mutate the inventory to game loot, call `handleDisconnect` + `reconnectPlayer`, then `restoreAllPlayers`, and assert the marker item is still there. Would catch the data-loss bug at Game.kt:164.
3. **`Team.isAlive` with all members offline** — construct a team, set members to UUIDs of offline players, assert `isAlive` is true (or whatever the intended contract is). Would catch the high-severity desync at Team.kt:19.
4. **`TeamManager.acceptInvite` rejects a player already in a different team** — pre-populate the player in team A, accept an invite to team B, assert membership is in B and *not* in A. Would catch the dupe-exploit at TeamManager.kt:46.
5. **`Game.eliminate` is a no-op for unknown UUIDs** — call with a fresh UUID, assert `eliminationOrder`, `spectators`, and team state are unchanged. Would catch the state desync at Game.kt:138.
6. **`Game.addPlayer` allocates distinct spawns with eliminated players in the map** — pre-populate 12 players (one eliminated), add a 13th, assert the new player gets spawn index 12, not the center fallback. Would catch Game.kt:106.
7. **`Game.eliminate` concurrent calls** — two `eliminate` calls in parallel (`Dispatchers.Default + runBlocking`) must not throw and must produce the correct order. Would catch the `mutableListOf` race at Game.kt:141.
8. **`Celebration.renderPixelArtHead` rejects oversized/malformed images** — point the configured URL at a 100 MB PNG, assert the function returns null/throws without OOM. Would catch Celebration.kt:80.
9. **`WorldManager.cleanup` after skipped `setup()`** — call `cleanup()` without `setup()`, assert the world border is *not* modified. Would catch WorldManager.kt:33.
10. **`Game.broadcastDeathMessage` uses configured templates** — set a custom `messages.deathNatural`, kill a victim, assert the broadcast contains the custom string. Would catch Game.kt:554.
11. **`TeamQueueManager.accept` removes the player from any prior pre-game team** — accept an invite while already in another pre-game team, assert membership in only one team. Would catch TeamQueueManager.kt:82.
12. **`Game.skipGracePeriod` actually skips the grace period** — would catch the no-op stub at Game.kt:445.
13. **`NameplateManager.stop` restores visibility synchronously** — set up hidden state, call `stop`, assert the player can see other players immediately. Would catch NameplateManager.kt:30.
14. **`Game.removePlayer(Player, false, false)` honours the parameters** — call with restoreState=false, assert the inventory is *not* restored. Would catch the dead-parameter finding at Game.kt:181 (or be deleted if the parameters are removed).
15. **`Game.addDummy` is unreachable from production code paths** — verify the function is `internal` (or guarded), so a non-test command cannot introduce ghosts. Would catch the Game.kt:101 finding.
