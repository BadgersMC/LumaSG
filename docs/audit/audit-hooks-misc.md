# Audit Report: audit-hooks-misc

Batch 6 covers 14 files: `debug/DummyManager.kt`, `discord/DiscordService.kt`, `discord/GameEmbed.kt`, `hooks/{HookManager, LumaGuildsHook, NexoHook, PlaceholderAPIHook, PluginHook}.kt`, `util/cache/{ConcurrentChestFiller, GuiComponentCache, LootTableCache, PlayerDataCache, ScoreboardCache}.kt`, and root `LumaSGPlugin.kt`.

Focus areas per brief: hooks lifecycle, async/threading (Bukkit main-thread rules), secret-leakage surface (Discord token, configs), and cache correctness.

## Summary

| Severity | Count |
|----------|-------|
| critical | 0 |
| high     | 3 |
| medium   | 6 |
| low      | 8 |

The biggest problems are concentrated in `PlayerDataCache.kt` (Bukkit main-thread violations in async Caffeine loaders and a DB-failure path that permanently serves zeroed stats). The hooks themselves are clean; Discord integration is structurally fine but has one startup-blocking call worth knowing about.

No test sources exist anywhere in the repository, so every "test-coverage gap" listed below corresponds to currently-uncovered behavior. The audit calls out only the highest-value missing tests; trivial helpers are omitted.

---

### [SEV: high] util/cache/PlayerDataCache.kt:199 — `loadPermissionFromBukkit` runs Bukkit API on a Caffeine worker thread

**What:** `permissionCache` is a `LoadingCache` whose loader is `::loadPermissionFromBukkit`. `Caffeine.newBuilder()...build(...)` defaults to the common `ForkJoinPool` executor. The loader calls `plugin.server.getPlayer(uuid)` and `player.hasPermission(permission)` — both of which are main-thread-only in Paper. There is no `withContext(bukkitDispatcher)` wrap.

**Why it matters:** First permission check for any `(playerUUID, permNode)` pair will throw `IllegalStateException: ... not on main thread` (or read torn state) on Paper. The exception is swallowed at line 205–207 and the cache stores `false`, so the player is silently treated as lacking the permission. Cache then serves `false` for up to 10 minutes. Worst-case observable bug: a freshly-joined admin is treated as a regular player for ~10 min on their first permission miss.

**Suggested fix:** Wrap the body in `withContext(bukkitDispatcher) { ... }` (injected as a constructor dependency — the `bukkitDispatcher` is already a Nexus bean) and change the cache to `LoadingCache<String, CompletableFuture<Boolean>>`, or precompute the result from a join event and `put` it.

**Confidence:** high

---

### [SEV: high] util/cache/PlayerDataCache.kt:106 — `getCachedDisplayName` reads Bukkit state on `ForkJoinPool.commonPool()`

**What:** `getCachedDisplayName` does `CompletableFuture.supplyAsync({ val player = plugin.server.getPlayer(uuid) ... }, executor)` where `executor` is `ForkJoinPool.commonPool()`. `Bukkit.getPlayer` is main-thread-only.

**Why it matters:** The first time a display name is requested for a UUID not in the cache (e.g., a leaderboard entry), the call violates the Bukkit main-thread contract. On strict Paper builds this throws; on lenient builds it returns null, and the code falls through to `getOfflinePlayer`, silently bypassing the cache. The result is then `put` into the cache, so the wrong/stale name gets pinned for 30 min.

**Suggested fix:** Do `Bukkit.getPlayer` / `getOfflinePlayer` inside `withContext(bukkitDispatcher) { ... }` and complete the future from the coroutine, or move the load to a join event handler that runs on the main thread.

**Confidence:** high

---

### [SEV: high] util/cache/PlayerDataCache.kt:74 — DB failure permanently caches zeroed-out `PlayerStats`

