# HERMES PLAN — fix-persistence (LumaSG audit remediation, batch C)

Baseline: `origin/main` @ `5baa40f`. Branch: `fix/fix-persistence` (already checked out).
Fixes audit findings H7, H8. **Touch ONLY `persistence/repositories/PlayerStatsRepository.kt`,
`util/cache/PlayerDataCache.kt`, and new test files.** Nothing else.

## Environment / gate
```
export JAVA_HOME=/opt/data/jdk-21.0.11+10
export GRADLE_USER_HOME=/opt/data/.gradle
./gradlew <task> --no-daemon --console=plain -Dmaven.repo.local=/opt/data/.m2/repository
```
Final gate: `./gradlew clean test shadowJar` (with those flags) → `BUILD SUCCESSFUL`,
all tests passing. No detekt in this project.

## CONFIRMED API SYMBOLS
- `persistence/repositories/PlayerStatsRepository.kt`: `suspend fun getLeaderboard(statType:
  StatType, limit: Int = 10, lootMode: LootMode? = null): List<PlayerStats>` (~53). Currently
  builds `orderColumn: Expression<*>` and for `KILL_DEATH_RATIO -> PlayerStatsTable.kills`,
  `WIN_RATE -> PlayerStatsTable.wins` (the bug). Uses Exposed DSL,
  `PlayerStatsTable.selectAll().where{...}.orderBy(orderColumn to SortOrder.DESC)...`.
  `dbQuery { }` wraps the suspend transaction.
- `persistence/tables/PlayerStatsTable.kt`: columns `kills`, `deaths`, `wins`, `gamesPlayed`
  (db name `games_played`), etc. PK is `(uuid, lootMode)`.
- `domain/StatType.kt`: enum incl. `KILL_DEATH_RATIO`, `WIN_RATE`, `KILLS`, `WINS`.
- Existing test `src/test/kotlin/net/lumalyte/lumasg/persistence/PlayerStatsRepositoryTest.kt`
  already wires H2 + Exposed (`Database.connect("jdbc:h2:mem:...")`,
  `SchemaUtils.create(PlayerStatsTable)`) and constructs
  `PlayerStatsRepository(db = io.mockk.mockk(relaxed = true))`. MIRROR this harness.
- `util/cache/PlayerDataCache.kt`: `getCachedStats(uuid)` loader catches DB failure and
  returns `PlayerStats(uuid, "Unknown Player")` (~193-196, the zeroed-stats bug);
  `getCachedDisplayName(uuid)` does `CompletableFuture.supplyAsync({ plugin.server.getPlayer(uuid)
  ... }, executor)` where `executor = ForkJoinPool.commonPool()` (~101-115, Bukkit off-thread);
  `loadPermissionFromBukkit(key)` calls `plugin.server.getPlayer` + `hasPermission` on a
  Caffeine worker (~199-208). Caffeine caches: `statsCache` (expireAfterWrite ~30m),
  `permissionCache` (expireAfterWrite 10m). Constructor deps include `plugin: JavaPlugin`,
  `playerStatsRepository`, a `bukkitDispatcher`/scope (read the file for exact names).

---

## TASK ORDER

### Task 1 — H7: KDR and Win-Rate leaderboards must order by the computed ratio (TDD)
REQ: `KILL_DEATH_RATIO` orders by `kills/deaths`; `WIN_RATE` by `wins/gamesPlayed`.
RED: add to `PlayerStatsRepositoryTest.kt` (reuse its H2/companion harness):
- `KDR leaderboard ranks better ratio first`: upsert A `(kills=100, deaths=100)` and
  B `(kills=50, deaths=1)`; `getLeaderboard(StatType.KILL_DEATH_RATIO, 10)`; assert
  `board.first().playerName == "B"`. (RED: current code orders by raw kills → A first.)
- `Win-rate leaderboard ranks better rate first`: upsert C `(wins=50, gamesPlayed=200)` and
  D `(wins=1, gamesPlayed=1)`; assert `getLeaderboard(StatType.WIN_RATE, 10).first()` is D.
- Division-by-zero guard: upsert E `(kills=5, deaths=0)`; assert it appears (no exception)
  and outranks A.
GREEN: in `getLeaderboard`, for `KILL_DEATH_RATIO` and `WIN_RATE` order by a computed Exposed
expression instead of a raw column. Use a custom expression, e.g.
`val kdr = CustomDoubleFunction(...)` or Exposed's `(PlayerStatsTable.kills.castTo<Double>(DoubleColumnType())
/ Coalesce(NullIf(PlayerStatsTable.deaths, intLiteral(0)), doubleLiteral(1.0)))` — pick the
Exposed 0.55.0 API that compiles (verify against the version on the box; `org.jetbrains.exposed.sql`).
If a pure-SQL expression is awkward in the DSL, an acceptable alternative: fetch the candidate
set (e.g. `limit * 5` rows ordered by the numerator) and sort in Kotlin by the computed ratio,
then `take(limit)` — but DOCUMENT the bound. Whichever path, the three tests above must pass.
COMMIT: `fix(stats): KDR and Win-Rate leaderboards order by computed ratio (H7)`

### Task 2 — H8a: DB failure must NOT cache a zeroed "Unknown Player" stats record (guarded)
REQ: A transient DB error must not pin all-zero stats for 30 minutes.
Change `getCachedStats`'s loader: on exception, log the error and **complete the future
exceptionally** (rethrow / `throw e`) OR return a value the cache layer does not persist —
do NOT return a fabricated `PlayerStats(uuid, "Unknown Player")` that Caffeine stores. The
simplest correct form: let the loader propagate the exception so Caffeine does not cache a
failed load (AsyncLoadingCache does not retain failed entries), and have the public accessor
return a transient default to the immediate caller without writing it to the cache. Ensure a
subsequent call after the DB recovers re-queries. No unit test (Caffeine/async). Verify compile.
COMMIT: `fix(cache): do not cache zeroed stats on DB failure (H8)`

### Task 3 — H8b: Bukkit API must run on the main thread in cache loaders (guarded)
REQ: `getCachedDisplayName` and `loadPermissionFromBukkit` must not call
`plugin.server.getPlayer` / `hasPermission` off the main thread.
Change: wrap the Bukkit calls in the project's main-thread dispatch. The class already has a
coroutine scope / `bukkitDispatcher` (confirm name by reading the file). Replace the
`supplyAsync(..., ForkJoinPool.commonPool())` body so the `getPlayer`/`displayName` read runs
via `withContext(bukkitDispatcher)` (bridge the coroutine to the returned
`CompletableFuture` with `scope.future { ... }` if a `CompletableFuture` return type is
required). For `loadPermissionFromBukkit`, run the `getPlayer`/`hasPermission` read on the
main thread the same way; keep the existing exception → `false` fallback but only as a true
error path, and prefer `getPlayer(uuid)` resolved on-thread. No unit test (Bukkit). Verify
compile. Keep the public signatures stable so callers don't change.
COMMIT: `fix(cache): resolve player/permission on main thread in cache loaders (H8)`

## FINISH
Final gate → `BUILD SUCCESSFUL`. `git push fork fix/fix-persistence`. Print
`FIX-PERSISTENCE_DONE` + commit short-hashes + the `BUILD SUCCESSFUL` line. On a 3x-stuck
gate, `FIX-PERSISTENCE_HALT` with the failing command/error, still push the green commits.
Every commit: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Push to `fork` ONLY.
