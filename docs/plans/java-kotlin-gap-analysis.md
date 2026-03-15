# LumaSG Java → Kotlin Gap Analysis
## Exhaustive Method-by-Method Comparison

**Date:** 2026-03-15
**Java Source:** `remotes/origin/main` branch
**Kotlin Source:** `feature/kotlin-rewrite` branch (worktree)

> This document lists EVERY public method from the Java codebase and whether it has a Kotlin equivalent.
> Legend: ✅ = exists in Kotlin | ❌ = MISSING | 🔄 = different API/approach | 🚫 = intentionally omitted

---

## 1. ARENA PACKAGE

### Arena.java → domain/Arena.kt (data class)

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `Arena(name, plugin, spawnPoints, maxPlayers, minPlayers)` | `Arena(name, displayName, worldName, ...)` data class | 🔄 |
| `Arena(name, plugin)` | N/A | ❌ |
| `Arena(name, plugin, maxPlayers, minPlayers)` | N/A | ❌ |
| `Arena(name, plugin, center, radius)` | N/A | ❌ |
| `fromConfig(plugin, section, arenaName): Arena` | Replaced by `ArenaRepository.findByName()` | 🔄 |
| `saveToConfig(section): void` | Replaced by `ArenaRepository.save()` | 🔄 |
| `getName(): String` | `name` property | ✅ |
| `getId(): UUID` | No UUID on Arena | ❌ |
| `getWorld(): World` | `worldName` (String only, no World ref) | 🔄 |
| `setWorld(World)` | Immutable data class | 🔄 |
| `getCenter(): Location` | `center: SerializableLocation` | ✅ |
| `setCenter(Location)` | Immutable data class | 🔄 |
| `getSpawnPoints(): List<Location>` | `spawnPoints: List<SerializableLocation>` | ✅ |
| `addSpawnPoint(Location)` | Immutable data class | ❌ |
| `removeSpawnPoint(int)` | Immutable data class | ❌ |
| `getChestLocations(): List<Location>` | NOT PRESENT | ❌ |
| `addChestLocation(Location)` | NOT PRESENT | ❌ |
| `removeChestLocation(int)` | NOT PRESENT | ❌ |
| `getLobbySpawn(): Location` | NOT PRESENT | ❌ |
| `setLobbySpawn(Location)` | NOT PRESENT | ❌ |
| `getSpectatorSpawn(): Location` | NOT PRESENT | ❌ |
| `setSpectatorSpawn(Location)` | NOT PRESENT | ❌ |
| `getMaxPlayers(): int` | `maxPlayers` property | ✅ |
| `getMinPlayers(): int` | `minPlayers` property | ✅ |
| `getRadius(): int` | `radius: Double` | ✅ |
| `setRadius(int)` | Immutable data class | 🔄 |
| `canSupportPlayers(int): boolean` | NOT PRESENT | ❌ |
| `scanForChests(): int` | NOT PRESENT | ❌ |
| `cleanup(): void` | NOT PRESENT | ❌ |
| `showSpawnPoints(): void` | NOT PRESENT | ❌ |
| `hideSpawnPoints(): void` | NOT PRESENT | ❌ |
| `getAllowedBlocks(): Set<Material>` | NOT PRESENT | ❌ |
| `addAllowedBlock(Material)` | NOT PRESENT | ❌ |
| `removeAllowedBlock(Material)` | NOT PRESENT | ❌ |
| `isBlockAllowed(Material): boolean` | NOT PRESENT (hardcoded in PlayerListener) | ❌ |
| `loadFromConfig(plugin, section): Arena` | Replaced by DB | 🔄 |
| `setConfigFile(File)` | N/A (DB-backed) | 🔄 |
| `getConfigFile(): File` | N/A (DB-backed) | 🔄 |

**Missing properties on Kotlin Arena:**
- `chestLocations: List<SerializableLocation>` — Java tracks chest positions per-arena
- `lobbySpawn: SerializableLocation?` — Java has dedicated lobby spawn
- `spectatorSpawn: SerializableLocation?` — Java has dedicated spectator spawn
- `allowedBlocks: Set<Material>` — Java has configurable breakable blocks per-arena
- `id: UUID` — Java assigns a UUID per arena

### ArenaConfigurationHelper.java → NO EQUIVALENT
| Java Method | Status |
|---|---|
| `saveBasicProperties(section, maxPlayers, minPlayers, radius)` | 🔄 (DB-backed) |
| `saveLocation(parent, sectionName, location)` | 🔄 (DB-backed) |
| `saveLocationList(parent, sectionName, locations)` | 🔄 (DB-backed) |
| `saveAllowedBlocks(section, allowedBlocks)` | 🔄 (DB-backed) |
| `loadLocation(section): Location` | 🔄 (DB-backed) |

### ArenaManager.java → service/ArenaService.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `start()` | `loadAll()` | 🔄 |
| `stop()` | N/A (no shutdown method) | ❌ |
| `loadArenas(): CompletableFuture<Void>` | `loadAll(): suspend Unit` | ✅ |
| `saveArenas(): CompletableFuture<Void>` | N/A (saves individually) | 🔄 |
| `saveArenasImmediately(): CompletableFuture<Void>` | N/A | ❌ |
| `getArena(name): Arena` | `getArena(name): Arena?` | ✅ |
| `getArenas(): List<Arena>` | `getAvailableArenas(): List<Arena>` | ✅ |
| `addArena(Arena)` | `addToCache(Arena)` | ✅ |
| `removeArena(Arena)` | NOT PRESENT | ❌ |
| `getSelectedArena(Player): Arena` | NOT PRESENT | ❌ |
| `createArena(name, center, radius): Arena` | NOT PRESENT | ❌ |

### ArenaTemplate.java → NO EQUIVALENT (entire class missing)
All 25+ methods missing. Template system not ported.

---

## 2. CHEST PACKAGE

### ChestConfiguration.java → NO EQUIVALENT

| Java Method | Status |
|---|---|
| `loadItems(): List<ChestItem>` | ❌ (loot tables hardcoded) |
| `getItemConfig(String): ConfigurationSection` | ❌ |

