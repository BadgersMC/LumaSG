# Loot Mode Tiers: Classic, Modern, OP

## Overview

LumaSG gains three loot mode tiers that control the caliber of gear available in a game. Each mode acts as a weight multiplier layer on top of the existing distance-based chest tier system (common/uncommon/rare/epic/legendary). Modes also support optional game timing overrides and per-mode statistical tracking.

**Modes:**
- **Classic** — Modernized 1.8 era. Leather, copper, iron/chainmail as high tier. Very low diamond chance. No airstrikes, smokes, feathers, firebombs. TNT, bombs, player trackers, and supply drop flares remain.
- **Modern** — Balanced middle ground with all features. Not too much diamond early, but by endgame expect half-diamond or full. Smokes and feathers are common. Airstrikes are uncommon. No maces or netherite.
- **OP** — Nothing below iron drops. Endgame sees half-netherite/half-diamond armor. Pearls, golden apples, splash potions, maces, airstrikes, and all custom items are in play.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Mode's relationship to distance tiers | Multiplier layer on top | Preserves risk/reward of running to center; mode controls caliber of loot |
| Stat tracking | Full per-mode separation | Fair leaderboards; global stats derived by summing |
| Timing overrides | Optional per-mode with global fallback | Tune each mode's feel without duplicating entire config |
| New 1.21.1 items | Regular items in chest.yml | Vanilla items don't need custom behavior |
| Config approach | Per-item mode-weights in chest.yml | Single source of truth, granular control, no config duplication |

## Section 1: Loot Mode Enum & Weight Resolution

### LootMode Enum

New enum in `domain/` package:

```kotlin
enum class LootMode {
    CLASSIC, MODERN, OP
}
```

### chest.yml Item Format

Every item gains an optional `mode-weights` block alongside its existing `tier-weights`:

```yaml
diamond_sword:
  material: DIAMOND_SWORD
  min-amount: 1
  max-amount: 1
  tier-weights:
    common: 0.5
    uncommon: 2.0
    rare: 5.0
    epic: 8.0
    legendary: 10.0
  mode-weights:
    classic: 0.05
    modern: 0.6
    op: 2.0
```

If `mode-weights` is omitted for an item, all three modes default to `1.0` (item appears normally in every mode). A value of `0.0` excludes the item from that mode entirely.

### custom-items.yml

Same pattern — each custom item's `loot` section gains `mode-weights` alongside its existing `tier-weights`.

### Weight Resolution

At loot generation time:

```
effective_weight = tier_weight × mode_weight
```

If `effective_weight <= 0`, the item is excluded from the candidate pool for that roll.

### ChestItem Changes

`ChestItem` gains a field:

```kotlin
val modeWeights: Map<LootMode, Double>  // defaults to {CLASSIC: 1.0, MODERN: 1.0, OP: 1.0}
```

**Loading note:** The existing `ChestManager.loadSingleItem()` explodes each YAML item entry into multiple `ChestItem` instances — one per tier — each with a flat `chance` field set to that tier's weight. The `mode-weights` block is parsed once during `loadSingleItem()` and the resulting `Map<LootMode, Double>` is propagated into every exploded `ChestItem` for that item via `createEnrichedSection()`. This means each `ChestItem` carries both its tier-specific `chance` and the shared `modeWeights` map.

### ChestManager Changes

`weightedRandomItem()` and `fillChest()` accept a `LootMode` parameter. Before rolling, the loot list is filtered/reweighted:

```kotlin
fun fillChest(inventory: Inventory, tier: String, mode: LootMode) {
    val candidates = lootByTier[tier]?.filter { item ->
        (item.modeWeights[mode] ?: 1.0) > 0.0
    } ?: return
    // weighted selection uses: item.chance * (item.modeWeights[mode] ?: 1.0)
}
```

### LootTableCache Changes

Pre-generates 50 chests per tier **per mode** (5 tiers × 3 modes = 15 cache buckets instead of 5). Cache key changes from `tier: String` to `Pair<String, LootMode>`.

### ConcurrentChestFiller Changes

`fillChestFromCache()` and `fillChestSync()` accept a `LootMode` parameter, passed through to the cache and fallback path.

## Section 2: Mode Definitions & Game Flow

### config.yaml Modes Section