**What:** `getCachedStats` has:
```kotlin
statsCache.get(uuid).exceptionally { throwable ->
    logger.warn("Failed to load stats for UUID: {}", uuid, throwable)
    PlayerStats(uuid = uuid, playerName = "Unknown Player")
}
```
Because `Caffeine.AsyncLoadingCache` calls the loader via `buildAsync { ... nexusScope.future { ... } }`, an exception is converted into a failed future. The `.exceptionally` here returns a *sentinel default* `PlayerStats` — but the AsyncLoadingCache does not store the exceptionally-provided value; it propagates the failure upstream. **However**, callers that chain another `.exceptionally` / `.get()` will see the default. More importantly, on the next access the loader runs again; if the DB is still down, the same `PlayerStats(uuid, "Unknown Player")` (all zeros) is the only thing the placeholder code path produces. A user-visible regression: a player who plays one game, then the DB hiccups, sees `kills=0, deaths=0, wins=0` in placeholders and scoreboards for up to 30 minutes (`expireAfterWrite`) — and stat placeholders that return "0" by design in `PlaceholderAPIHook.kt:31` will then look like a stat reset.

**Why it matters:** Misleading stats for an entire player base during a transient DB outage; if the DB then recovers the same data is re-fetched, but during the window placeholders and Discord announcements broadcast zeros. Worse, the `Unknown Player` name leaks into every downstream display (embed author, leaderboard row) — visible to all players, not just the affected one.

**Suggested fix:** On loader failure, return a `PlayerStats` with a sentinel marker (e.g., `playerName = null`) and have the cache layer treat it as "not loaded" / "stale"; or just let the exception propagate and let the caller decide. Do not put the failure default into a path that writes to log + returns a value that looks like a real player record.

**Confidence:** med (the exact Caffeine behavior for AsyncLoadingCache + `exceptionally` returning a value is subtle; the visible-to-players zero-stats symptom is what matters and is high-confidence).

---

### [SEV: med] util/cache/LootTableCache.kt:103 — `preGenerateLootTables()` is never invoked at startup

**What:** The method is defined and called only from `forceRegeneration` (line 149). There is no call site in `LumaSGPlugin.onEnable`, no `@PostConstruct` call, and no scheduled task that does an initial pre-gen. The `@PostConstruct init()` only schedules the 15-min regeneration, which only fires on buckets that already have data (it only regenerates if `existing.size < PREGENERATED_CHESTS_PER_TIER / 2`).

**Why it matters:** First game of a server boot pays the full on-demand generation cost. The first `getPreGeneratedChest` call for each (tier, mode) pair triggers `generateLootTableForTier` on the calling thread (line 133), blocking game-start. With ~50 chests per bucket × N tiers × 2 modes, this can stall `preGame` for seconds. `ConcurrentChestFiller` is the hot consumer; its `fillChestsForGame` would otherwise have benefited from a warm cache.

**Suggested fix:** Call `preGenerateLootTables()` from `LumaSGPlugin.onEnable` after the arena service is ready, or from a `@PostConstruct` of a service that runs late in the init chain. Or change the 15-min task to always run the first iteration immediately.

**Confidence:** high

---

### [SEV: med] hooks/LumaGuildsHook.kt:40 — HIGHEST-priority `un-cancel` may lose the race against LumaGuilds itself

**What:** The handler runs at `EventPriority.HIGHEST` to set `event.isCancelled = false` after LumaGuilds' `CombatService` has cancelled same-guild damage. Per Bukkit docs, ordering between two handlers at the same priority is *undefined*. If LumaGuilds' own `EntityDamageByEntityEvent` listener also runs at `HIGHEST` (or `HIGH`/`HIGHEST` and the dispatcher orders them differently across server versions), the final cancellation state depends on JVM internal listener ordering — not contractually guaranteed.

**Why it matters:** Edge case where PvP between guild members during Deathmatch silently fails to deal damage — players would think they hit but take no damage, and the survival-game elimination logic never fires for that interaction. No log, no warning.

