# HERMES PLAN — fix-arena-chest (LumaSG audit remediation, batch D)

Baseline: `origin/main` @ `5baa40f`. Branch: `fix/fix-arena-chest` (already checked out).
Fixes audit findings C2, H12, H13. **Touch ONLY `domain/Arena.kt`,
`listeners/ChestListener.kt`, `service/ArenaService.kt`, and new test files.** Nothing else.

## Environment / gate
```
export JAVA_HOME=/opt/data/jdk-21.0.11+10
export GRADLE_USER_HOME=/opt/data/.gradle
./gradlew <task> --no-daemon --console=plain -Dmaven.repo.local=/opt/data/.m2/repository
```
Final gate: `./gradlew clean test shadowJar` → `BUILD SUCCESSFUL`, all tests passing.
No detekt.

## CONFIRMED API SYMBOLS
- `domain/Arena.kt`: `data class Arena(... val center: SerializableLocation, val radius:
  Double = 500.0, ...)`. `fun scanForChests(): List<SerializableLocation>` (~35) is a triple
  nested loop `for x in (cx-r)..(cx+r) { for y ... { for z ... { world.getBlockAt(x,y,z) }}}`
  over the FULL cube — main-thread freeze at radius 500. `Bukkit.getWorld(worldName)`,
  `center.toBukkit()`, `SerializableLocation(worldName, x, y, z)`,
  `world.minHeight`/`world.maxHeight`, `Material.CHEST`/`TRAPPED_CHEST`.
- `service/ArenaService.kt`: `fun addToCache(arena: Arena)` (~53) = `cache[arena.name.lowercase()]
  = arena` (memory only — the H12 bug; wand edits only persist via onDisable saveAll).
  `suspend fun save(arena: Arena)` = `arenaRepo.save(arena); cache[...] = arena`.
  `suspend fun removeArena(arena)`. Constructor deps include `arenaRepo` (has
  `suspend fun save(arena)`), a coroutine scope, `logger`. Read the file to confirm the exact
  scope field name and that `arenaRepo.save` is suspend.
- `listeners/ChestListener.kt`: `onChunkLoad` (~57-88) finds the game by
  `getAllActiveGames().firstOrNull { it.arena.worldName == worldName }` then fills EVERY chest
  in the chunk → cross-arena fill when two arenas share a world. `filledChests` is a listener-
  global set. `chestManager.fillChest(location, tier, mode)`, `game.arena.center`,
  `game.arena.radius`, `Location.distanceSquared(...)`.

---

## TASK ORDER

### Task 1 — C2: `scanForChests()` must not freeze the main thread (TDD radius guard + chunk scan)
REQ: scanning must be bounded; an out-of-range radius must be rejected rather than iterating
billions of blocks.
RED: add a pure helper to `Arena` (companion):
`fun isChestScanRadiusSafe(radius: Double): Boolean` returning `radius in 1.0..64.0` (a sane
arena-local cap). Create `src/test/kotlin/net/lumalyte/lumasg/domain/ArenaScanTest.kt`:
assert `Arena.isChestScanRadiusSafe(500.0)` is false, `Arena.isChestScanRadiusSafe(32.0)` is
true, `Arena.isChestScanRadiusSafe(0.0)` is false. (RED: helper absent.)
GREEN:
- Implement the helper.
- In `scanForChests`, compute an effective radius `val r = radius.toInt().coerceAtMost(64)`
  (cap), and if `!isChestScanRadiusSafe(radius)` log a WARN naming the arena + radius so the
  operator knows the scan was capped. Keep the existing chest-detection logic but iterate only
  the chunks intersecting the (capped) bounding box — get chunk-bounded block access via
  `world.getChunkAt(cx >> 4, cz >> 4)` over the chunk range, reading block types within bounds
  — rather than `getBlockAt` over the raw cube, to avoid loading the whole volume at once.
  If the chunk-iteration refactor is risky to get compiling, the MINIMUM acceptable fix is the
  radius cap (so the worst case is ~64³ ≈ 260K lookups, not 10⁹) plus the WARN log; the
  bounding-box iteration can stay `getBlockAt` within the capped range.
COMMIT: `fix(arena): cap scanForChests radius to prevent main-thread freeze (C2)`

### Task 2 — H12: wand/cache arena edits must persist (TDD mockk verify)
REQ: an in-memory arena update must also be written to the DB so it survives a crash/reload.
RED: create `src/test/kotlin/net/lumalyte/lumasg/service/ArenaServiceTest.kt`:
construct `ArenaService` with a relaxed-mockk `arenaRepo` and a test scope (use the same scope
type the constructor needs; if it needs a `CoroutineScope`, pass
`CoroutineScope(UnconfinedTestDispatcher())` from kotlinx-coroutines-test so the async save
runs synchronously). Call `addToCache(arena)` and then verify the arena was both cached
(`getArena(arena.name) == arena`) AND persisted (`coVerify { arenaRepo.save(arena) }`).
(RED: `addToCache` currently does not call `arenaRepo.save`.)
GREEN: change `addToCache` to also enqueue an async persist:
`scope.launch { runCatching { arenaRepo.save(arena) }.onFailure { logger.warn(...) } }`
(use the class's existing scope; do NOT make `addToCache` suspend — callers are sync event
handlers). This persists for ALL callers (including the wand) with no call-site change.
COMMIT: `fix(arena): persist arena edits made via addToCache (H12)`

### Task 3 — H13: chest fill must be scoped to the owning arena (guarded change)
REQ: when two arenas share a world, each game only fills chests inside its own arena bounds.
In `ChestListener.onChunkLoad`, after resolving a candidate game by world, only fill a chest
whose location is within that arena's bounds:
`chest.location.distanceSquared(game.arena.center.toBukkit()!!) <= game.arena.radius *
game.arena.radius`. When multiple active games share the world, iterate the matching games and
fill each chest with the game whose bounds actually contain it (skip chests in no game's
bounds). Key `filledChests` by arena to keep games isolated (e.g. include `game.arena.name` in
the key, or use a per-game set) so two arenas in one world don't share fill-tracking. No unit
test (Bukkit chunk/world). Verify compile.
COMMIT: `fix(chest): scope chunk-load chest fill to the owning arena bounds (H13)`

## FINISH
Final gate → `BUILD SUCCESSFUL`. `git push fork fix/fix-arena-chest`. Print
`FIX-ARENA-CHEST_DONE` + commit short-hashes + `BUILD SUCCESSFUL`. On 3x-stuck gate,
`FIX-ARENA-CHEST_HALT` with the failing command/error, still push the green commits. Every
commit: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Push to `fork` ONLY.