### ChestItem.java → chest/LootEntry.kt (data class)

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| 6 constructors | Single data class constructor | 🔄 |
| `fromConfig(plugin, section, itemKey): ChestItem` | NOT PRESENT | ❌ |
| `getItemStack(plugin): ItemStack` | NOT PRESENT (material only) | ❌ |
| `getMaterial(): Material` | `material` property | ✅ |
| `getMinAmount(): int` | `minAmount` property | ✅ |
| `getMaxAmount(): int` | `maxAmount` property | ✅ |
| `getChance(): double` | `weight` property | ✅ |
| `isNexoItem(): boolean` | NOT PRESENT | ❌ |
| `getNexoItemId(): String` | NOT PRESENT | ❌ |
| `getTier(): String` | N/A (tier is on LootTable) | 🔄 |

### ChestManager.java → chest/ChestManager.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `start()` | N/A (@PostConstruct) | 🔄 |
| `stop()` | N/A (@PreDestroy) | 🔄 |
| `loadChestItems(): CompletableFuture<Void>` | NOT PRESENT (hardcoded) | ❌ |
| `fillChest(location, tier): boolean` | `fillAll(chests, tier): suspend Unit` | 🔄 |
| `fillChest(location): void` | NOT PRESENT | ❌ |
| `isChest(block): boolean` | NOT PRESENT | ❌ |
| `getChestItems(): List<ChestItem>` | NOT PRESENT | ❌ |
| `getTiers(): Set<String>` | NOT PRESENT | ❌ |
| `getTierItems(tier): List<ChestItem>` | NOT PRESENT | ❌ |
| `getRandomItem(tier): ItemStack` | NOT PRESENT | ❌ |
| `getRandomItems(tier, count): List<ItemStack>` | NOT PRESENT | ❌ |

---

## 3. GAME PACKAGE

### Game.java → game/Game.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `getArena(): Arena` | `arena` property | ✅ |
| `getGameId(): UUID` | `id` property | ✅ |
| `getState(): GameState` | `phase: GamePhase` | ✅ |
| `getGameMode(): GameMode` | `mode: GameMode` | ✅ |
| `getPlayers(): Set<UUID>` | `players: Map<UUID, GamePlayer>` | ✅ |
| `getSpectators(): Set<UUID>` | NOT EXPOSED | ❌ |
| `getDisconnectedPlayers(): Set<UUID>` | `disconnectedPlayers` | ✅ |
| `isPvpEnabled(): boolean` | NOT PRESENT | ❌ |
| `getCurrentState(): GameState` | `phase` property | ✅ |
| `getPlayerCount(): int` | `players.size` (no method) | ❌ |
| `getPlayerLocations(): Map<UUID, Location>` | NOT PRESENT | ❌ |
| `isShuttingDown(): boolean` | NOT PRESENT | ❌ |
| `addPlayer(Player)` | `addPlayer(Player)` | ✅ |
| `removePlayer(Player, boolean)` | `removePlayer(UUID)` | ✅ |
| `removePlayer(Player, boolean, boolean)` | N/A | ❌ |
| `addSpectator(Player)` | NOT PRESENT (handled internally) | ❌ |
| `eliminatePlayer(Player)` | `eliminate(UUID)` | ✅ |
| `startCountdown(int)` | Coroutine-based (no explicit method) | 🔄 |
| `startCountdown()` | Coroutine-based | 🔄 |
| `cancelCountdown()` | NOT PRESENT | ❌ |
| `skipGracePeriod()` | `skipGracePeriod()` | ✅ |
| `endGame(Object)` | Coroutine-based (GameEndSignal) | 🔄 |
| `broadcastMessage(Component)` | NOT PRESENT as public method | ❌ |
| `getTimeRemaining(): int` | NOT PRESENT | ❌ |
| `getPlayerKills(UUID): int` | `players[uuid]?.kills` | 🔄 |
| `recordDamageDealt(UUID, double)` | NOT PRESENT (tracked on GamePlayer) | ❌ |
| `recordDamageTaken(UUID, double)` | NOT PRESENT | ❌ |
| `recordChestOpened(UUID)` | NOT PRESENT | ❌ |
| `getPlayerDamageDealt(UUID): double` | NOT PRESENT | ❌ |
| `getPlayerDamageTaken(UUID): double` | NOT PRESENT | ❌ |
| `getPlayerChestsOpened(UUID): int` | NOT PRESENT | ❌ |
| `cleanup()` | Coroutine scope cancellation | 🔄 |
| `isBlockAllowed(Material): boolean` | NOT PRESENT (in PlayerListener) | ❌ |
| `trackPlacedBlock(Location)` | `worldManager.trackPlacedBlock()` | ✅ |
| `getDeathMessageManager()` | N/A (function-based) | 🔄 |
| `getEliminationManager()` | N/A | 🔄 |
| `getCelebrationManager()` | N/A | 🔄 |
| `getTeamManager()` | `teamManager` property | ✅ |
| `getWorldManager()` | N/A (private) | ❌ |
| `activateGame(GameMode)` | N/A (mode set at construction) | 🔄 |
| `isSetupComplete(): boolean` | NOT PRESENT | ❌ |
| `markSetupComplete()` | NOT PRESENT | ❌ |
| `clearSetupComplete()` | NOT PRESENT | ❌ |

### GameManager.java → game/GameManager.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `createGame(Arena): Game` | `createGame(Arena, GameMode): Game` | ✅ |
| `getGame(String): Game` | `getGame(UUID): Game?` | ✅ |
| `getActiveGames(): List<Game>` | `getAllActiveGames(): Collection<Game>` | ✅ |
| `findAvailableGame(Arena): Game` | NOT PRESENT | ❌ |
| `getAllGames(): List<Game>` | `getAllActiveGames()` | ✅ |
| `getGameByPlayer(Player): Game` | `getGameForPlayer(UUID): Game?` | ✅ |
| `findGamesByArena(Arena): List<Game>` | NOT PRESENT | ❌ |
| `getGameByArena(Arena): Game` | `getGameByArena(String): Game?` | ✅ |
| `removeGame(Game)` | `onGameEnd(UUID)` | ✅ |
| `getActiveGameCount(): int` | NOT PRESENT | ❌ |
| `getTotalGameCount(): int` | NOT PRESENT | ❌ |
| `isPlayerInGame(Player): boolean` | `getGameForPlayer() != null` | 🔄 |
| `getActiveGameCountInArena(Arena): int` | NOT PRESENT | ❌ |
| `hasActiveGames(Arena): boolean` | NOT PRESENT | ❌ |
| `shutdown()` | NOT PRESENT | ❌ |
| `getOrCreateGame(Arena): Game` | NOT PRESENT | ❌ |
| `getGames(): Collection<Game>` | `getAllActiveGames()` | ✅ |
| `cleanupOrphanedGames(): int` | NOT PRESENT | ❌ |

