# Audit Report: audit-persistence

Batched audit of persistence/, statistics/, config/, and service/ArenaService — focused on
SQL injection, transactional correctness, resource leaks, thread-safety, and game-integrity
of stats / arena state.

---

## Findings

### [SEV: high] persistence/repositories/PlayerStatsRepository.kt:64-65 — K/D and Win-Rate leaderboards are not actually computed
**What:** `StatType.KILL_DEATH_RATIO` and `StatType.WIN_RATE` both fall through to ordering by
the raw `kills` (or `wins`) column rather than computing the ratio. The enum in
`domain/StatType.kt` explicitly documents the intent — `KILL_DEATH_RATIO` comment says
`"computed from kills/deaths"`, `WIN_RATE` says `"computed from wins/gamesPlayed"`.
**Why it matters:** Game-integrity exploit. A player with 100 kills / 100 deaths ranks above
a player with 50 kills / 1 death on the K/D leaderboard. A player with 1 win / 1 game
ranks above a player with 50 wins / 200 games on the Win-Rate leaderboard. Players can
farm stats (or play only one short game) to dominate public leaderboards — directly
breaks the gamified ranking feature the plugin advertises in `messages.killNotification`
and `winnerAnnouncement`.
**Suggested fix:** Compute the ratio in SQL (e.g. `CAST(kills AS DOUBLE) / NULLIF(deaths, 0)`
and `CAST(wins AS DOUBLE) / NULLIF(games_played, 0)`) using a custom `Expression<*>`,
and use `orderBy` on that. Alternatively, sort in Kotlin after fetching a bounded
candidate set. Ensure the test for `KILL_DEATH_RATIO` actually asserts the ratio (see
"test-coverage gap" below).
**Confidence:** high

---

### [SEV: high] persistence/repositories/PlayerStatsRepository.kt:113 — `UUID.fromString` on stored column can crash the query
**What:** `toPlayerStats()` does `UUID.fromString(this[PlayerStatsTable.uuid])` with no
try/catch. A single corrupt UUID in any row causes the entire `findByUuid`,
`getLeaderboard`, or `getTotalPlayerCount` call to throw `IllegalArgumentException`,
which propagates out of the coroutine as an unhandled exception.
**Why it matters:** Data-loss / data-corruption adjacent. One bad row (manual DB edit,
partial migration, in-flight change) bricks every stats read path. The `ArenaRepository`
correctly wraps `UUID.fromString` in `runCatching` (line 62) — this one was missed.
**Suggested fix:** Wrap in `runCatching { UUID.fromString(this[PlayerStatsTable.uuid]) }
.getOrElse { UUID(0L, 0L) }` and log a warning. (ArenaRepository's approach of
substituting a fresh random UUID is also questionable — it would silently persist a
different UUID on next upsert and break referential integrity — but at least it doesn't
crash.)
**Confidence:** high

---

### [SEV: high] persistence/DatabaseService.kt:91-96 — Primary-key detection is case-sensitive string match
**What:** The legacy-table detector compares `rs.getString("COLUMN_NAME")` literally to
`"name"`. If the primary key column is stored as `"NAME"`, `"Name"`, or `"NAME "`
(any case or whitespace variant — common after hand-edits or third-party migration
tools), the check returns `false` and the migration runs.
**Why it matters:** The migration then `DROP TABLE arenas` and recreates from
`arenas_new`. Even if data is preserved via the `INSERT … SELECT`, the rebuild is
unnecessary work on a hot startup path. Worse, if the rebuild hits an error mid-flight
(no `try/finally` around the `PRAGMA foreign_keys = OFF` at line 100), the `arenas`
table is left in a half-migrated state and `PRAGMA foreign_keys` may stay `OFF` on
the pooled connection — silently disabling FK enforcement for the rest of the session.
**Suggested fix:** Compare case-insensitively and trim: `it.equals("name", ignoreCase = true)`.
Wrap the `PRAGMA foreign_keys = OFF` / rebuild / `PRAGMA foreign_keys = ON` in
`try { … } finally { stmt.executeUpdate("PRAGMA foreign_keys = ON") }` so a rebuild
exception cannot leave FK enforcement disabled on the pooled connection.
**Confidence:** med

---

