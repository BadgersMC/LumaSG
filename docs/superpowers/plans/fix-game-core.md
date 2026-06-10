# HERMES PLAN — fix-game-core (LumaSG audit remediation, batch A)

Baseline: `origin/main` @ `5baa40f`. Branch: `fix/fix-game-core` (already checked out).
Fixes audit findings C1, C3, H1, H2, H3, H4, H5, H6, H16. **Touch ONLY the files named
below + new test files.** Do not touch other batches' files (items, persistence,
arena/chest).

## Environment / gate (run for EVERY gradle invocation)
```
export JAVA_HOME=/opt/data/jdk-21.0.11+10
export GRADLE_USER_HOME=/opt/data/.gradle
./gradlew <task> --no-daemon --console=plain -Dmaven.repo.local=/opt/data/.m2/repository
```
Final gate: `./gradlew clean test shadowJar` (with those flags) must end
`BUILD SUCCESSFUL` with all tests passing. There is NO detekt in this project — do not add it.

## CONFIRMED API SYMBOLS (verified against 5baa40f — do not invent others)
- `game/Team.kt`: `data class Team`, `@Volatile var isEliminated` (private set),
  `fun eliminate()` (sets isEliminated=true), `val isAlive` (currently
  `!isEliminated && members.any { Bukkit.getPlayer(it)?.isOnline == true }`),
  `fun hasOnlineMembers()`, `val members: MutableList<UUID>`.
- `domain/GamePhase.kt`: sealed; objects/classes `Waiting`, `Countdown(secondsLeft)`,
  `Grace(...)`, `Active(...)`, `Deathmatch(...)`, `Ended(winner)`.
- `game/Game.kt`: `class Game(... arena: Arena, ... )`; `private val _players =
  ConcurrentHashMap<UUID,GamePlayer>()`; `var phase: GamePhase`; `fun addPlayer(player: Player)`
  (line ~105, picks spawn by `_players.size`, calls `playerStateManager.saveAndPrepare`,
  no phase/capacity guard); `fun eliminate(uuid: UUID)` (line ~138, NO membership guard);
  `fun reconnectPlayer(player: Player): Boolean` (line ~159, calls
  `playerStateManager.saveAndPrepare` — the bug); `arena.maxPlayers`.
- `game/PlayerStateManager.kt`: `class PlayerStateManager(private val config: LumaSGConfig)`;
  `private class SavedState(val location: Location, ...)`; `private val saved =
  ConcurrentHashMap<UUID, SavedState>()`; `fun saveAndPrepare(player: Player,
  spawnLocation: Location)` (snapshots `if (config.game.saveLocation) player.location.clone()
  else spawnLocation`); `fun restore(player: Player)`; `fun makeSpectator(player: Player)`;
  `config.game.saveLocation`, `config.game.clearInventory`, `config.lobby.*`.
- `gui/GameBrowserMenu.kt`: click handler (~line 65) checks `isPlayerInGame` + size cap,
  then `game.addPlayer(viewer)`. `game.phase`, `game.players.size`, `game.arena.maxPlayers`.
- `commands/SGCommand.kt`: `forceStart(...)` (~296) validates then only sends a message
  (no-op); `create(...)` (~437) annotated `@Async @PlayerOnly suspend`, reads
  `player.location` + `player.sendMessage` off-thread.
- `game/TeamQueueManager.kt`: `fun accept(player: Player): Boolean` (~76):
  `invitation.team.add(player.uniqueId); preGameTeams[player.uniqueId] = invitation.team`
  with NO removal from a prior team; `private val preGameTeams: ConcurrentHashMap<UUID, Team>`
  (confirm exact name/type by reading the file).
- `listeners/PlayerListener.kt`: `onPlayerDeath` (~107) builds `killCommand` via
  `.replace("<player>", it.name)` then `plugin.server.dispatchCommand(consoleSender, cmd)`.
- `game/Celebration.kt`: win-announcement command path uses the same `<player>` substitution.
- Test stack: JUnit5 (`org.junit.jupiter.api.Test`), `kotlin.test.*`, MockBukkit
  (`com.github.seeseemelk:MockBukkit-v1.21`, `org.mockbukkit.mockbukkit.MockBukkit` /
  package `be.seeseemelk.mockbukkit.MockBukkit` — verify the import that the existing
  tests/this MockBukkit version expose by checking how `MockBukkit.mock()` is imported),
  mockk, kotlinx-coroutines-test (`runTest`). Existing tests live in
  `src/test/kotlin/net/lumalyte/lumasg/{domain,game,persistence}`.

---