### GameBarrierManager.java → NO DIRECT EQUIVALENT (partially in WorldManager)

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `createBarrierBoxAroundLocation(Location)` | NOT PRESENT | ❌ |
| `removeAllBarriers()` | `WorldManager.removeBarriers()` | 🔄 |
| `removeBarriersAroundLocation(Location)` | NOT PRESENT | ❌ |
| `hasBarrier(Location): boolean` | NOT PRESENT | ❌ |
| `getBarrierCount(): int` | NOT PRESENT | ❌ |
| `cleanup()` | `WorldManager.cleanup()` | 🔄 |

### CelebrationManager.java → game/Celebration.kt (top-level suspend fun)

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `celebrateWinner(Player)` | `runCelebration(winnerUuid, ...)` | 🔄 |
| `celebrateWinner(Player, Component)` | `runCelebration(... deathMessage)` | 🔄 |
| `cleanup()` | N/A (suspend function scope) | 🔄 |

### GameChestManager.java → Handled by ChestListener + ChestManager

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `fillArenaChestsAsync(): CompletableFuture` | `ChestManager.fillAll()` | 🔄 |
| `isChestFilled(Location): boolean` | `ChestListener.filledChests` set | 🔄 |
| `getFilledChestCount(): int` | NOT PRESENT | ❌ |
| `cleanup()` | `ChestListener.clearFilledChests()` | 🔄 |
| `shutdownThreadPool()` | N/A (coroutines) | 🔄 |

### DeathMessageManager.java → game/DeathMessages.kt (top-level functions)

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `createDeathMessage(victim, killer): Component` | `deathMessage(victim, killer, config)` | ✅ |
| `createNaturalDeathMessage(victim): Component` | `deathMessage(victim, null, config)` | ✅ |
| `getWeaponType(weapon): String` | `weaponName(item)` | ✅ |
| `broadcastDeathMessage(Component)` | `Game.broadcastDeathMessage()` | 🔄 |
| `handlePlayerKill(victim, killer)` | NOT PRESENT as single method | ❌ |

### GameEliminationManager.java → NO DIRECT EQUIVALENT (inlined into Game.kt)

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `eliminatePlayer(Player): boolean` | `Game.eliminate(UUID)` | ✅ |
| `recordDamageDealt(UUID, double)` | NOT PRESENT | ❌ |
| `recordDamageTaken(UUID, double)` | NOT PRESENT | ❌ |
| `recordChestOpened(UUID)` | NOT PRESENT | ❌ |
| `getFinalRankings(): List<UUID>` | NOT PRESENT | ❌ |
| `recordFinalStatistics(long)` | NOT PRESENT | ❌ |
| `handlePlayerDeath(victim, killer)` | Handled in PlayerListener | 🔄 |
| `getPlayerDamageDealt(UUID): double` | `players[uuid]?.damageDealt` | 🔄 |
| `getPlayerDamageTaken(UUID): double` | `players[uuid]?.damageTaken` | 🔄 |
| `getPlayerChestsOpened(UUID): int` | `players[uuid]?.chestsOpened` | 🔄 |
| `getEliminationOrder(): List<UUID>` | NOT PRESENT | ❌ |
| `getStatisticsManager()` | N/A | 🔄 |
| `getLogger()` | N/A | 🔄 |

### GameMode.java (enum) → domain/GameMode.kt (sealed class)

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `getTeamSize(): int` | `teamSize` property | ✅ |
| `getDisplayName(): String` | `displayName` property | ✅ |
| `getDescription(): String` | NOT PRESENT | ❌ |
| `isTeamMode(): boolean` | NOT PRESENT | ❌ |
| `getMaxTeams(playerCount): int` | NOT PRESENT | ❌ |
| `getIdealPlayerCount(maxPlayers): int` | NOT PRESENT | ❌ |
| `fromDisplayName(name): GameMode` | NOT PRESENT | ❌ |

### GameState.java (enum) → domain/GamePhase.kt (sealed class)

| Java Values | Kotlin Equivalent | Status |
|---|---|---|
| INACTIVE | N/A | ❌ |
| WAITING | `Waiting` | ✅ |
| COUNTDOWN | `Countdown(seconds)` | ✅ |
| GRACE_PERIOD | `Grace(seconds)` | ✅ |
| ACTIVE | `Active(seconds)` | ✅ |
| DEATHMATCH | `Deathmatch(seconds)` | ✅ |
| FINISHED | `Ended(winner)` | ✅ |

### GameNameplateManager.java → game/NameplateManager.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `start()` | `start(players)` | ✅ |
| `addPlayer(Player)` | `addPlayer(UUID)` | ✅ |
| `removePlayer(Player)` | `removePlayer(UUID)` | ✅ |
| `disableNameplateHiding()` | NOT PRESENT | ❌ |
| `enableNameplateHiding()` | NOT PRESENT | ❌ |
| `cleanup()` | `stop()` | ✅ |
| `getDebugInfo(): Map` | NOT PRESENT | ❌ |

### GamePlayerManager.java → game/PlayerStateManager.kt + game/GamePlayer.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `addPlayer(Player, GameState): boolean` | `saveAndPrepare(Player, Location)` | 🔄 |
| `addSpectator(Player)` | `makeSpectator(Player)` | ✅ |
| `removePlayer(Player, boolean, boolean)` | `restore(Player)` | 🔄 |
| `eliminatePlayer(Player)` | `makeSpectator(Player)` | 🔄 |
| `getCachedPlayer(UUID): Player` | NOT PRESENT | ❌ |
| `cleanup()` | NOT PRESENT | ❌ |
| `getPlayers(): Set<UUID>` | `Game.players` | 🔄 |
| `getSpectators(): Set<UUID>` | NOT PRESENT | ❌ |
| `getDisconnectedPlayers(): Set<UUID>` | `Game.disconnectedPlayers` | 🔄 |
| `getPlayerCount(): int` | `Game.players.size` | 🔄 |
| `getPlayerKills(UUID): int` | `GamePlayer.kills` | 🔄 |
| `incrementKills(UUID)` | Mutable GamePlayer | 🔄 |
| `getPlayerLocations(): Map<UUID, Location>` | NOT PRESENT | ❌ |
| `getPlayerGameModes(): Map<UUID, GameMode>` | NOT PRESENT | ❌ |
| `getSerializedInventories(): Map<UUID, byte[]>` | Stored in PlayerStateManager | 🔄 |
| `getSerializedArmorContents(): Map<UUID, byte[]>` | Stored in PlayerStateManager | 🔄 |
| `getPlayerExperienceLevels(): Map<UUID, Integer>` | Stored in PlayerStateManager | 🔄 |
| `getPlayerExperiencePoints(): Map<UUID, Float>` | Stored in PlayerStateManager | 🔄 |
| `getPreviousLocations(): Map<UUID, Location>` | Stored in PlayerStateManager | 🔄 |
| `getPlayerFoodLevels(): Map<UUID, Integer>` | Stored in PlayerStateManager | 🔄 |
| `getPlayerSaturationLevels(): Map<UUID, Float>` | Stored in PlayerStateManager | 🔄 |
| `getPlayerPotionEffects(): Map<UUID, byte[]>` | Stored in PlayerStateManager | 🔄 |

