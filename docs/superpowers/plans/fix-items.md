# HERMES PLAN — fix-items (LumaSG audit remediation, batch B)

Baseline: `origin/main` @ `5baa40f`. Branch: `fix/fix-items` (already checked out).
Fixes audit findings H9, H10, H11, H14, H15. **Touch ONLY `items/AirstrikeItem.kt`,
`items/CustomItemsManager.kt`, `items/AirdropFlareItem.kt`, `listeners/CustomItemListener.kt`,
and new test files.** Nothing else.

## Environment / gate
```
export JAVA_HOME=/opt/data/jdk-21.0.11+10
export GRADLE_USER_HOME=/opt/data/.gradle
./gradlew <task> --no-daemon --console=plain -Dmaven.repo.local=/opt/data/.m2/repository
```
Final gate: `./gradlew clean test shadowJar` → `BUILD SUCCESSFUL`, all tests passing.
No detekt.

## CONFIRMED API SYMBOLS
- `items/AirstrikeItem.kt`: in `launchAirstrike(...)` the per-meteor `onImpact` lambda (~293)
  builds `immuneUUIDs`: adds `callerTeam.members` then `immuneUUIDs.remove(callerUuid)` —
  INVERTED (teammates immune, caller not). `game.teamManager.getTeamForPlayer(callerUuid): Team?`.
  In `handleLocked` (~183): `held.amount--` runs BEFORE
  `val game = gameManager.getGameForPlayer(uuid) ?: return` then `launchAirstrike(...)` —
  charge consumed even when game lookup fails. `chargeStates`, `toRemove`.
- `items/CustomItemsManager.kt`: `registerDefaults()` (~45) constructs items and calls
  `item.register()` for `GliderItem`/`SmokeGrenadeItem`/`AirstrikeItem` (each calls
  `plugin.server.pluginManager.registerEvents(this, plugin)`). `shutdownItems()` (~58) stops
  runnables but does NOT `HandlerList.unregisterAll`. `reload()` (~99) =
  `shutdownItems(); registry.clear(); registerDefaults()` → double registration.
- `listeners/CustomItemListener.kt`: `onBombExplode`/poison/fire handlers (~140-217) do
  `world.getNearbyEntities(...).filterIsInstance<Player>()` then `target.damage(damage)` +
  velocity, filtered only by `throwerId`; NO check of `game.phase`, `isPvpEnabled()`, or
  `config.game.teams.friendlyFire`. `gameManager.getGameForPlayer(uuid): Game?`,
  `game.isPvpEnabled(): Boolean`, `game.teamManager.areTeammates(a, b)` /
  `getTeamForPlayer(uuid)`.
- `items/AirdropFlareItem.kt`: `onUse` (~51) launches a coroutine; at impact (~99-108) runs
  `MeteorUtils.spawnExplosion(...)` then `findSolidGround(dropLocation)` then
  `chestLoc.block.type = Material.CHEST` with NO air/arena/occupancy check; uses
  `player.location` at impact time (not snapshotted at use time).
- `game/Game.kt`: `fun isPvpEnabled(): Boolean = phase is GamePhase.Active || phase is
  GamePhase.Deathmatch`. `game.teamManager` exposes `areTeammates`, `getTeamForPlayer`.

---

## TASK ORDER

### Task 1 — H9: airstrike friendly-fire immunity is inverted (TDD pure helper)
REQ: the caller's whole team (including the caller) is immune to their own airstrike;
non-team players are not.
RED: add a pure helper to `AirstrikeItem` (companion or top-level):
`fun airstrikeImmuneUuids(teamMembers: Collection<UUID>?, callerUuid: UUID): Set<UUID>`.
Create `src/test/kotlin/net/lumalyte/lumasg/items/AirstrikeImmunityTest.kt`:
- solo (teamMembers null): `airstrikeImmuneUuids(null, caller) == setOf(caller)` (caller still
  immune to own strike).