## TASK ORDER (commit after each; crits first so a partial push still has value)

### Task 1 — H2: `Team.isAlive` must reflect elimination, not online status (TDD)
REQ: A team is alive iff it has not been eliminated; transient member disconnects must NOT
make it "dead".
RED: create `src/test/kotlin/net/lumalyte/lumasg/game/TeamTest.kt`:
- `team with members and not eliminated is alive` — `Team(id=1, members=mutableListOf(UUID.randomUUID()))`;
  assert `team.isAlive` is `true` (NO MockBukkit started — the current impl calls
  `Bukkit.getPlayer` and will throw/return false → RED).
- `eliminated team is not alive` — call `team.eliminate()`; assert `!team.isAlive`.
- `empty non-eliminated team is not alive` — `Team(id=2)` with empty members; assert
  `!team.isAlive` (a team with no members can't be alive).
GREEN: change `isAlive` to `get() = !isEliminated && members.isNotEmpty()`.
COMMIT: `fix(game): Team.isAlive tracks elimination, not online status (H2)`

### Task 2 — C3: reject joins outside joinable phases / over capacity (TDD pure helper + guard)
REQ: Players may only join a game in `Waiting` or `Countdown`, and never beyond `maxPlayers`.
RED: in `domain/GamePhase.kt` add `fun GamePhase.isJoinable(): Boolean = this is GamePhase.Waiting
|| this is GamePhase.Countdown`. Create
`src/test/kotlin/net/lumalyte/lumasg/domain/GamePhaseJoinableTest.kt` asserting
`Waiting.isJoinable()` and `Countdown(10).isJoinable()` are true and
`Active(1).isJoinable()`, `Deathmatch(1).isJoinable()`, `Grace(1).isJoinable()`,
`Ended(null).isJoinable()` are false. (RED: function doesn't exist → compile fail, then
add it → GREEN.)
GREEN/guard: in `Game.addPlayer`, at the top add
`if (!phase.isJoinable() || _players.size >= arena.maxPlayers) return` (change return type
is `Unit` already — just early-return). In `GameBrowserMenu` click handler, before
`game.addPlayer`, add a guard: if `!game.phase.isJoinable()` send `"§cThat game has already
started."` and `return@SimpleItem`. Keep the existing full-game check.
COMMIT: `fix(game): guard addPlayer + browser against mid-game/over-capacity joins (C3)`

### Task 3 — C1 + H1: reconnect must not overwrite the pre-game snapshot; saveLocation=false
must not pin the arena spawn (TDD with MockBukkit)
REQ C1: `reconnectPlayer` must preserve the original saved inventory/state.
REQ H1: with `config.game.saveLocation == false`, restore must NOT send the player to the
in-arena spawn; the saved location is "none / use lobby".
RED: create `src/test/kotlin/net/lumalyte/lumasg/game/PlayerStateManagerTest.kt` using
MockBukkit:
- start MockBukkit, add a player, give them a marker item; build a `PlayerStateManager`
  with a config (mockk relaxed, `config.game.saveLocation returns true`,
  `config.game.clearInventory returns true`); call `saveAndPrepare(player, spawnA)`; then
  set the player's inventory to different "loot"; call `prepareWithoutSaving(player, spawnB)`
  (NEW method — doesn't exist yet → RED); call `restore(player)`; assert the marker item is
  back (i.e. the original snapshot survived).
- `saveLocation false does not save the arena spawn` — `config.game.saveLocation returns
  false`; `saveAndPrepare(player, arenaSpawn)`; assert the stored `SavedState.location` is
  `null` (expose a test seam: add `internal fun savedLocationOf(uuid): Location?`), NOT
  `arenaSpawn`.
GREEN:
- Make `SavedState.location` nullable (`val location: Location?`). In `saveAndPrepare` set
  `location = if (config.game.saveLocation) player.location.clone() else null`.
- In `restore`, when `location == null`, teleport to the lobby
  (`config.lobby` resolution already used elsewhere — reuse the same lobby lookup the class
  already has; if none, skip the teleport) instead of a non-null arena spawn.
- Add `fun prepareWithoutSaving(player: Player, spawnLocation: Location)` that does the
  teleport + gamemode/health/clear-inventory prep of `saveAndPrepare` WITHOUT writing to
  `saved`.
- In `Game.reconnectPlayer`, replace `playerStateManager.saveAndPrepare(player, spawn)` with
  `playerStateManager.prepareWithoutSaving(player, spawn)`.
- Add `internal fun savedLocationOf(uuid: UUID): Location? = saved[uuid]?.location` (test seam).
COMMIT: `fix(game): reconnect preserves pre-game snapshot; saveLocation=false uses lobby (C1,H1)`

### Task 4 — H6: `Game.eliminate` must be a no-op for non-participants (guarded change)
REQ: `eliminate(uuid)` must not mutate state when `uuid` is not an active player.
No unit test (Game requires full DI wiring; the repo's own game tests avoid constructing
Game). Change: first line of `eliminate` → `if (_players[uuid] == null) return`. Also make
it idempotent: if `_players[uuid]?.isAlive == false` return early too.
COMMIT: `fix(game): eliminate() ignores non-participant/duplicate UUIDs (H6)`

### Task 5 — H16: sanitize player names before console-command substitution (TDD pure fn)
REQ: A player name substituted into a reward command must not inject extra command tokens.
RED: create `util/CommandSanitizer.kt` with
`fun sanitizeCommandArg(raw: String): String` that strips whitespace and any character
outside `[A-Za-z0-9_]` (vanilla-name charset; Bedrock/Geyser names with spaces or dots are
collapsed). Create `src/test/kotlin/net/lumalyte/lumasg/util/CommandSanitizerTest.kt`:
assert `sanitizeCommandArg("Steve") == "Steve"`,
`sanitizeCommandArg(".Bedrock Player") == "BedrockPlayer"`,
`sanitizeCommandArg("a b say hi") == "absayhi"`. (RED: file/fn absent.)
GREEN: implement the function. Apply it at the `<player>` substitution sites in
`PlayerListener.onPlayerDeath` (killCommand) and `Celebration` (win/announcement command)
— wrap the name: `.replace("<player>", sanitizeCommandArg(it.name))`. Do NOT change the
chat/broadcast message substitutions (those are not dispatched as commands).
COMMIT: `fix(rewards): sanitize player name in dispatched reward commands (H16)`

### Task 6 — H4: `create` command must not touch Bukkit API off-thread (guarded change)
REQ: `/sg create` must read `player.location` and message the player on the main thread.
Change: remove the `@Async` annotation from `create(...)` in `SGCommand.kt` (the operation
is one DB row; the command framework runs it on the main thread). Leave the `suspend` if the
framework requires it for the repo call; if removing `@Async` causes a signature/compile
issue, instead wrap every `player.location` / `player.sendMessage` in
`withContext(bukkitDispatcher) { ... }` and keep the repo call off-thread. Prefer the simpler
`@Async` removal. Verify it compiles.
COMMIT: `fix(commands): /sg create runs Bukkit API on main thread (H4)`

### Task 7 — H3: `/sg forcestart` must actually start the game (guarded change)
REQ: forcestart transitions a `Waiting` game out of the lobby wait.
Read how a normal game leaves `Waiting` (the countdown/lifecycle in `Game`). Add a public
`fun Game.forceStart()` that performs the same transition the countdown's terminal step does
(e.g. sets `phase = GamePhase.Countdown(0)` or signals the waiting lifecycle to proceed —
match the EXISTING mechanism; do not invent a new state machine). In `SGCommand.forceStart`,
after the existing validation, call `game.forceStart()` before the confirmation message.
No unit test (Game wiring). Verify compile + that the message only prints after the call.
COMMIT: `fix(commands): /sg forcestart advances the game out of waiting (H3)`

### Task 8 — H5: accepting a queue invite must leave any prior pre-game team (guarded change)
REQ: A player can be in at most one pre-game team.
In `TeamQueueManager.accept`, before `invitation.team.add(player.uniqueId)`, remove the
player from any existing pre-game team: look up `preGameTeams[player.uniqueId]`, if present
call `.remove(player.uniqueId)` on that team and clear the map entry; also defensively
`for (t in preGameTeams.values) t.remove(player.uniqueId)`. Then add + set the pointer.
(Read the file to confirm the exact field name/type before editing.) No unit test (Bukkit
Player needed). Verify compile.
COMMIT: `fix(game): queue invite-accept removes player from prior team (H5)`

## FINISH
Run the final gate. When `BUILD SUCCESSFUL` with all tests green, push to the fork ONLY:
`git push fork fix/fix-game-core`. Then print `FIX-GAME-CORE_DONE` followed by the commit
short-hashes and the final `BUILD SUCCESSFUL` line. If the gate fails 3 times on the same
task, STOP and print `FIX-GAME-CORE_HALT` with the failing command + error, but still
`git push fork fix/fix-game-core` so the green commits up to that point are reviewable.
Every commit message must include: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
Push to `fork` ONLY — never origin/upstream.