### GameScoreboardManager.java → game/GameScoreboard.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `createNameplateTeam()` | N/A (handled internally) | 🔄 |
| `addPlayerToTeam(Player)` | `addPlayer(Player)` | ✅ |
| `removePlayerFromTeam(Player)` | `removePlayer(Player)` | ✅ |
| `forceScoreboardUpdate(Player)` | NOT PRESENT | ❌ |
| `removePlayerFromScoreboard(Player)` | `resetPlayer(Player)` | ✅ |
| `setCurrentGameState(GameState)` | NOT PRESENT (phase-aware internally) | ❌ |
| `cleanup()` | `resetAll()` + `stop()` | ✅ |
| `getGameScoreboard(): Scoreboard` | NOT PRESENT | ❌ |
| `getObjective(): Objective` | NOT PRESENT | ❌ |
| `getGameTeam(): Team` | NOT PRESENT | ❌ |

### GameStatisticsManager.java → NO DIRECT EQUIVALENT

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `recordDamageDealt(UUID, double)` | Tracked on GamePlayer | 🔄 |
| `recordDamageTaken(UUID, double)` | Tracked on GamePlayer | 🔄 |
| `recordChestOpened(UUID)` | Tracked on GamePlayer | 🔄 |
| `recordKill(UUID)` | Tracked on GamePlayer | 🔄 |
| `recordElimination(UUID)` | NOT PRESENT | ❌ |
| `getPlayerKills(UUID): int` | `GamePlayer.kills` | 🔄 |
| `getPlayerDamageDealt(UUID): double` | `GamePlayer.damageDealt` | 🔄 |
| `getPlayerDamageTaken(UUID): double` | `GamePlayer.damageTaken` | 🔄 |
| `getPlayerChestsOpened(UUID): int` | `GamePlayer.chestsOpened` | 🔄 |
| `recordGameStatistics(participants)` | `StatisticsService.recordGameEnd()` | 🔄 |
| `cleanup()` | N/A | 🔄 |

### GameTeamManager.java → game/TeamManager.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `setGameMode(GameMode)` | N/A (set at construction) | 🔄 |
| `getGameMode(): GameMode` | N/A (on Game) | 🔄 |
| `assignPlayerToTeam(Player): Team` | `assignToTeam(UUID): Team` | ✅ |
| `removePlayerFromTeam(Player): boolean` | `removeFromTeam(UUID)` | ✅ |
| `getPlayerTeam(Player): Team` | `getTeamForPlayer(UUID): Team?` | ✅ |
| `areTeammates(Player, Player): boolean` | NOT PRESENT | ❌ |
| `getAllTeams(): Collection<Team>` | `getAllTeams(): Collection<Team>` | ✅ |
| `getActiveTeams(): List<Team>` | `getAliveTeams(): List<Team>` | ✅ |
| `getActiveTeamCount(): int` | `getAliveTeams().size` | 🔄 |
| `eliminateTeam(Team)` | NOT PRESENT | ❌ |
| `applyTeamEffects(Team)` | `applyGlowingToTeammates()` | 🔄 |
| `removePlayerTeamEffects(Player)` | NOT PRESENT | ❌ |
| `refreshTeamEffects()` | NOT PRESENT | ❌ |
| `setGlowEffectsEnabled(boolean)` | NOT PRESENT | ❌ |
| `disbandAllTeams()` | NOT PRESENT | ❌ |
| `autoBalanceTeams()` | NOT PRESENT | ❌ |
| `getTeamStatistics(): Map` | NOT PRESENT | ❌ |
| `cleanup()` | NOT PRESENT | ❌ |
| `getTeams(): Collection<Team>` | `getAllTeams()` | ✅ |
| `createTeam(): Team` | `createTeam(): Team` | ✅ |

### GameTimerManager.java → NO EQUIVALENT (coroutine-based lifecycle)

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `setCurrentGameState(GameState)` | Coroutine flow | 🔄 |
| `startCountdown(seconds, onComplete)` | Coroutine delay loop | 🔄 |
| `startCountdown(onComplete)` | Coroutine delay loop | 🔄 |
| `cancelCountdown()` | Coroutine cancellation | 🔄 |
| `startGracePeriod(onComplete)` | Coroutine delay loop | 🔄 |
| `scheduleDeathmatch(onDeathmatch, onGameEnd)` | Coroutine delay loop | 🔄 |
| `scheduleDeathmatchEnd(onGameEnd)` | Coroutine delay loop | 🔄 |
| `getTimeRemaining(): int` | NOT EXPOSED | ❌ |
| `resetStartTime()` | NOT PRESENT | ❌ |
| `markGameEndedEarly()` | GameEndSignal exception | 🔄 |
| `cleanup()` | Scope cancellation | 🔄 |
| `getCountdown(): int` | From config | 🔄 |
| `getGameTime(): int` | From config | 🔄 |
| `getGracePeriod(): int` | From config | 🔄 |
| `getDeathmatchTime(): int` | From config | 🔄 |
| `getStartTime(): long` | NOT PRESENT | ❌ |