**Suggested fix:** Either (a) negotiate a softer integration with LumaGuilds (e.g., a public API hook), or (b) on the SG side run the un-cancel at `MONITOR` and rely on the documented guarantee that MONITOR is the last to run for the event, *and* add an explicit assertion that `event.isCancelled` was true at entry to detect the case where LumaGuilds didn't actually cancel.

**Confidence:** med

---

### [SEV: med] discord/DiscordService.kt:32 — `awaitReady()` blocks the server's main thread during `onEnable`

**What:** `JDABuilder.createLight(...).build().awaitReady()` is a blocking call. It runs in the `@PostConstruct` of a service constructed during `LumaSGPlugin.onEnable` (line 27–36), which itself runs on the server's main thread. `awaitReady` waits for the JDA gateway handshake, which can take several seconds on a slow connection and is documented to throw on auth failure only after a timeout.

**Why it matters:** Cold start of a server with a slow/flaky Discord endpoint (or a valid token but unreachable Discord API) can stall the entire server enable for the full JDA timeout (configurable, default ~10s, but can be longer with a `LoginException` causing a retry). Players and other plugins see no progress.

**Suggested fix:** Build JDA without `awaitReady()` (the builder returns immediately), and either use `jda.awaitReady().onSuccess { ... }` (coroutine) or schedule the announce check to defer until `JDA.Status` is `CONNECTED`. The `announce`/`postStats` already guard `jda?.getTextChannelById(...)` against a null JDA, so dropping the `awaitReady` is safe.

**Confidence:** med (depends on whether Nexus @PostConstruct is invoked on the main thread — by the visible code path it is, because `NexusContext.create` is called synchronously from `onEnable`).

---

### [SEV: med] util/cache/PlayerDataCache.kt:54 — Permission cache is stale for up to 10 min after grants/revokes

**What:** `permissionCache` has `expireAfterWrite(10m)` and `expireAfterAccess(5m)`. There is no event-based invalidation when a player's permissions change at runtime (e.g., LuckPerms `PermissionUpdateEvent`, or `/lp user ... permission set`). The only invalidation is `invalidatePlayer(uuid)` on player leave, which doesn't help for an *online* player whose permissions just changed.

**Why it matters:** A moderator who gets `lumasg.moderator` granted mid-session will not see permission-gated commands/placeholders work for up to 10 min. A player whose `lumasg.vip` is revoked will continue to see VIP-only features in the GUI cache for the same window.

**Suggested fix:** Add a `Listener` that hooks LuckPerms `PermissionUpdateEvent` and calls `invalidatePlayer` or `permissionCache.invalidate("$uuid:$node")`. At minimum, expose an `invalidatePermission(uuid, node)` and document that callers must invoke it.

**Confidence:** high

---

### [SEV: med] util/cache/LootTableCache.kt:132 — Double generation on concurrent cache miss

**What:** `getPreGeneratedChest`:
```kotlin
var chests = lootTableCache.getIfPresent(key)
if (chests.isNullOrEmpty()) {
    generateLootTableForTier(tier, mode)
    chests = lootTableCache.getIfPresent(key)
    ...
}
```
Two callers that miss concurrently both run `generateLootTableForTier`. The second call overwrites the first bucket and resets the round-robin `AtomicInteger` to 0 (line 234). The wasted CPU is non-trivial — 50 chests × N items each, all on the calling thread.

**Why it matters:** At server start, when many chests are being filled in parallel (e.g., `ConcurrentChestFiller.fillChestsForGame` fans out per chest), the very first batch will all hit the cold cache and trigger redundant `generateLootTableForTier` calls. If the game-start path is already async, this stacks. Combine with the missing startup pre-gen (see prior finding) and the first game of the day pays this cost N times.

**Suggested fix:** Use `Caffeine.build(key, mappingFunction)` with a `CacheLoader` and Caffeine's `getAll`/`get` will dedupe. Or wrap the generate step in `putIfAbsent` semantics on a separate `ConcurrentHashMap<CacheKey, CompletableFuture<...>>` and `.join()` from the loader.

**Confidence:** med

---