- team: `airstrikeImmuneUuids(listOf(caller, mate), caller)` contains BOTH `caller` and `mate`.
(RED: helper absent.)
GREEN: implement `airstrikeImmuneUuids = (teamMembers?.toSet() ?: emptySet()) + callerUuid`.
In the `onImpact` lambda, replace the inverted block with
`val immuneUUIDs = airstrikeImmuneUuids(callerTeam?.members, callerUuid)` and DELETE the
`immuneUUIDs.remove(callerUuid)` line.
COMMIT: `fix(items): airstrike no longer damages the caller's own team/self (H9)`

### Task 2 — H10: airstrike charge consumed before game lookup (guarded change)
REQ: do not consume the item if the strike can't launch.
In `handleLocked`, reorder: resolve `val game = gameManager.getGameForPlayer(uuid) ?: return`
FIRST (before `toRemove.add(uuid)` and before `held.amount--`); only after a non-null game,
add to `toRemove`, decrement the held item, clear the action bar, and `launchAirstrike(...)`.
No unit test (Bukkit Player). Verify compile.
COMMIT: `fix(items): airstrike consumes charge only when it actually launches (H10)`

### Task 3 — H11: reload() must not double-register listeners (guarded change)
REQ: `reload()` is idempotent w.r.t. Bukkit event registration.
In `shutdownItems()`, for each Listener item (Glider/SmokeGrenade/Airstrike) call
`org.bukkit.event.HandlerList.unregisterAll(item)` (item implements `Listener`). That makes
`reload()` (shutdown→clear→register) net-zero. Add the import. No unit test. Verify compile.
COMMIT: `fix(items): unregister item listeners on reload to stop double handling (H11)`

### Task 4 — H14: bomb/poison/fire damage must respect phase & friendly-fire (guarded change)
REQ: thrown-item damage obeys the same rules as normal PvP.
In `CustomItemListener`, in each explosion/damage handler, before applying
`target.damage(...)`/velocity to a player target: resolve
`val game = gameManager.getGameForPlayer(throwerId) ?: continue` (or the target's game),
and skip the target when `!game.isPvpEnabled()` (Waiting/Countdown/Grace) OR
(`!config.game.teams.friendlyFire` AND `game.teamManager.areTeammates(throwerId, target.uniqueId)`)
OR the target is the thrower's own teammate per the same rule. Confirm the exact config path
for friendly-fire by reading `LumaSGConfig` (likely `config.game.teams.friendlyFire`); if the
field differs, use the real one. Apply consistently to the bomb, poison-bomb, and fire-bomb
paths. No unit test. Verify compile.
COMMIT: `fix(items): thrown-bomb damage respects phase and friendly-fire (H14)`

### Task 5 — H15: airdrop chest placement must be safe (guarded change)
REQ: the airdrop chest must land at the aimed location, only replacing air, inside the arena,
not inside a player.
Changes in `AirdropFlareItem`:
- Snapshot the target location at `onUse` time (`val target = player.location.clone()` /
  the aimed location) and use THAT for impact + chest placement, not `player.location` read at
  impact time.
- Before `chestLoc.block.type = Material.CHEST`: require `chestLoc.block.type == Material.AIR`
  (or `isAir`); if not, search upward/adjacent for the nearest air block, and abort with no
  placement (log debug) if none found within a few blocks.
- Guard against the void: if `findSolidGround` reaches `y <= world.minHeight`, abort placement.
- Skip placement if a player occupies the target block.
No unit test (Bukkit world). Verify compile.
COMMIT: `fix(items): airdrop chest placed safely at aimed location (H15)`

## FINISH
Final gate → `BUILD SUCCESSFUL`. `git push fork fix/fix-items`. Print `FIX-ITEMS_DONE` +
commit short-hashes + `BUILD SUCCESSFUL`. On 3x-stuck gate, `FIX-ITEMS_HALT` with the failing
command/error, still push the green commits. Every commit:
`Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Push to `fork` ONLY.