### GameWorldManager.java → game/WorldManager.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `setupWorld()` | `setup()` | ✅ |
| `removeSpawnBarriers()` | `removeBarriers()` | ✅ |
| `setupDeathmatchBorder()` | `setupDeathmatch()` | ✅ |
| `setCelebrationBorder()` | `resetBorderForCelebration()` | ✅ |
| `restoreWorld()` | `cleanup()` | ✅ |
| `isBlockAllowed(Material): boolean` | NOT PRESENT | ❌ |
| `trackPlacedBlock(Location)` | `trackPlacedBlock(Location)` | ✅ |
| `removeAllPlacedBlocks()` | In `cleanup()` | 🔄 |
| `clearAllDrops()` | NOT PRESENT | ❌ |
| `trackBarrierBlock(Location)` | NOT PRESENT | ❌ |
| `untrackBarrierBlock(Location)` | NOT PRESENT | ❌ |
| `removeAllBarrierBlocks()` | In `cleanup()` | 🔄 |
| `cleanup()` | `cleanup()` | ✅ |
| `getPlacedBlocks(): Set<Location>` | NOT PRESENT | ❌ |
| `getOriginalDifficulty(): Difficulty` | NOT PRESENT | ❌ |
| `getOriginalTime(): long` | NOT PRESENT | ❌ |
| `getBarrierManager()` | N/A | ❌ |
| `getChestManager()` | N/A | ❌ |

### Team.java → game/Team.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `Team(int teamNumber)` | `Team(id, members, maxSize, inviteOnly)` | ✅ |
| `addMember(Player): boolean` | `add(UUID): Boolean` | ✅ |
| `removeMember(UUID): boolean` | `remove(UUID)` | ✅ |
| `isMember(UUID): boolean` | `contains(UUID): Boolean` | ✅ |
| `getMembers(): Set<UUID>` | `members` property | ✅ |
| `getOnlineMembers(): List<Player>` | NOT PRESENT | ❌ |
| `getCachedMember(UUID): Player` | NOT PRESENT | ❌ |
| `getSize(): int` | `members.size` | 🔄 |
| `getMemberCount(): int` | `members.size` | 🔄 |
| `getOnlineSize(): int` | NOT PRESENT | ❌ |
| `isInviteOnly(): boolean` | `inviteOnly` property | ✅ |
| `isEmpty(): boolean` | `members.isEmpty()` | 🔄 |
| `isFull(GameMode): boolean` | `isFull` property | ✅ |
| `hasOnlineMembers(): boolean` | NOT PRESENT | ❌ |
| `getTeamId(): UUID` | `id: Int` (different type) | 🔄 |
| `getTeamNumber(): int` | `id: Int` | ✅ |
| `isEliminated(): boolean` | NOT PRESENT | ❌ |
| `eliminate()` | NOT PRESENT | ❌ |
| `getCreatedAt(): long` | NOT PRESENT | ❌ |
| `getDisplayName(): String` | NOT PRESENT | ❌ |
| `getMemberNames(): List<String>` | NOT PRESENT | ❌ |
| `getMemberByName(String): UUID` | NOT PRESENT | ❌ |
| `getLeader(): UUID` | `leader: UUID?` property | ✅ |
| `getDisplayNumber(): int` | NOT PRESENT | ❌ |
| `updateCache()` | NOT PRESENT | ❌ |
| `cleanup()` | NOT PRESENT | ❌ |

### TeamInvitation.java → NO DIRECT EQUIVALENT (inlined into TeamManager/TeamQueueManager)

| Java Method | Status |
|---|---|
| `getInviter(): UUID` | ❌ (no TeamInvitation class) |
| `getInvitee(): UUID` | ❌ |
| `getTeam(): Team` | ❌ |
| `getGame(): Game` | ❌ |
| `getCreatedAt(): Instant` | ❌ |
| `getExpiresAt(): Instant` | ❌ |
| `isExpired(): boolean` | ❌ |
| `isResponded(): boolean` | ❌ |
| `markResponded()` | ❌ |
| `isValid(): boolean` | ❌ |

### TeamQueueManager.java → game/TeamQueueManager.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `sendInvitation(inviter, invitee, team, game): boolean` | `invite(inviter, invitee): Boolean` | 🔄 |
| `acceptInvitation(Player): boolean` | `accept(Player): Boolean` | ✅ |
| `declineInvitation(Player): boolean` | `decline(Player): Boolean` | ✅ |
| `leaveTeam(Player): boolean` | NOT PRESENT | ❌ |
| `getPlayerTeam(UUID): Team` | `getPreGameTeam(UUID): Team?` | ✅ |
| `getPlayerTeam(Player): Team` | `getPreGameTeam(player.uniqueId)` | ✅ |
| `updateGameBroadcast(Game)` | In `start()` coroutine | 🔄 |
| `stopGameBroadcast(Game)` | `stop()` | 🔄 |
| `toggleMute(Player): boolean` | `toggleMute(Player)` | ✅ |
| `cleanupPlayer(Player)` | `removeFromQueue(UUID)` | ✅ |
| `shutdown()` | `stop()` | ✅ |
| `createTeam(player, game, inviteOnly, autoFill): Team` | NOT PRESENT (simplified) | ❌ |
| `joinTeam(player, team): boolean` | NOT PRESENT | ❌ |
| `hasInvitation(UUID, team): boolean` | NOT PRESENT | ❌ |
| `removeInvitation(UUID)` | NOT PRESENT | ❌ |
| `startSetupPeriod(game, setupTimeSeconds)` | NOT PRESENT | ❌ |

### PlayerGameStats.java → game/GamePlayer.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| Record(kills, damageDealt, damageTaken, chestsOpened) | Data class with more fields | ✅ |

---

## 4. GUI PACKAGE

### MainMenu.java → NO EQUIVALENT
| Java Method | Status |
|---|---|
| `openMenu(Player)` | ❌ |

### GameSetupMenu.java → NO EQUIVALENT (partially SetupMenu.kt)
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `openMenu(Player)` | `SetupMenu.open(Player)` | 🔄 |
| `openMapSelection(Player)` | NOT PRESENT | ❌ |
| `onMapSelected(Player, Arena)` | NOT PRESENT | ❌ |
| `cleanupPlayer(UUID)` | NOT PRESENT | ❌ |

### MapSelectionMenu.java → gui/ArenaSelectionMenu.kt
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `openMenu(Player, config)` | `open(Player)` | 🔄 |

### MenuUtils.java → NO EQUIVALENT
| Java Method | Status |
|---|---|
| `initialize(plugin)` | ❌ |
| `getPlugin(): LumaSG` | ❌ |
| `createItem(Material, String, List<String>): ItemStack` | ❌ |
| `createItem(Material, String): ItemStack` | ❌ |
| `createBorderItem(): ItemStack` | ❌ |
| `createBackButton(): ItemStack` | ❌ |
| `createNextPageButton(): ItemStack` | ❌ |
| `createPrevPageButton(): ItemStack` | ❌ |
| `fillEmptySlots(Inventory, ItemStack)` | ❌ |
| `fillEmptySlots(Inventory)` | ❌ |