New `ModesSection` in `LumaSGConfig`:

```yaml
modes:
  default-mode: modern
  classic:
    display-name: "<gold>Classic"
    description: "Modernized 1.8-era survival games"
    timing-overrides:
      grace-period: 30
  modern:
    display-name: "<green>Modern"
    description: "Balanced gameplay with all features"
  op:
    display-name: "<red>OP"
    description: "Overpowered loot, chaotic battles"
    timing-overrides:
      deathmatch-duration: 180
```

Timing overrides are optional maps. Any key not present falls back to the global timing config.

### Data Class

```kotlin
data class ModesSection(
    var defaultMode: String = "modern",
    var classic: ModeConfig = ModeConfig(
        displayName = "<gold>Classic",
        description = "Modernized 1.8-era survival games"
    ),
    var modern: ModeConfig = ModeConfig(
        displayName = "<green>Modern",
        description = "Balanced gameplay with all features"
    ),
    var op: ModeConfig = ModeConfig(
        displayName = "<red>OP",
        description = "Overpowered loot, chaotic battles"
    )
)

data class ModeConfig(
    var displayName: String = "",
    var description: String = "",
    var timingOverrides: Map<String, Int> = emptyMap()
)

// Helper to resolve mode config by enum
fun ModesSection.forMode(mode: LootMode): ModeConfig = when (mode) {
    LootMode.CLASSIC -> classic
    LootMode.MODERN -> modern
    LootMode.OP -> op
}
```

Valid `timingOverrides` keys: `grace-period`, `game-duration`, `deathmatch-duration`, `countdown-time`, `chest-refill-delay`, `border-shrink-speed`. Any key not present falls back to the corresponding global config value.

### Command Change

`/sg start <arena> [mode]`:
- `mode` is an optional argument, tab-completes to `classic`, `modern`, `op`
- Defaults to `config.modes.defaultMode` if omitted
- Invalid mode names show an error with valid options

### Game Changes

`Game` gains a `lootMode: LootMode` property, set at creation via `GameManager.createGame(arena, gameMode, lootMode)`.

**Timing resolution:**

```kotlin
val gracePeriod = config.modes.forMode(lootMode).timingOverrides["grace-period"]
    ?: config.game.gracePeriod
```

Applied to: grace period, game duration, deathmatch duration, countdown time, chest refill delay, border shrink speed.

### ChestListener Changes

`ChestListener` reads `game.lootMode` and passes it to `ConcurrentChestFiller`/`ChestManager` when filling chests. The existing `determineTier()` (distance-based) remains unchanged — it determines the tier, then the mode multiplier is applied on top.

### Scoreboard/HUD

Mode display name shown on the sidebar (e.g., `Mode: Classic`).

### Discord Integration

Game-start Discord announcements include the mode name.

## Section 3: Per-Mode Statistics

### Storage Schema

Stats are keyed by `(playerUUID, lootMode)` instead of just `playerUUID`. **All existing stats** are tracked per mode — wins, kills, deaths, games played, damage dealt, damage taken, chests opened, best placement, win streaks, top-3 finishes, etc. The full `PlayerStats` field set is preserved; the only change is adding the mode dimension.

**Schema change:** Add a `loot_mode` VARCHAR column to `PlayerStatsTable` (Exposed ORM). The primary key becomes a composite of `(player_uuid, loot_mode)`. Each player gets up to 3 rows (one per mode) instead of 1.

### Migration

Existing rows in `PlayerStatsTable` get their `loot_mode` column set to `"MODERN"` (since all games before this feature were effectively Modern-tier). This is a single `ALTER TABLE ADD COLUMN ... DEFAULT 'MODERN'` followed by updating the primary key constraint.

### Global Stats

Derived by summing across all three modes at query time. No separate "global" row stored. `PlayerStatsRepository` methods gain an optional `lootMode: LootMode?` parameter — `null` means aggregate across all modes.

### GamePlayer

`GamePlayer` gains awareness of which mode the game is running so stats persist to the correct bucket on game end. This is read from the parent `Game.lootMode`.

### Commands

- `/sg stats [player] [mode]` — shows per-mode stats. Mode omitted shows combined view.
- `/sg leaderboard [mode]` — top players for that mode. Mode omitted shows overall.

### Scoreboard

Sidebar shows stats for the current game's mode during gameplay.