### [SEV: med] util/cache/ConcurrentChestFiller.kt:86 — `chest.location` accessed on possibly off-main thread in error path

**What:** Inside `fillChestFromCache`, the `try` block contains `withContext(bukkitDispatcher) { ... }`. The `catch (e: Exception)` block (line 85–88) runs `logger.error("Error filling chest at {} ...", chest.location, tier, e)` on the *calling* coroutine context, not the main thread. `chest.location` (`BlockState.getLocation()`) is main-thread-only on Paper. The same pattern exists in `fillChestSync` (line 109) — there the method is documented as main-thread-only so it is only a concern if a future caller violates the contract.

**Why it matters:** A failure during chest fill will throw a second exception from the error log itself on strict Paper builds, masking the original failure and producing unhelpful stack traces.

**Suggested fix:** Move the log line inside the `withContext(bukkitDispatcher)` block (capture `chest.location` on the main thread), or use `Location.toString()` outside (which itself calls into Bukkit — same problem). Cleanest: log just the tier and chest coordinates captured before the failing call.

**Confidence:** med

---

### [SEV: low] debug/DummyManager.kt:82 — Linear scan over `getAllActiveGames()` on every dummy death

**What:** `gameManager.getAllActiveGames().firstOrNull { it.id == gameId }` is O(n) per dummy death. If multiple solo games are running in parallel (e.g., admins stress-testing), each dummy death scans the full list.

**Why it matters:** Debug-only code path, but EventHandler listeners run on the main thread; a hot path that scales O(N) with active games on the main thread is a stutter risk during large test setups.

**Suggested fix:** Add a `Map<UUID, Game>` lookup on `GameManager` (one already exists at `activeGames`), or have `DummyManager` cache `villager.uniqueId → game` directly (which it does on line 70) and dereference the Game from there instead of going through `gameManager`.

**Confidence:** high

---

### [SEV: low] debug/DummyManager.kt:55 — Comment doesn't match behavior of `drop(game.players.size)`

**What:** Comment says "Skip index 0 so the real player keeps their spawn" but the code skips `game.players.size` entries. It works only because the calling `debugDummies` command (SGCommand.kt:532–534) calls `addPlayer` before invoking `spawnDummies`, leaving `players.size == 1` at that point. If the call order ever changes, dummies collide with the real player's spawn.

**Why it matters:** Latent bug. Today the assumption holds, but a future refactor of the command (e.g., spawning dummies into a not-yet-started game with multiple real players already queued) would silently misalign spawns.

**Suggested fix:** Either fix the code to match the comment (`drop(1)`) or fix the comment and add a precondition check that `players.isNotEmpty() && players.values.first()` is the real player. Alternatively, pass the player's spawn index explicitly.

**Confidence:** med

---

### [SEV: low] debug/DummyManager.kt:86 — Dummy-on-dummy kill still broadcasts "eliminated by Dummy_X"