### GameBrowserMenu.java → gui/GameBrowserMenu.kt
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `openMenu(Player)` | `open(Player)` | ✅ |

### LeaderboardMenu.java → gui/LeaderboardMenu.kt
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `openMenu(Player)` | `open(Player)` | ✅ |
| `openLeaderboardTab(Player, StatType)` | NOT PRESENT | ❌ |

### SetupMenu.java → gui/SetupMenu.kt
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `openMenu(Player)` | `open(Player)` | ✅ |

### TeamSelectionMenu.java → gui/TeamSelectionMenu.kt
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `openMenu(Player, Game)` | `open(Player, List<Team>)` | 🔄 |

---

## 5. HOOKS PACKAGE

### HookManager.java → NO EQUIVALENT
| Java Method | Status |
|---|---|
| `start()` | ❌ |
| `stop()` | ❌ |
| `getNexoHook(): NexoHook` | ❌ |
| `getKingdomsXHook(): KingdomsXHook` | ❌ |
| `isHookAvailable(String): boolean` | ❌ |
| `isPlayerInActivePvPGame(Player): boolean` | ❌ |
| `arePlayersInSamePvPGame(Player, Player): boolean` | ❌ |

### PluginHook.java (interface) → hooks/PluginHook.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `getPluginName(): String` | `pluginName` property | ✅ |
| `isAvailable(): boolean` | `isAvailable(): Boolean` | ✅ |
| `getPlugin(): Plugin` | NOT PRESENT | ❌ |
| `initialize(): boolean` | NOT PRESENT | ❌ |
| `enable(): boolean` | `register()` | 🔄 |
| `disable()` | NOT PRESENT | ❌ |

### KingdomsXHook.java → hooks/KingdomsXHook.kt (+ LumaGuildsHook.kt replaces)
Present but being replaced by LumaGuildsHook per user directive.

### NexoHook.java → hooks/NexoHook.kt
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| All PluginHook interface methods | See above | See above |
| `getNexoItem(String): Optional<ItemStack>` | NOT PRESENT | ❌ |

### PlaceholderAPIHook.java → hooks/PlaceholderAPIHook.kt
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `getIdentifier(): String` | NOT CHECKED | ❓ |
| `getAuthor(): String` | NOT CHECKED | ❓ |
| `getVersion(): String` | NOT CHECKED | ❓ |
| `persist(): boolean` | NOT CHECKED | ❓ |
| `onPlaceholderRequest(Player, String): String` | NOT CHECKED | ❓ |

---

## 6. LISTENERS PACKAGE

All 5 listeners exist in both. Key method-level gaps:

### AdminWandListener.java → listeners/AdminWandListener.kt
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `onPlayerInteract(PlayerInteractEvent)` | `onInteract(PlayerInteractEvent)` | ✅ |
| `onPlayerItemHeld(PlayerItemHeldEvent)` | NOT PRESENT | ❌ |
| `onPlayerDropItem(PlayerDropItemEvent)` | NOT PRESENT | ❌ |
| `onInventoryClick(InventoryClickEvent)` | NOT PRESENT | ❌ |
| `onPlayerSwapHandItems(PlayerSwapHandItemsEvent)` | NOT PRESENT | ❌ |
| `onPlayerQuit(PlayerQuitEvent)` | NOT PRESENT | ❌ |
| `setSelectedArena(Player, Arena)` | NOT PRESENT | ❌ |
| `getSelectedArena(Player): Arena` | NOT PRESENT | ❌ |
| `onWandGiven(Player)` | NOT PRESENT | ❌ |
| `giveWand(Player)` | NOT PRESENT | ❌ |
| `getAdminWand(): AdminWand` | NOT PRESENT | ❌ |

### ChestListener.java → listeners/ChestListener.kt
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `onChestOpen(InventoryOpenEvent)` | `onChestOpen(InventoryOpenEvent)` | ✅ |
| `onChestBreak(BlockBreakEvent)` | `onChestBreak(BlockBreakEvent)` | ✅ |
| `onChestPlace(BlockPlaceEvent)` | `onChestPlace(BlockPlaceEvent)` | ✅ |
| `onInventoryClick(InventoryClickEvent)` | NOT PRESENT | ❌ |
| `clearOpenedChests()` | `clearFilledChests()` | ✅ |

### CustomItemListener.java → listeners/CustomItemListener.kt
✅ Well-covered (fire bomb, bomb, poison bomb, debris handling)

### FishingListener.java → listeners/FishingListener.kt
✅ Present

### PlayerListener.java → listeners/PlayerListener.kt
| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `onPlayerJoin` | ✅ | ✅ |
| `onPlayerQuit` | ✅ | ✅ |
| `onPlayerDeath` | ✅ | ✅ |
| `onPlayerRespawn` | ✅ | ✅ |
| `onEntityDamage` | ✅ | ✅ |
| `onEntityDamageByEntity` | ✅ | ✅ |
| `onPlayerTeleport` | ✅ | ✅ |
| `onBlockPlace` | ✅ | ✅ |
| `onEntityExplode` | ✅ | ✅ |
| `onBlockBreak` | ✅ (added in Kotlin) | ✅ |

---

## 7. STATISTICS PACKAGE

### PlayerStats.java → domain/PlayerStats.kt (data class)

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| Full constructor (18 params) | 9-param data class | 🔄 |
| `getPlayerId(): UUID` | `uuid` property | ✅ |
| `getPlayerName(): String` | `playerName` property | ✅ |
| `getWins(): int` | `wins` property | ✅ |
| `getLosses(): int` | NOT PRESENT | ❌ |
| `getKills(): int` | `kills` property | ✅ |
| `getDeaths(): int` | `deaths` property | ✅ |
| `getGamesPlayed(): int` | `gamesPlayed` property | ✅ |
| `getTotalTimePlayed(): long` | NOT PRESENT | ❌ |
| `getBestPlacement(): int` | NOT PRESENT | ❌ |
| `getCurrentWinStreak(): int` | NOT PRESENT | ❌ |
| `getBestWinStreak(): int` | NOT PRESENT | ❌ |
| `getTop3Finishes(): int` | NOT PRESENT | ❌ |
| `getTotalDamageDealt(): double` | `damageDealt` property | ✅ |
| `getTotalDamageTaken(): double` | `damageTaken` property | ✅ |
| `getChestsOpened(): int` | NOT PRESENT | ❌ |
| `getFirstJoined(): LocalDateTime` | `createdAt: Instant` | ✅ |
| `getLastPlayed(): LocalDateTime` | NOT PRESENT | ❌ |
| `getLastUpdated(): LocalDateTime` | NOT PRESENT | ❌ |
| `getKillDeathRatio(): double` | `kdr` computed property | ✅ |
| `getWinRate(): double` | `winRate` computed property | ✅ |
| `getTop3Rate(): double` | NOT PRESENT | ❌ |
| `getAverageKillsPerGame(): double` | NOT PRESENT | ❌ |
| `getAverageGameTime(): double` | NOT PRESENT | ❌ |
| All 14 setters | Immutable data class (copy()) | 🔄 |
| `incrementWins()` | N/A (copy()) | 🔄 |
| `incrementLosses()` | N/A | 🔄 |
| `incrementKills()` | N/A | 🔄 |
| `incrementDeaths()` | N/A | 🔄 |
| `incrementGamesPlayed()` | N/A | 🔄 |
| `addTimePlayed(long)` | NOT PRESENT | ❌ |
| `incrementChestsOpened()` | NOT PRESENT | ❌ |
| `addDamageDealt(double)` | N/A | 🔄 |
| `addDamageTaken(double)` | N/A | 🔄 |
| `updatePlacement(int)` | NOT PRESENT | ❌ |