## Section 4: New Vanilla Items

### Items Added to chest.yml

**Copper tier** (between leather and iron):
- `copper_sword` — COPPER_SWORD
- `copper_axe` — COPPER_AXE
- `copper_helmet` — COPPER_HELMET
- `copper_chestplate` — COPPER_CHESTPLATE
- `copper_leggings` — COPPER_LEGGINGS
- `copper_boots` — COPPER_BOOTS

**Spears:**
- `wooden_spear` — WOODEN_SPEAR
- `stone_spear` — STONE_SPEAR
- `iron_spear` — IRON_SPEAR
- `diamond_spear` — DIAMOND_SPEAR

**Mace:**
- `mace` — MACE

### Mode Weight Philosophy

| Item Category | Classic | Modern | OP |
|---------------|---------|--------|----|
| Leather armor | 1.5 | 1.0 | 0.0 |
| Copper armor | 1.5 | 1.0 | 0.3 |
| Iron armor | 0.8 | 1.0 | 0.5 |
| Chainmail armor | 0.6 | 0.8 | 0.4 |
| Diamond armor | 0.05 | 0.6 | 1.5 |
| Netherite armor | 0.0 | 0.0 | 1.0 |
| Wooden/stone weapons | 1.5 | 0.8 | 0.0 |
| Copper weapons | 1.2 | 1.0 | 0.0 |
| Iron weapons | 0.8 | 1.0 | 0.5 |
| Diamond weapons | 0.05 | 0.6 | 1.5 |
| Spears (all tiers) | 1.0 | 1.0 | 1.0 |
| Mace | 0.0 | 0.0 | 1.5 |
| Airstrike | 0.0 | 0.15 | 1.5 |
| Smoke grenade | 0.0 | 1.2 | 1.5 |
| Glider | 0.0 | 1.0 | 1.5 |
| Ender pearl | 0.0 | 0.3 | 1.5 |
| Golden apple | 0.3 | 0.8 | 1.5 |
| Notch apple | 0.0 | 0.0 | 0.8 |
| TNT/bombs | 1.0 | 1.0 | 1.0 |
| Player tracker | 1.0 | 1.0 | 1.0 |
| Airdrop flare | 1.0 | 1.0 | 1.0 |
| Fire bomb | 0.0 | 1.0 | 1.0 |
| Splash potions | 0.0 | 0.3 | 1.5 |

All values are starting defaults — fully configurable in chest.yml and custom-items.yml.

### DeathMessages.kt Updates

`weaponName()` gains cases for:
- `MACE` → "mace"
- Spear materials → "wooden spear", "stone spear", "iron spear", "diamond spear"
- Copper weapons → "copper sword", "copper axe"

## Files Modified

| File | Change |
|------|--------|
| `domain/LootMode.kt` | **New** — enum CLASSIC, MODERN, OP |
| `config/LumaSGConfig.kt` | Add ModesSection, ModeConfig data classes with @Comment annotations |
| `chest/ChestItem.kt` | Add `modeWeights: Map<LootMode, Double>` field |
| `chest/ChestManager.kt` | Accept LootMode in fillChest/weightedRandomItem, apply multiplier |
| `util/cache/LootTableCache.kt` | Cache key becomes (tier, mode), pre-generate 15 buckets |
| `util/cache/ConcurrentChestFiller.kt` | Pass LootMode through to cache and fallback |
| `listeners/ChestListener.kt` | Read game.lootMode, pass to filler |
| `game/Game.kt` | Add lootMode property, use timing overrides |
| `game/GameManager.kt` | Accept LootMode in createGame() |
| `game/GamePlayer.kt` | Mode-aware stat persistence |
| `game/DeathMessages.kt` | Add mace, spear, copper weapon names |
| `commands/SGCommand.kt` | Add optional [mode] arg to /sg start, stats, leaderboard |
| `statistics/StatisticsService.kt` | Key stats by (uuid, mode), migration, derived globals |
| `scoreboard/ScoreboardCache.kt` | Show mode name on sidebar |
| `discord/DiscordService.kt` | Include mode in announcements |
| `src/main/resources/chest.yml` | Add copper/spear/mace items, mode-weights to all items |
| `src/main/resources/custom-items.yml` | Add mode-weights to all custom items |