### [SEV: high] persistence/CacheManager.kt:30-35 — `getOrPut` is not thread-safe (cache stampede / duplicate compute)
**What:** `getOrPut` does `get(key)?.let { return it }` then `compute()` then `put(key, value)`.
Two threads hitting a missing key simultaneously will both miss, both run `compute()`,
and the loser's value silently overwrites the winner's. This is the classic
double-checked-locking bug — `ConcurrentHashMap` does not help because the check and
write are two separate operations.
**Why it matters:** For a stats cache where `compute()` is a DB lookup, a cold key
under a burst (e.g. several players join simultaneously) can trigger N parallel DB
queries for the same data. For a computation that has side effects (e.g. inserts a new
`PlayerStats` row, as `StatisticsService.getOrCreate` does at line 30), this can create
duplicate rows.
**Suggested fix:** Use the atomic `store.computeIfAbsent(key) { CacheEntry(compute(), Instant.now().plus(ttl)) }`,
or hoist the cache write inside the same `compute()` lambda. Then expose a way to
return the cached value without re-running `compute()` on the hit path
(`computeIfAbsent` returns the existing-or-new value directly).
**Confidence:** high

---

### [SEV: high] statistics/StatisticsService.kt:23-33 — `getOrCreate` has the same stampede + write-on-read bug as CacheManager
**What:** `getOrCreate` does `cache[key]?.let { return it }` then a `statsRepo.findByUuid`
then `PlayerStats(...).also { statsRepo.upsert(it) }` then `cache[key] = stats`. Two
threads calling concurrently with a missing key will both miss the cache, both query
the DB, and both upsert a freshly-minted `PlayerStats` row — creating a duplicate
`(uuid, lootMode)` row that violates the primary key constraint at
`PlayerStatsTable` line 27.
**Why it matters:** Game-integrity. Duplicate rows would either be silently dropped by
the upsert (losing the original's `playerName` and `createdAt` on race) or — if the
PK enforcement path is loose — end up as two rows that the leaderboard then sums.
Also see `preloadPlayerStats` (line 40) which calls this for all three loot modes on
every player join, multiplying the race window.
**Suggested fix:** Wrap the find-or-create in a per-key mutex (e.g. `ConcurrentHashMap<CacheKey, Mutex>`),
or use the DB itself as the source of truth with `INSERT … ON CONFLICT DO NOTHING` and
read back. At minimum, guard the `upsert` so it only fires when `findByUuid` actually
returned `null` (not just as part of an `also` block that runs even on the hit path
when the cache happens to be empty).
**Confidence:** high

---

### [SEV: med] persistence/repositories/ArenaRepository.kt:62 — Silently substituting `UUID.randomUUID()` on parse failure
**What:** `id = runCatching { UUID.fromString(this[ArenaTable.id]) }.getOrDefault(UUID.randomUUID())`.
If the stored `id` column is corrupt or empty, the loader synthesises a brand-new
random UUID, then `ArenaRepository.save` (line 36) writes that fresh UUID back to the
row.
**Why it matters:** The `id` column is not the primary key (the PK is `name`), but it
is still a stable identifier used elsewhere in the plugin. Replacing it on load + save
silently rotates the arena's id. Any external system or future migration that joins
on `id` will lose track of the arena. Worse, on first load after corruption the cache
holds the random id; on save the row is updated; on next load the same logic produces
a *different* random id and overwrites again — only the first one is recoverable.
**Suggested fix:** Use a sentinel `UUID(0L, 0L)` and log a `logger.warn(...)` so the
operator knows the row is bad and can repair it. Do not auto-rotate.
**Confidence:** high

---

### [SEV: med] persistence/repositories/ArenaRepository.kt:69-91 — `runCatching { gson.fromJson(...) }.getOrDefault(...)` silently swallows every JSON / material parse error
**What:** Four separate `runCatching` blocks silently coerce failure to
`emptyList()` / `getOrNull()` / `Arena.DEFAULT_ALLOWED_BLOCKS`. There is no logging.
**Why it matters:** Game-integrity. A bad row in `spawn_points_json` or
`chest_locations_json` (e.g. truncated, schema drift after a Gson upgrade) causes the
arena to load with **zero spawn points and zero chest locations**. The game will then
place players at the literal world spawn (or 0,0,0) and no chest loot will ever fire —
a complete silent feature break with no operator signal. The `allowedBlocks` fallback
to `DEFAULT_ALLOWED_BLOCKS` is a different design choice but again without logging the
operator never knows their saved `allowedBlocks` was rejected.
**Suggested fix:** On each catch, log at WARN level with the arena name and the
malformed JSON. For the spawn-points / chest-locations case, return an explicit "broken
arena" sentinel (or refuse to load the arena entirely) rather than loading a half-empty
arena. Consider a strict Gson type-adapter that fails loudly.
**Confidence:** high

---

### [SEV: med] service/ArenaService.kt:26, 44-47, 57-61 — `loadAll`, `save`, `removeArena` have no transactional cache/DB consistency
**What:**
- `loadAll()` (`@PostConstruct` on a `suspend fun` — see line 26) loads arenas from
  the DB into the in-memory `cache`. The cache is then used as the source of truth.
- `save(arena)` writes to the DB *then* updates the cache. If the DB write throws,
  the new in-memory value still goes into the cache on line 46? No — re-reading, the
  call is `arenaRepo.save(arena); cache[…] = arena`, so the cache is only updated
  after a successful save. But if `saveAll()` (line 49-51) iterates `cache.values`
  and the DB throws partway through, the cache remains "saved" while the DB is
  partially written — no rollback is possible.
- `removeArena(arena)` removes from cache first (line 58) then deletes from DB
  (line 59). If the DB delete throws, the cache is now empty but the row remains —
  on next restart the arena reappears.

**Why it matters:** Data-loss in normal use. Server crash or DB hiccup mid-`saveAll`
leaves a half-persisted arena set; on reload, arenas that were "saved" in cache but
not yet flushed are lost (because the cache is cleared in `stop()` at line 33 before
the DB shutdown). The `removeArena` ordering is also surprising: it is a *cache-first*
delete, so a DB failure causes the row to survive a restart that the operator believed
already removed it.
**Suggested fix:** Treat the DB as source of truth. `save` → DB first, then refresh
cache from the saved row (or do `arenaRepo.save`; `cache[…] = arenaRepo.findByName(...)`
to be sure the cached value is the one actually persisted). `removeArena` → DB first,
then `cache.remove` only on success. For `saveAll`, use a single transaction
(`dbQuery { for (...) upsert {} }`) so a failure rolls back atomically.
**Confidence:** med

---

### [SEV: med] service/ArenaService.kt:23, 79-88 — `selectedArenas` map is never evicted on player quit
**What:** `selectedArenas: ConcurrentHashMap<UUID, String>` stores the player's currently
admin-selected arena. `clearSelectedArena` (line 86) exists but is not wired up to a
`PlayerQuitEvent` listener. `stop()` (line 32-35) clears the map only on plugin
shutdown.
**Why it matters:** Slow memory leak. Each time an admin selects an arena and logs
out, the entry stays in the map forever. For a long-running server with rotating
admin staff this grows without bound. (There is a separate `selectedArenas` in
`AdminWandListener.kt:38` that *is* cleaned up on quit — that one is the actual
production path; the unused one in `ArenaService` looks like dead/draft code. Either
way it is misleading.)
**Suggested fix:** Either wire `clearSelectedArena` to a `PlayerQuitEvent`, or
delete the unused `selectedArenas` / `setSelectedArena` / `getSelectedArena` /
`clearSelectedArena` from `ArenaService` if `AdminWandListener`'s copy is the real
one.
**Confidence:** med

---

### [SEV: med] statistics/StatisticsService.kt:53-80 — Every per-kill / per-damage event is two DB round-trips
**What:** `recordKill`, `recordDeath`, `recordDamageDealt`, `recordDamageTaken`, and
`recordChestOpened` each do `statsRepo.findByUuid(...)` followed by `statsRepo.upsert(...)`.
`recordDamageDealt` and `recordDamageTaken` are never called from the production
code path (the only call sites are in `Game.kt:233-243` which only mutate the
in-memory `GamePlayer`); they appear to be dead code. `recordKill` and `recordDeath`
*are* called from `PlayerListener.kt:120` on death.
**Why it matters:** Resource leak / performance under load. A 24-player game with
several kills per second creates 2N+2 DB queries per kill (2 for the killer, 2 for
the victim). On a small Hikari pool (`maximumPoolSize = 1` for SQLite per
`DatabaseService.kt:43`, default 8 for MySQL) this can starve the pool and back up
the coroutine dispatcher. SQLite is hit *twice as hard* because every write
serialises through the single-writer connection.
**Suggested fix:** Batch. The `Game` already accumulates the per-player kill / damage
totals in `GamePlayer` (see `Game.kt:233-243`). `recordGameEnd` already iterates
`game.players.values` and writes once. Delete the per-event `recordKill` /
`recordDeath` from `StatisticsService` and let `recordGameEnd` do the work, or
introduce a periodic flush (every N seconds / events) with `saveAllPendingStats` —
the batching infrastructure is already there.
**Confidence:** med

---

### [SEV: med] statistics/StatisticsService.kt:86 — `gameTime` is computed and never used
**What:** `val gameTime = game.getTimeRemaining().toLong()` on line 86 is assigned but
never referenced afterwards. The variable is dead.
**Why it matters:** Style nit with a real consequence: a future maintainer reading the
function will assume `gameTime` is being persisted (and look for the bug in the
DB-write path) when actually the function silently never records total game duration
in `totalTimePlayed`. `PlayerStats.totalTimePlayed` is incremented *only* via
`getAggregatedStats` (line 165), never on a per-game basis.
**Suggested fix:** Either remove the dead variable, or actually compute
`game.startedAt → now` and add it to `stats.totalTimePlayed` in the `recordGameEnd`
upsert.
**Confidence:** high

---

### [SEV: med] statistics/StatisticsService.kt:97 — `placement` defaults to `totalPlayers` for unplaced players
**What:** `val placement = gp.placement.takeIf { it > 0 } ?: totalPlayers`. If
`GamePlayer.placement` is left at its default `0` (which happens for any player who
disconnects before the game finalises their placement), the function attributes to
them a placement equal to the number of *participants*, not the number of *finishers*.
**Why it matters:** Game-integrity. A player who rage-quits at 10 players alive
silently gets recorded as "10th place" and increments their `top3Finishes` only if
`placement <= 3` (so they don't get an undeserved top-3, OK) — but their
`bestPlacement` is overwritten with `10`, which is *worse* than the truth (they
should retain their previous best, not get a permanent "10th place" stamp). For a
player who previously had a 1st-place best, a ragequit can lock their `bestPlacement`
at the player count of whatever game they quit.
**Suggested fix:** Do not overwrite `bestPlacement` when the game's placement is
indeterminate. Either skip the placement update for `gp.placement == 0`, or record
`Int.MAX_VALUE` and use a `min` that ignores the sentinel. (Also note the
double-condition at line 110: `stats.bestPlacement == 0 || placement < stats.bestPlacement`
— the `== 0` short-circuit means the first game with a real placement always wins,
which is fine, but the `placement = totalPlayers` default makes this worse.)
**Confidence:** med

---

### [SEV: med] statistics/StatisticsService.kt:148-172 — `getAggregatedStats` returns a `PlayerStats` with a fake `lootMode`
**What:** The aggregated result is constructed with `lootMode = LootMode.MODERN` and a
comment `"placeholder — aggregate has no single mode"`. The shape is a regular
`PlayerStats`, so any caller that doesn't know it's aggregated and passes it back to
`statsRepo.upsert` (e.g. via `savePlayerStats`) will silently write garbage to the
MODERN row for that player — overwriting real MODERN stats with a sum that includes
CLASSIC and OP.
**Why it matters:** Data corruption footgun. Not currently triggered by production
callers, but a refactor that "simplifies" the stats layer to pass the aggregated
value through the existing save path will wipe a player's MODERN row. The
`PlayerStats(uuid, playerName, lootMode = …)` constructor itself doesn't reject
this misuse.
**Suggested fix:** Make `getAggregatedStats` return a dedicated `AggregatedPlayerStats`
type (or a `Map<LootMode, PlayerStats>`). Or, if the current shape must stay, use a
sentinel `lootMode` enum value like `LootMode.AGGREGATE` that `PlayerStatsTable` will
reject (it has no row for `AGGREGATE`, so the upsert would fail loudly rather than
silently overwriting).
**Confidence:** med

---

### [SEV: med] statistics/StatisticsService.kt:166 — `bestPlacement` aggregation reads `Int.MAX_VALUE` then unwraps via `takeIf`
**What:**
```kotlin
bestPlacement = allModeStats.minOfOrNull { it.bestPlacement.takeIf { p -> p > 0 } ?: Int.MAX_VALUE }
    ?.takeIf { it != Int.MAX_VALUE } ?: 0
```
Works, but is a three-deep null-and-sentinel dance. A single-mode player with
`bestPlacement = 0` correctly gets `0` out, but the path is fragile — a future
maintainer who removes one `takeIf` will silently make every player look like they
have "1st place best" because the min of `{0, 0, 0}` after `takeIf { > 0 }` returns
empty and `minOfOrNull` on an empty sequence returns `null` → the `?:` returns `0`.
That part is correct, but the readability cost is real.
**Why it matters:** Style / maintainability with a latent correctness trap.
**Suggested fix:** Extract a helper:
```kotlin
fun bestPlacementAcross(modes: List<PlayerStats>): Int =
    modes.mapNotNull { it.bestPlacement.takeIf { p -> p > 0 } }.minOrNull() ?: 0
```
**Confidence:** low (style-leaning)

---

### [SEV: med] persistence/DatabaseService.kt:99-139 — `migrateSqliteLegacyArenasTable` has no `try/finally` around `PRAGMA foreign_keys`
**What:** Lines 100 and 138 set `PRAGMA foreign_keys = OFF` and back to `ON`. There is
no `try/finally`. If any of the `executeUpdate` calls between them throws (e.g.
`CREATE TABLE arenas_new` fails due to disk full, `DROP TABLE arenas` fails because
another connection holds it, etc.), the connection returns to the Hikari pool with
`foreign_keys = OFF`. SQLite enforces `PRAGMA foreign_keys` per-connection, so the
*next* user of that pooled connection will have FK enforcement disabled.
**Why it matters:** Data-integrity. The `game_history` table has
`arenaName references ArenaTable.name` (`tables/GameHistoryTable.kt:8`). With FK
enforcement off, an admin deleting an arena no longer cascades (or rather, no longer
blocks) the orphan `game_history` rows. The orphaned rows would survive in the DB
and re-appear in any future migration that adds a real cascade.
**Suggested fix:**
```kotlin
try {
    stmt.executeUpdate("PRAGMA foreign_keys = OFF")
    // … rebuild …
} finally {
    runCatching { stmt.executeUpdate("PRAGMA foreign_keys = ON") }
}
```
Also consider doing the rebuild in a single `transaction { … }` block so SQLite
itself rolls back on failure.
**Confidence:** med

---

### [SEV: med] persistence/DatabaseService.kt:65-75 — Migration runs `transaction { SchemaUtils.createMissingTablesAndColumns(...) }` but legacy migration runs *before* it
**What:** `init()` calls `migrateSqliteLegacyArenasTable()` at line 66, then
`transaction { SchemaUtils.createMissingTablesAndColumns(...) }` at line 69-75.
`createMissingTablesAndColumns` may also try to `ALTER TABLE` to add the primary key
to `arenas` on a legacy schema — which `SQLite` cannot do. If the legacy migration
above fails (e.g. because the table doesn't exist yet, which it returns-early on, but
also because of the `PRAGMA foreign_keys` bug above), the `createMissingTablesAndColumns`
will either error out or silently fail to add the PK.
**Why it matters:** Data-integrity on first startup. There is no test for this path
(see "test-coverage gap" below). The order is correct *in principle* (legacy first,
then add-columns), but the two paths are not coordinated.
**Suggested fix:** Run both inside the same `transaction` block so a failure in
either rolls both back. Add a test that starts from a pre-migration schema (table
without PK) and asserts the final schema is correct.
**Confidence:** low

---

### [SEV: low] config/LumaSGConfig.kt:333 — Discord bot token stored as plaintext `String` in config
**What:** `var botToken: String = ""` in `DiscordConfig`. There is no encryption, no
environment-variable indirection, no permission check on the file.
**Why it matters:** Secret leakage. If the operator commits the populated config to
git, or shares a debug bundle that includes the plugin data folder, the bot token
leaks. The comment says "keep secure, never share" but the config-loading code does
nothing to enforce that.
**Suggested fix:** Either (a) read the token from an environment variable at startup
with the config value as a fallback / path to a separate file, or (b) at minimum,
log a one-time warning at startup if `botToken.isNotBlank()` that reminds the
operator to keep the file out of version control, and exclude `config.yml` from
the build artifact.
**Confidence:** low (the bot token is only used in one place, `DiscordService.kt:30`,
and the default is empty — leakage requires operator action)

---

### [SEV: low] persistence/repositories/PlayerStatsRepository.kt:81-83 — `getTotalPlayerCount` counts (uuid, mode) pairs, not unique players
**What:** `PlayerStatsTable.selectAll().count()` returns the row count, but the PK is
`(uuid, lootMode)` (line 27 of the table). One player who has played all three loot
modes contributes three rows.
**Why it matters:** Likely-surprise bug. Whatever consumes this count (e.g. a Discord
stat embed, a server-info command) will report a player count 3× the actual
population.
**Suggested fix:** `PlayerStatsTable.uuid.countDistinct()` (Exposed: `PlayerStatsTable.uuid.countDistinct()`).
**Confidence:** med

---

### [SEV: low] persistence/repositories/PlayerStatsRepository.kt:85-110 — `savePlayerStatsBatch` issues N separate upserts
**What:** The batch is implemented as a `for (stats in statsList) { … upsert … }`
inside one `dbQuery`, which is one transaction. The `upsert` DSL call is still N
round-trips to the DB.
**Why it matters:** Performance under load. For a server with 100+ players this is
noticeable, especially over a network DB. A single `INSERT … ON CONFLICT … DO UPDATE`
batch would be a fraction of the cost.
**Suggested fix:** Use Exposed's `batchInsert` with an `onUpdate` / `onConflict`
clause, or build a single multi-row upsert.
**Confidence:** med

---

### [SEV: low] persistence/CacheManager.kt:23-24, 39, 45 — TTL eviction is best-effort; size cap never enforced
**What:** `put` calls `evictExpired()` only when `store.size >= maxSize`. If the
caller `getOrPut`s with a key that hasn't expired and the size is at `maxSize`, no
eviction is triggered — but a new key is still inserted, pushing the size past
`maxSize` by 1 (and then the eviction runs, removing the *new* entry's
contemporaries, but the new entry itself remains). There is no actual cap.
**Why it matters:** The `maxSize` parameter is documented in the class comment as
"max size" but it is a soft target, not a hard cap. Indefinite growth is possible
if the workload has many short-lived keys that arrive faster than they expire.
**Suggested fix:** Enforce: if `store.size >= maxSize` after `evictExpired()` and
size is still at the cap, refuse the insert (or evict the oldest). Use
`store.compute` with a size check.
**Confidence:** low

---

### [SEV: low] persistence/DatabaseExtensions.kt:7-9 — `dbQuery` uses `Dispatchers.IO` which is unbounded
**What:** `Dispatchers.IO` has a default max of 64 threads, can grow with
`systemProp`. Every `dbQuery { }` spawns (or reuses) an IO thread. There is no
back-pressure: a burst of N caller coroutines that all `dbQuery` simultaneously
can occupy N IO threads, blocking the coroutine dispatcher used elsewhere.
**Why it matters:** Performance under load, especially on a server with many
players all calling stats reads / writes at the same time. Not a correctness bug.
**Suggested fix:** Consider a bounded `Executors.newFixedThreadPool(db.pool.maximumPoolSize * 2)`
and `withContext(pool.asCoroutineDispatcher())`, or set `Dispatchers.IO.limitedParallelism(N)`.
**Confidence:** low

---

### [SEV: low] statistics/StatisticsService.kt:40-45 — `preloadPlayerStats` writes a stats row for every player on join
**What:** `preloadPlayerStats` calls `getOrCreate` for *all three* `LootMode.entries`
on every player join. `getOrCreate` upserts a freshly-minted `PlayerStats` if none
exists for that `(uuid, mode)`. A player who joins, looks at the lobby for 10
seconds, and leaves now has 3 rows in `player_stats`.
**Why it matters:** Resource leak / data hygiene. Over a long-running server the
table accumulates rows for every player who ever connected, even if they never
played. (Compare to the cache-hit path in `getOrCreate`: the *cache* is correctly
populated without a DB write, but the very first call for a mode where the row is
missing still triggers an upsert.)
**Suggested fix:** Distinguish "load or return existing" from "load or create". The
latter should not be called from `preloadPlayerStats`; only the former. A player
who hasn't played a mode yet should simply have no row until they play it.
**Confidence:** med

---

### [SEV: low] config/LumaSGConfig.kt:296-314 — `DatabaseConfig.type` is a free-form String, not an enum
**What:** `var type: String = "SQLITE"` — the value is matched in
`DatabaseService.kt:38` via `db.type.uppercase()` and falls into an `else -> error(...)`
branch for unknown values. The error is thrown at startup (fine) but the config
itself does not constrain the value, so a typo (`"SQLLITE"`) fails late and
cryptically.
**Why it matters:** Operator experience. Also means the IDE / config-validation layer
can't suggest valid values.
**Suggested fix:** `var type: DatabaseType = DatabaseType.SQLITE` where
`DatabaseType` is an enum with `MYSQL`, `MARIADB`, `POSTGRESQL`, `SQLITE`. Default
to a value that gives a friendly error if the type is unknown.
**Confidence:** low

---

### [SEV: low] config/LumaSGConfig.kt:298, 220 — API URLs and SQLite file path are concatenable, no validation
**What:**
- `var sqliteFile: String = "lumasg.db"` is resolved via `plugin.dataFolder.resolve(db.sqliteFile)`
  and then put straight into a JDBC URL: `jdbc:sqlite:${dbFile.absolutePath}`. A
  malicious or misconfigured value containing `?` or `;` could break the JDBC URL
  parsing.
- `var apiUrl: String = "https://starlightskins.lunareclipse.studio/..."` is used
  elsewhere (not in this batch) by substituting `<name>` — an unencoded player
  name with `?` or `#` would corrupt the URL. (Worth flagging because the
  `<name>` placeholder is exactly the URL-injection surface.)

**Why it matters:** Security. Both are config-controlled so exploitation requires
operator misconfiguration, but a generated-config UI (e.g. an in-game setup wizard)
that lets an admin paste a URL becomes the attack surface.
**Suggested fix:** Validate `sqliteFile` is a simple filename (no `..`, no path
separators, no URL metacharacters). URL-encode `<name>` when substituting into
`apiUrl`. Or, simpler, build the JDBC URL via `URI` and validate.
**Confidence:** low

---

### [SEV: low] service/ArenaService.kt:53-55 — `addToCache` is misleadingly named and only updates memory
**What:** `fun addToCache(arena: Arena) { cache[arena.name.lowercase()] = arena }`.
Used by `AdminWandListener.kt:89, 99` for in-progress arena edits. There is no DB
write. A caller that uses this expecting persistence loses data on the next
plugin restart.
**Why it matters:** Defensive-coding nit. The current callers know the rule, but
the function name suggests "save to cache" which sounds like "save for next time".
**Suggested fix:** Rename to `updateLocalArena` or `stageArenaUpdate`, and add a
KDoc that explicitly says "in-memory only; call `save()` to persist".
**Confidence:** low

---

## Test-coverage gaps

The following behaviour is currently untested. Each gap has a corresponding
real-bug risk in the production code.

| Untested behaviour | Where the bug would live | Test that should exist |
|---|---|---|
| `getOrPut` is safe under concurrent calls (no stampede) | `CacheManager.kt:30-35` | `CacheManagerConcurrentTest`: 8 threads call `getOrPut` for the same key with an `AtomicInteger` counter in `compute`; assert counter == 1. |
| `getOrCreate` is safe under concurrent calls (no duplicate `PlayerStats` rows) | `StatisticsService.kt:23-33` | `StatisticsServiceConcurrentTest` (or integration test against H2): 8 threads call `getOrCreate` for the same `(uuid, mode)`; assert exactly one row in `PlayerStatsTable`. |
| `KILL_DEATH_RATIO` leaderboard actually orders by `kills / deaths` | `PlayerStatsRepository.kt:64-65` | A unit test that inserts (kills=100, deaths=100) and (kills=50, deaths=1) and asserts the second is ranked first. Currently the test at `PlayerStatsRepositoryTest.kt:73-81` only covers the `KILLS` case. |
| `WIN_RATE` leaderboard actually orders by `wins / games_played` | `PlayerStatsRepository.kt:65` | Similar to above: (wins=50, games_played=200) should rank below (wins=1, games_played=1). |
| `ArenaRepository.toArena` does not silently return empty spawn points on bad JSON | `ArenaRepository.kt:69-91` | Insert a row with malformed `spawn_points_json`; assert either the load throws *or* the operator is warned — definitely do not assert `emptyList()`. |
| `ArenaRepository.toArena` does not silently substitute `UUID.randomUUID()` on bad `id` | `ArenaRepository.kt:62` | Insert a row with `id = "not-a-uuid"`; assert behaviour is logged and stable across re-reads. |
| `DatabaseService.migrateSqliteLegacyArenasTable` rebuilds a legacy table and preserves all data | `DatabaseService.kt:85-142` | Integration test against SQLite: create a legacy `arenas` table without a PK, populate 10 rows, run the migration, assert the new table has the 10 rows and the new PK constraint. Also test the case where the migration throws mid-way (e.g. by mocking a `Statement` that fails on `DROP TABLE`) and assert `PRAGMA foreign_keys` is reset to `ON` on the pooled connection. |
| `recordGameEnd` aggregates `gp.placement` correctly for rage-quitters | `StatisticsService.kt:83-121` | A unit test with a `GamePlayer` whose `placement == 0` (the default) and a non-zero `bestPlacement` on the existing stats row; assert `bestPlacement` is *not* overwritten with the ragequit game's `totalPlayers`. |
| `getAggregatedStats` returns a sum, not a snapshot of one mode | `StatisticsService.kt:148-172` | Insert three rows for the same uuid (one per mode); assert the aggregated kills = sum of all three. |
| `getTotalPlayerCount` returns unique-player count, not row count | `PlayerStatsRepository.kt:81-83` | Insert two rows for the same uuid (different modes); assert count == 1, not 2. |
| `getOrCreate` for a player who has never played does not write a row | `StatisticsService.kt:23-33` (and `preloadPlayerStats:40-45`) | Call `preloadPlayerStats` for a brand-new uuid; assert `PlayerStatsTable.select { uuid eq … }.count() == 0` afterwards. |
| `CacheManager.put` respects the `maxSize` cap as a hard limit | `CacheManager.kt:38-47` | Insert `maxSize + N` non-expiring keys; assert `size() <= maxSize` after each insert. |
| `ArenaService.removeArena` is cache+DB consistent under DB failure | `ArenaService.kt:57-61` | Mock `ArenaRepository.delete` to throw; assert the arena is *still* in the cache. |
| `DatabaseService.init` rejects unknown `database.type` with a clear error | `DatabaseService.kt:38-60` | Construct a `LumaSGConfig` with `database.type = "ORACLE"`; assert a descriptive `IllegalStateException` is thrown mentioning the unsupported value. |
| `LumaSGConfig` validation: `minPlayers > maxPlayers` | `LumaSGConfig.kt:66-68` | Assert the config loader (wherever it lives) either clamps or warns on this misconfiguration. |

---

## Files reviewed

```
src/main/kotlin/net/lumalyte/lumasg/config/LumaSGConfig.kt             (461 lines)
src/main/kotlin/net/lumalyte/lumasg/persistence/CacheManager.kt        ( 67 lines)
src/main/kotlin/net/lumalyte/lumasg/persistence/DatabaseExtensions.kt   (  9 lines)
src/main/kotlin/net/lumalyte/lumasg/persistence/DatabaseService.kt      (151 lines)
src/main/kotlin/net/lumalyte/lumasg/persistence/repositories/ArenaRepository.kt         ( 95 lines)
src/main/kotlin/net/lumalyte/lumasg/persistence/repositories/PlayerStatsRepository.kt    (133 lines)
src/main/kotlin/net/lumalyte/lumasg/persistence/tables/ArenaTable.kt                   ( 24 lines)
src/main/kotlin/net/lumalyte/lumasg/persistence/tables/GameHistoryTable.kt              ( 17 lines)
src/main/kotlin/net/lumalyte/lumasg/persistence/tables/PlayerStatsTable.kt              ( 28 lines)
src/main/kotlin/net/lumalyte/lumasg/service/ArenaService.kt                             ( 89 lines)
src/main/kotlin/net/lumalyte/lumasg/statistics/StatisticsService.kt                     (173 lines)
```

Plus context: `domain/Arena.kt`, `domain/LootMode.kt`, `domain/StatType.kt`,
`game/Game.kt`, `game/GamePlayer.kt`, `listeners/PlayerListener.kt`,
`listeners/AdminWandListener.kt`, `discord/DiscordService.kt`, and the two
existing test files `persistence/CacheManagerTest.kt` and
`persistence/PlayerStatsRepositoryTest.kt`.