**Missing PlayerStats fields in Kotlin:**
- `losses: Int`
- `totalTimePlayed: Long`
- `bestPlacement: Int`
- `currentWinStreak: Int`
- `bestWinStreak: Int`
- `top3Finishes: Int`
- `chestsOpened: Int`
- `lastPlayed: Instant`
- `lastUpdated: Instant`

### StatType.java → NO EQUIVALENT
| Java Enum Value | Status |
|---|---|
| WINS | ❌ |
| KILLS | ❌ |
| GAMES_PLAYED | ❌ |
| KILL_DEATH_RATIO | ❌ |
| WIN_RATE | ❌ |
| TIME_PLAYED | ❌ |
| BEST_PLACEMENT | ❌ |
| WIN_STREAK | ❌ |
| TOP3_FINISHES | ❌ |
| DAMAGE_DEALT | ❌ |
| CHESTS_OPENED | ❌ |

### StatisticsDatabase.java → persistence/repositories/PlayerStatsRepository.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `initialize(): CompletableFuture` | Database auto-created by Exposed | 🔄 |
| `savePlayerStats(stats): CompletableFuture` | `upsert(stats)` | ✅ |
| `loadPlayerStats(UUID): CompletableFuture` | `findByUuid(UUID)` | ✅ |
| `getLeaderboard(StatType, limit): CompletableFuture` | `getLeaderboard(limit)` (no StatType) | 🔄 |
| `getTotalPlayerCount(): CompletableFuture` | NOT PRESENT | ❌ |
| `savePlayerStatsBatch(List): CompletableFuture` | NOT PRESENT | ❌ |
| `shutdown()` | `DatabaseService.shutdown()` | ✅ |

### StatisticsManager.java → statistics/StatisticsService.kt

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `initialize(): CompletableFuture` | N/A (@PostConstruct) | 🔄 |
| `isHealthy(): boolean` | NOT PRESENT | ❌ |
| `shutdown(): CompletableFuture` | N/A (@PreDestroy) | 🔄 |
| `getPlayerStats(UUID, name): CompletableFuture` | `getOrCreate(UUID, name)` | ✅ |
| `getCachedPlayerStats(UUID): PlayerStats` | NOT PRESENT | ❌ |
| `recordGameResult(UUID, placement, kills, dmgDealt, dmgTaken, chests, time)` | `recordGameEnd(game, winner)` | 🔄 |
| `recordDeath(UUID)` | NOT PRESENT | ❌ |
| `recordKill(UUID)` | `recordKill(UUID)` | ✅ |
| `recordDamageDealt(UUID, double)` | NOT PRESENT | ❌ |
| `recordDamageTaken(UUID, double)` | NOT PRESENT | ❌ |
| `recordChestOpened(UUID)` | NOT PRESENT | ❌ |
| `getLeaderboard(StatType, limit): CompletableFuture` | `getLeaderboard(limit)` (no StatType filter) | 🔄 |
| `getTotalPlayerCount(): CompletableFuture` | NOT PRESENT | ❌ |
| `savePlayerStats(UUID): CompletableFuture` | NOT PRESENT | ❌ |
| `saveAllPendingStats(): CompletableFuture` | NOT PRESENT | ❌ |
| `uncachePlayer(UUID)` | NOT PRESENT | ❌ |
| `preloadPlayerStats(Player)` | NOT PRESENT | ❌ |
| `getDatabase(): StatisticsDatabase` | NOT PRESENT | ❌ |

---

## 8. CUSTOM ITEMS PACKAGE

### CustomItem.java → items/CustomItem.kt (interface)

| Java Method | Kotlin Equivalent | Status |
|---|---|---|
| `fromConfig(plugin, section, key): CustomItem` | N/A (hardcoded items) | 🔄 |
| `createItemStack(): ItemStack` | `createStack(): ItemStack` | ✅ |
| `createItemStack(amount): ItemStack` | NOT PRESENT | ❌ |
| `getId(): String` | `key: NamespacedKey` | ✅ |
| `getBehaviorType(): CustomItemBehavior` | N/A (each item is its own behavior) | 🔄 |
| `getBehaviorConfig(): ConfigurationSection` | N/A | 🔄 |
| `getLootSettings(): LootSettings` | NOT PRESENT | ❌ |
| `getCategory(): String` | NOT PRESENT | ❌ |
| `hasBehavior(behavior): boolean` | N/A (is-check on type) | 🔄 |
| `getBehaviorInt/Double/Boolean/String()` | N/A | 🔄 |

### CustomItem.LootSettings (inner class) → NO EQUIVALENT
| Java Method | Status |
|---|---|
| `fromConfig(section)` | ❌ |
| `getTiers(): Set<String>` | ❌ |
| `getChanceForTier(tier): double` | ❌ |
| `getMinAmount(): int` | ❌ |
| `getMaxAmount(): int` | ❌ |
| `canAppearInTier(tier): boolean` | ❌ |

### CustomItemBehavior.java (enum) → NO EQUIVALENT
| Java Method | Status |
|---|---|
| `fromString(String): CustomItemBehavior` | ❌ |
| `requiresSpecialHandling(): boolean` | ❌ |
| `isThrowable(): boolean` | ❌ |
| `isTracker(): boolean` | ❌ |
| `isCombat(): boolean` | ❌ |
| `isUtility(): boolean` | ❌ |