**What:** `entity.killer` may be another villager (a dummy). The branch at line 95–101 will then broadcast `"<Dummy_A> was eliminated by <Dummy_B>!"`. The kill-counter increment at line 88 is a no-op (the killer UUID isn't a `GamePlayer`), so the broadcast is the only observable artifact.

**Why it matters:** Cosmetic. Debug only.

**Suggested fix:** Gate the broadcast on `killer is Player`, or treat villager-on-villager kills as a no-op for broadcast purposes.

**Confidence:** high

---

### [SEV: low] hooks/PlaceholderAPIHook.kt:48 — `PlaceholderExpansion` is never unregistered

**What:** `.register()` is called in `@PostConstruct`, but the class has no `@PreDestroy` to call `PlaceholderExpansion.unregister()`. On `/plugman reload` or a hot-reload, the expansion is still registered in PlaceholderAPI but bound to the previous classloader. The next reload piles up.

**Why it matters:** Classloader leak / stale-expansion after a reload. Production servers typically don't reload, so impact is low.

**Suggested fix:** Hold the expansion reference and call `unregister()` from a `@PreDestroy`.

**Confidence:** high

---

### [SEV: low] hooks/LumaGuildsHook.kt:30 — Event listener is never unregistered

**What:** `registerEvents(this, plugin)` is called in `@PostConstruct`, but no corresponding `unregisterEvents(this)` in `@PreDestroy`. A plugin reload leaks the listener registration.

**Why it matters:** Same pattern as PlaceholderAPIHook. Mostly impacts reload workflows.

**Suggested fix:** Add a `@PreDestroy` that calls `HandlerList.unregisterAll(this)`.

**Confidence:** high

---

### [SEV: low] discord/DiscordService.kt:49 — `try/catch` around `queue()` doesn't catch the failure path

**What:**
```kotlin
nexusScope.launch {
    try {
        channel.sendMessageEmbeds(embed).queue()
    } catch (e: Exception) {
        logger.warn("Failed to send Discord announcement: ${e.message}")
    }
}
```
JDA's `queue()` returns immediately and dispatches to its own thread pool; failures are reported through the success/failure callbacks (`queue(Consumer, Consumer)`), not via thrown exceptions from the call site. The `try/catch` only catches the rare synchronous failure (e.g., channel null after a re-fetch).

**Why it matters:** Misleading error handling: a real Discord send failure (rate-limit, missing perms, gateway down) is logged silently by JDA and not visible in the LumaSG log. Operators see a working integration that actually isn't delivering.

**Suggested fix:** Use the overload `queue(Consumer<RestAction<T>> onSuccess, Consumer<Throwable> onFailure)` and route `onFailure` to `logger.warn("Failed to send ...", it)`.

**Confidence:** med

---

### [SEV: low] LumaSGPlugin.kt:65 — `runBlocking { ... saveAll() }` on the shutdown thread

**What:** `onDisable` blocks the shutdown thread on the arena persistence save. If `ArenaService.saveAll` writes many arenas or talks to a slow JDBC store, the server appears hung during shutdown.

**Why it matters:** Operator-facing delay. Not a correctness bug, but the shutdown-time blocking pattern is worth knowing.

**Suggested fix:** Use the plugin's existing coroutine scope with a bounded timeout, or schedule `saveAll` at the end of each game so `onDisable` only flushes the in-memory delta.

**Confidence:** low

---

### [SEV: low] util/cache/LootTableCache.kt:251 — No defense against misconfigured `minAmount`/`maxAmount`

**What:**
```kotlin
stack.amount = if (selected.maxAmount > selected.minAmount) {
    selected.minAmount + random.nextInt(selected.maxAmount - selected.minAmount + 1)
} else {
    selected.minAmount
}
```
If `minAmount` is `0` or negative, the fall-through path produces an empty or invalid stack. If `minAmount > maxAmount` (config typo), the fall-through is taken, but the value is still `minAmount` (not what the operator wrote in `maxAmount`).

**Why it matters:** A single misconfigured item in `chest-items.yml` produces a silent silent-fail (empty stack in chest) that no log line points to. Operator sees a chest that "looks right" but contains a no-op slot.

**Suggested fix:** Clamp `stack.amount` to `[1, maxStackSize]`, log a warning when the input is out of range, and treat `minAmount < 1` as a config error.

**Confidence:** med

---

### [SEV: low] util/cache/ScoreboardCache.kt:120 — Fuzzy `startsWith` key match on player UUIDs

**What:** `invalidatePlayer` does `scoreboardLineCache.asMap().keys.removeIf { it.startsWith(playerPrefix) }` and the same for `placeholderCache`. The prefix is a full UUID string, but the line/placeholder caches have keys like `"$gameId:$lineType:$value"` and arbitrary user-supplied strings, so collision is unlikely. Still, `startsWith` on a fixed-length prefix is correct in practice only because the `:` separator in those cache keys prevents ambiguity.

**Why it matters:** If a future caller adds a cache key that *legitimately* starts with a UUID string (e.g., `"$uuid:nickname"`), `invalidatePlayer` will wipe it even though the entry is global rather than per-player.

**Suggested fix:** Require the separator in the predicate: `it.startsWith("$playerPrefix:")` or wrap a structured key in a data class.

**Confidence:** low

---

### [SEV: low] util/cache/GuiComponentCache.kt:181 — `createGuiItem` parses up to 3 colon-segments but only uses first 2

**What:** `parts.split(":", limit = 3)` produces up to 3 parts, then `parts[0]` and `parts[1]` are consumed. The third part is silently dropped. Keys like `"button:back:12345"` work because `parts[1] = "back"` and the loader doesn't care about `parts[2]`.

**Why it matters:** Cache key design is incoherent. The hashed display name (line 99) and stat-value hash (line 115) influence cache *lookup* but not the constructed item. This is fine semantically (the value is the same) but means the cache is *less effective* than the keys suggest: a `SimpleItem` is built from `buttonType` only, so every call with the same `buttonType` should share a cache entry but instead gets a separate one per `(buttonType, displayName)` pair.

**Suggested fix:** Make the cache key match the value: drop the hash, use `"button:$buttonType"`. Then the supplier fast-path (line 102) and the loader fast-path (line 104) hit the same entry.

**Confidence:** med

---

## Test-coverage gaps (project has no test sources at all)

The repository has zero test files. The following behaviors are particularly worth covering before the affected code is refactored again:

- **util/cache/LootTableCache.kt:264** — `weightedRandomItem` weight math, including the `totalWeight <= 0` fallback branch. The deterministic test would feed a known `(tier, mode)` and assert distribution.
- **util/cache/PlayerDataCache.kt:75** — the DB-failure path in `getCachedStats`. A mock repository that throws on `findByUuid` should verify what the *caller* receives (not what the loader returns).
- **hooks/LumaGuildsHook.kt:40** — the un-cancel logic for two players in the same game during `Active`/`Deathmatch` versus `Grace`/`Waiting`, and the same-team carve-out.
- **util/cache/ConcurrentChestFiller.kt:99** — the `fillChestSync` synchronous path that was added specifically to close the "player opens empty chest" race. There should be a test that pre-fills the cache, calls `fillChestSync` from a non-coroutine context, and asserts the chest is populated *before* the call returns.
- **util/cache/ScoreboardCache.kt:64** — `PlayerScoreboardData.needsRefresh(interval)` returns true on the very first call regardless of `lastUpdate = 0L`. The contract should be specified and pinned.
- **discord/GameEmbed.kt** — trivial; not worth a test.

---

## Files with no findings

- `hooks/HookManager.kt` — straight delegation; correctness depends on `getGameForPlayer` and `isPvpEnabled` (in `game/` package, not in this batch).
- `hooks/PluginHook.kt` — single-line default; fine.
- `hooks/NexoHook.kt` — defensive `try/catch` around `NexoItems.itemFromId(id)?.build()`. `id` is supplied by trusted internal callers (config), no NBT injection surface here.
- `discord/GameEmbed.kt` — pure data builder, no findings.
- `debug/DummyManager.kt` — only low-severity findings (see above).
- `util/cache/GuiComponentCache.kt` — one low finding (incoherent cache key); no correctness bug.

---

## Notes for the next pass

- `DummyManager.spawnDummies` and the dummy-villager `eliminate` flow both need to live with the `Game.addDummy` shape. If `GamePlayer` ever gains a `Player` reference (it currently does not — confirmed at `GamePlayer.kt`), dummies would need to be excluded. Worth a comment in `Game.kt:101`.
- The Discord token (`config.discord.botToken`) is not logged anywhere; the only place it could leak is via a JDA exception message. JDA's `LoginException` / `InvalidTokenException` messages do not embed the token, so this batch is clean on secret-leakage. If a future change logs `JDAException` objects directly, that would re-open the issue.
- The hooks are correctly idempotent on the not-present path (`isAvailable()` short-circuit before any class loading). No `ClassNotFoundException` risk.