### CustomItemsManager.java → NO EQUIVALENT
| Java Method | Status |
|---|---|
| `initialize(): boolean` | ❌ |
| `getCustomItem(id): CustomItem` | ❌ |
| `getAllCustomItems(): Collection` | ❌ |
| `getCustomItemsByCategory(cat): Collection` | ❌ |
| `getCustomItemsByBehavior(behavior): Collection` | ❌ |
| `createCustomItemStack(id): ItemStack` | ❌ |
| `createCustomItemStack(id, amount): ItemStack` | ❌ |
| `isCustomItem(ItemStack): boolean` | `CustomItem.fromStack()` companion | 🔄 |
| `getCustomItemId(ItemStack): String` | N/A | ❌ |
| `getCustomItemFromStack(ItemStack): CustomItem` | `CustomItem.fromStack()` | ✅ |
| `getChestItemsForTier(tier): List` | NOT PRESENT | ❌ |
| `reload(): boolean` | NOT PRESENT | ❌ |
| `isEnabled(): boolean` | NOT PRESENT | ❌ |
| `isCleanupOnGameEnd(): boolean` | NOT PRESENT | ❌ |
| `isTrackUsage(): boolean` | NOT PRESENT | ❌ |
| `getMaxItemsPerGame(): int` | NOT PRESENT | ❌ |
| `getCleanupInterval(): int` | NOT PRESENT | ❌ |
| `isAsyncProcessing(): boolean` | NOT PRESENT | ❌ |

---

## 9. EXCEPTION PACKAGE → NO EQUIVALENT

### LumaSGException.java → NOT PRESENT
| Class/Method | Status |
|---|---|
| `LumaSGException(message)` | ❌ |
| `LumaSGException(message, cause)` | ❌ |
| `arenaError(message): ArenaException` | ❌ |
| `chestError(message, location): ChestException` | ❌ |
| `gameError(message): GameException` | ❌ |
| `configError(message): ConfigurationException` | ❌ |
| `configurationError(message, context)` | ❌ |
| `playerError(message): PlayerException` | ❌ |
| `validationError(message): ValidationException` | ❌ |
| `databaseError(message): DatabaseException` | ❌ |
| All 7 inner exception classes | ❌ |

---

## 10. PERMISSIONS PACKAGE → NO EQUIVALENT

### RankPermissions.java → NOT PRESENT
| Java Method | Status |
|---|---|
| `canSetupGames(Player): boolean` | ❌ |
| `canInvitePlayers(Player): boolean` | ❌ |
| `canJoinTeams(Player): boolean` | ❌ |
| `canCreateTeams(Player): boolean` | ❌ |
| `canConfigureTeams(Player): boolean` | ❌ |
| `canSelectMaps(Player): boolean` | ❌ |
| `canManageQueues(Player): boolean` | ❌ |
| `canBypassLimits(Player): boolean` | ❌ |
| `canForceStart(Player): boolean` | ❌ |
| `hasAdminAccess(Player): boolean` | ❌ |
| `getPermissionLevel(Player): String` | ❌ |

---

## 11. UTIL PACKAGE → MOSTLY NOT PORTED

### AdminWand.java → NOT PRESENT (partially in AdminWandListener)
### MessageUtils.java → NOT PRESENT (MiniMessage used directly)
### MiniMessageUtils.java → NOT PRESENT (MiniMessage used directly)
### ItemUtils.java → NOT PRESENT
### InventoryUtils.java → NOT PRESENT
### InvitationManager.java → Partially in TeamQueueManager
### ConfigurationManager.java → Replaced by LumaSGConfig.kt
### DebugLogger.java → NOT PRESENT (SLF4J used directly)
### ErrorHandlingUtils.java → NOT PRESENT
### CacheManager.java → NOT PRESENT
### ArenaWorldCache.java → NOT PRESENT
### ConcurrentChestFiller.java → Replaced by coroutine-based filling
### GameInstancePool.java → NOT PRESENT
### GuiComponentCache.java → NOT PRESENT
### LootTableCache.java → NOT PRESENT
### OptimizedConfigLoader.java → NOT PRESENT
### PerformanceProfiler.java → NOT PRESENT

---

## SUMMARY: CRITICAL MISSING FEATURES

### Must-Have (affects gameplay parity):
1. **Arena properties:** chestLocations, lobbySpawn, spectatorSpawn, allowedBlocks
2. **PlayerStats fields:** losses, totalTimePlayed, bestPlacement, winStreak, bestWinStreak, top3Finishes, chestsOpened, lastPlayed
3. **StatType enum** — needed for leaderboard filtering
4. **Game.getTimeRemaining()** — needed for scoreboard/UI
5. **Game.broadcastMessage()** — public broadcast method
6. **Game.getSpectators()** — spectator tracking
7. **Game.isPvpEnabled()** — PvP state check
8. **GameManager convenience methods:** findAvailableGame, isPlayerInGame, shutdown, cleanupOrphanedGames
9. **TeamManager.areTeammates()** — critical for PvP logic
10. **TeamManager.disbandAllTeams()** — cleanup
11. **TeamManager.eliminateTeam()** — team elimination
12. **Team properties:** isEliminated, eliminate(), getOnlineMembers, displayName, createdAt
13. **TeamInvitation class** — proper invitation tracking
14. **NameplateManager.disableNameplateHiding()/enableNameplateHiding()** — celebration phase
15. **WorldManager.clearAllDrops()** — world cleanup
16. **WorldManager.isBlockAllowed()** — block restriction
17. **AdminWandListener** — most event handlers missing
18. **ChestListener.onInventoryClick()** — prevent item theft
19. **RankPermissions** — entire permission system
20. **HookManager** — hook registry and convenience methods
21. **NexoHook.getNexoItem()** — Nexo item lookup
22. **CustomItemsManager** — item registry
23. **MenuUtils** — GUI utilities
24. **MainMenu** — main menu entry point
25. **LeaderboardMenu.openLeaderboardTab(StatType)** — filtered leaderboard
26. **GameMode helper methods:** isTeamMode, fromDisplayName, getDescription

### Nice-to-Have (can omit or simplify):
1. ArenaTemplate system (not commonly used)
2. ArenaConfigurationHelper (DB-backed now)
3. Most util classes (replaced by Kotlin idioms)
4. Exception hierarchy (Kotlin uses Result/null)
5. GameBarrierManager as separate class
6. Performance profiler
7. Cache managers (Kotlin coroutines handle differently)
