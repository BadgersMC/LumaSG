# Audit Report: audit-domain-util

Scope: 17 files under `domain/`, `permissions/`, and `util/` (non-cache) of the LumaSG plugin. Findings grouped by severity. No source files were modified.

---

### [SEV: crit] src/main/kotlin/net/lumalyte/lumasg/domain/Arena.kt:35-54 — `scanForChests()` cubic scan will freeze the main thread

**What:** `scanForChests()` performs a triple-nested loop over the full cubic volume `(2*radius+1)^3` with no chunk loading, no async batching, and no budget. With the default `radius = 500.0` (line 18), the loop is 1001 × ~1384 × 1001 ≈ 1.39 billion iterations, each calling `world.getBlockAt(x, y, z)`. Even with cheap block lookups the wall time is in the many-minutes range; with cache misses it is effectively a server hang.

**Why it matters:** This is documented to be called on the main thread (line 33). When triggered from `/lumasg ...` setup, `onEnable()`, or any UI/command handler, the entire server tick loop stops. Every player on the server freezes. This is reachable in normal use by any player with `lumasg.setup.games` invoking the command that drives it. Tied-for-most-impactful bug in this batch.

**Suggested fix:** Replace with a chunk-bounded scan (`world.getChunkAt(cx >> 4, cz >> 4)`) and either load the relevant chunks first, or stream a `MapIterator` per chunk. Long-term: do this off the main thread via a coroutine and progressively update `chestLocations`. Also add a sanity cap (e.g., refuse radius > 50) and warn if `radius.toInt()^3 > 10_000_000`.

**Confidence:** high

---

### [SEV: high] src/main/kotlin/net/lumalyte/lumasg/util/InventorySerializer.kt:43-55 — Silent data loss in `serializeInventory` fallback

**What:** When `ItemStack.serializeItemsAsBytes(items.toList())` throws, the catch block (line 46) re-serializes an *empty* inventory of the same `items.size` and returns that as a valid `ByteArray`. Callers cannot distinguish "real serialization of these items" from "fallback empty array."

**Why it matters:** If this is used to persist a player's loot/inventory contents (death drops, disconnect save, pre-game loadout, etc.), a one-time serialization failure silently wipes the player's items. The error is logged but the data is gone. Reachable in normal use any time the serializer throws (e.g., exotic NBT from a third-party plugin, OOM, classloader issues).

**Suggested fix:** On fallback, return `null` and log loudly, OR introduce a sentinel (e.g., a wrapper that carries "this is empty fallback"). Better: make the failure path explicit and let the caller decide whether to retry / block the event / etc. Do not invent a "successful" empty payload.

**Confidence:** high

---

### [SEV: high] src/main/kotlin/net/lumalyte/lumasg/domain/Arena.kt:36-37 — `scanForChests()` returns empty list silently on unloaded / missing world

**What:** `Bukkit.getWorld(worldName) ?: return emptyList()` and `center.toBukkit() ?: return emptyList()` both silently return `emptyList()` if the world is not loaded or the `SerializableLocation` cannot be resolved. The function name implies it scans the arena; in practice, the user gets an empty result with no log, no warning, no exception.

**Why it matters:** A setup admin runs the chest-scan command on an arena whose world is currently unloaded (server restart, world not yet loaded, world name typo). The arena is then saved with zero chest locations, breaking chest-refill loot logic (see GameIntegrity concern in the audit methodology) and the looted item economy. There is no signal to the admin that anything went wrong.

**Suggested fix:** Log a `WARN` with the world name and a hint ("is it loaded?") and either return a sentinel like `Result<List<SerializableLocation>>` or surface the failure to the calling command so the admin sees an error. Consider also `world.loadChunksFor(center, radius)` semantics (or a chunk-preload step) before scanning.

**Confidence:** high

---

### [SEV: med] src/main/kotlin/net/lumalyte/lumasg/util/InvitationManager.kt:29-35 — `removalListener` does not clean up `playerActiveInvitations` on TTL/size eviction

**What:** When Caffeine evicts an entry due to TTL expiry (30s) or size (1000), the `removalListener` only logs the eviction. The corresponding entry in `playerActiveInvitations: ConcurrentHashMap<UUID, String>` is left dangling — the invitee's UUID still points to a key that no longer exists in the cache.

**Why it matters:** The map grows monotonically up to the number of distinct invitees who ever received an invitation that wasn't explicitly `accept`/`decline`/`removePlayerInvitations`-d. Cleanup only happens opportunistically inside `getPlayerInvitation` (line 79), so a player who receives and ignores an invitation keeps a stale mapping forever. Under a busy server, the map leaks UUID→String entries for the lifetime of the plugin.

**Suggested fix:** Move cleanup into the removal listener: capture the `invitation.invitee` and call `playerActiveInvitations.remove(invitation.invitee, key)`. This also needs to be safe under concurrent createInvitation calls — the listener should not yank a *new* mapping that has the same invitee UUID. Use the `remove(key, value)` form of ConcurrentHashMap to compare-and-remove.

**Confidence:** high

---

### [SEV: med] src/main/kotlin/net/lumalyte/lumasg/util/InvitationManager.kt:46-63 — `createInvitation` race loses the new invitation's mapping

**What:** Lines 50-56 do `get existing key → invalidate existing → put new → put mapping`. The first three operations on `playerActiveInvitations` and `invitationsCache` are not atomic. If two threads call `createInvitation` for the same `invitee` concurrently (e.g., command handler and async event listener), one thread can:
1. read existingKey = null
2. thread B reads existingKey = null
3. A puts A into cache, sets mapping to A
4. B puts B into cache, overwrites mapping to B
5. Caffeine's removalListener fires for A and could even yank the invitee's mapping back to null if the listener is added

Net effect: B is in the cache, B's mapping is set. A is in the cache (TTL'd 30s) but is unreachable. Acceptable, but the 30s window where A is a "ghost" invitation isn't a leak — it expires — yet the *primary* correctness issue is that the *opposite* interleaving can drop B's mapping if the removalListener is added (line 30-34) and cleans up via `playerActiveInvitations.remove(invitation.invitee)` without comparing values.

**Why it matters:** Subtle. In a single-threaded Bukkit command context it's fine. In a coroutine context, the cache listener could in principle be invoked concurrently with the map write, causing the mapping to be wiped for an unrelated newer invitation. Currently the listener only logs (doesn't remove), so the worst case is an unreachable ghost entry — but adding cleanup to the listener (as suggested in the previous finding) makes this race a *correctness* bug unless guarded with compare-and-remove.

**Suggested fix:** When adding cleanup to the removal listener, use `playerActiveInvitations.remove(invitee, key)` so the listener only clears the mapping if it still points to the *evicted* key. Optionally: synchronize `createInvitation` per-invitee with a striped lock keyed by `invitee.hashCode()`.

**Confidence:** med

---

### [SEV: med] src/main/kotlin/net/lumalyte/lumasg/util/InvitationManager.kt:173-177 — `Bukkit.getPlayer` called from potentially off-main-thread coroutine context

**What:** `getPlayerName(playerId)` calls `Bukkit.getPlayer(playerId)?.name`. This is documented to be called from inside the removalListener (Caffeine invokes listeners on the thread that triggered the operation) and from logging inside `createInvitation`. Caffeine can call its removalListener from any thread that touches the cache, including async coroutine dispatchers.

**Why it matters:** `Bukkit.getPlayer(UUID)` is not thread-safe in all Paper versions; even where it doesn't throw, results can be inconsistent with the main thread's view of online players. More directly, this is a Bukkit main-thread contract violation if the cache is ever read off-thread.

**Suggested fix:** Either: (a) only call `Bukkit.getPlayer` from the main thread (wrap in `Bukkit.getScheduler().callSyncMethod` or use a `BukkitDispatcher`); or (b) cache the name snapshot at invitation creation time and store it on `TeamInvitation` so the listener doesn't need to look it up. The latter is cleaner and removes a Bukkit call from the hot path.

**Confidence:** med

---

### [SEV: med] src/main/kotlin/net/lumalyte/lumasg/util/MeteorUtils.kt:21-247 — All spawn helpers touch Bukkit world/entities without a thread-safety contract

**What:** `spawnMeteorSphere`, `spawnPhysicsExplosion`, `spawnShockwaveRing`, `spawnGroundCircle`, `spawnVisualDebris`, and `spawnExplosion` all call `world.spawnParticle`, `world.getBlockAt`, `world.spawnFallingBlock`, `world.playSound`, `world.getNearbyEntities`, and modify `FallingBlock` entities. None of these helpers are documented as main-thread-only, and `spawnVisualDebris` even mutates `FallingBlock.dropItem`, `setHurtEntities`, and `velocity` (lines 183-195) — all main-thread-only Bukkit operations.

**Why it matters:** If a caller invokes these from a coroutine dispatcher other than `BukkitDispatcher`, the Paper scheduler will throw `IllegalStateException` or, worse, race with the main thread and produce inconsistent state (e.g., `FallingBlock` set up with `dropItem = true` after a server tick boundary). The `launchMeteor` coroutine (line 249) does use `withContext(bukkitDispatcher)` per-tick, but it invokes `spawnMeteorSphere` from inside that block — which is fine, but the helper itself can be misused.

**Suggested fix:** Either annotate the helpers' docstrings with `@MainThread` / `// Must be called on the main thread`, or accept a `BukkitDispatcher` and `withContext` internally. Also: in `spawnVisualDebris` the spawned `FallingBlock` entities are never cleaned up — they remain in the world until they hit a block, the void, or despawn. Add a delayed despawn task or use `setTicksLived`.

**Confidence:** high

---

### [SEV: med] src/main/kotlin/net/lumalyte/lumasg/util/MeteorUtils.kt:264-273 — `launchMeteor` divides by `totalDistance` without a guard

**What:** Line 260 sets `val totalDistance = meteorStart.distance(target)`. Line 267 computes `val progress = (1.0 - (distanceToTarget / totalDistance)).coerceIn(0.0, 1.0)`. If a caller passes a `target` equal to (or extremely close to) the computed `meteorStart`, `totalDistance` is 0 and the division yields NaN, which propagates through `currentSpeed`, `arcHeight`, and `direction`, leaving the meteor spinning in place until the 2000-tick cap.

**Why it matters:** Edge case, but reachable if a caller computes the meteor start from the target and forgets to add an offset, or if `horizontalOffset = 0` and `spawnHeight = 0` are passed. The `if (distanceToTarget < 3 || currentLocation.y <= target.y) break` guard on line 283 will eventually exit, but only after up to 100 seconds of bogus particle work.

**Suggested fix:** If `totalDistance < 1.0`, skip the loop and immediately `onImpact()`. Or add a precondition `require(spawnHeight > 0 && horizontalOffset > 0)`.

**Confidence:** med

---

### [SEV: med] src/main/kotlin/net/lumalyte/lumasg/util/ErrorHandlingUtils.kt:289-340 — `CircuitBreaker` fields are not thread-safe

**What:** `failureCount: Int`, `isOpen: Boolean`, and `lastFailureTime: Long` are plain `var ... private set` properties (lines 289-295). `isOpen` and `failureCount` are read and written across the success and failure paths of `execute(...)` (lines 304-339) without `volatile` / `AtomicInteger` / `AtomicBoolean` semantics.

**Why it matters:** If a single `CircuitBreaker` instance is shared across coroutines or threads (which the lack of a "single-threaded only" contract invites), updates to `failureCount` from one thread are not guaranteed to be visible to another. The circuit may stay open (or stay closed) indefinitely; or `failureCount >= failureThreshold` may not be observed promptly. This is a resource-stability / observability bug under load, not a data-corruption bug.

**Suggested fix:** Make the three fields `@Volatile` and use `AtomicInteger` for `failureCount` with a compare-and-set on threshold-crossing. Or document "single-thread use only" and require callers to wrap with their own synchronization.

**Confidence:** high

---

### [SEV: med] src/main/kotlin/net/lumalyte/lumasg/util/InventorySerializer.kt:122-136 — `calculateSize` divides by `items.size` without a zero check

**What:** `"%.1f".format(base64Data.size.toDouble() / items.size)` on line 132. If `items.size == 0` and the empty array still serializes successfully (it does — see the `emptyItems` fallback), this evaluates to `0.0 / 0.0 = NaN` and the formatted string becomes `"NaN bytes"`.

**Why it matters:** Cosmetic / defensive-coding nit on the surface, but the surrounding code (line 36-56) returns a non-null `ByteArray` for empty inventories, so `calculateSize(arrayOf())` is a real call path. Returns a misleading string to whoever invokes it. Easy to fix.

**Suggested fix:** Early-return when `items.isEmpty()`. Better, document that `calculateSize` requires a non-empty array.

**Confidence:** med

---

### [SEV: med] src/main/kotlin/net/lumalyte/lumasg/domain/StatType.kt:8-9 — `columnName` for KDR and WinRate is semantically wrong

**What:** `KILL_DEATH_RATIO("K/D Ratio", "kills")` and `WIN_RATE("Win Rate", "wins")` reuse the base columns. The enum's contract (per the class docstring "Types of statistics that can be used for leaderboard filtering") implies `columnName` is the column the leaderboard SQL will `ORDER BY`. Ordering by raw `kills` for KDR and raw `wins` for WinRate is wrong — a 100-kill / 100-death player ranks above a 10-kill / 1-death player on KDR, and a 100-win / 1000-game player ranks above a 10-win / 10-game player on WinRate.

**Why it matters:** Depends on whether the leaderboard code uses this column name blindly. If the SQL builder does `"ORDER BY $columnName DESC"`, every leaderboard using KDR or WinRate produces wrong rankings — a player-integrity bug that can be triggered by any player opening the leaderboard GUI. This is the most-likely-to-be-exploited bug in this file: an op player can pad `kills` to top the KDR leaderboard without actually being the best K/D.

**Suggested fix:** Either: (a) make the leaderboard SQL smart enough to compute KDR/WinRate inline and order by the computed expression, ignoring `columnName` for these cases; or (b) replace `columnName` with a sealed-class hierarchy that distinguishes "raw column" vs "computed expression" cases. Verify the actual leaderboard consumer and patch both ends.

**Confidence:** med

---

### [SEV: med] src/main/kotlin/net/lumalyte/lumasg/util/ItemUtils.kt:195,180 — Enchantments added with `ignoreLevelRestriction = true`

**What:** `meta.addEnchant(enchant, level, true)` (line 195) and `meta.addStoredEnchant(enchant, level, true)` (line 180) both pass `true` for `ignoreLevelRestriction`. This means a config-driven item can be Sharpness X, Protection X, Fortune X, etc. — beyond vanilla max levels.

**Why it matters:** Items are loaded from YAML config files on the server; only trusted admins can edit them, so this is *not* a player-exploitable bug. However: (a) if any config gets committed to a public repo, a malicious fork can hand out overpowered custom items; (b) the items can break vanilla combat balance assumptions; (c) other plugins that read item levels (e.g., anti-cheat, scoreboards) may not handle >vanilla levels. The current behavior silently overrides the safety net Bukkit provides.

**Suggested fix:** Read the enchantment's max level from the registry and clamp the configured level. Or add a config flag `over-level-enchantments: true` per item that explicitly opts in.

**Confidence:** med

---

### [SEV: low] src/main/kotlin/net/lumalyte/lumasg/domain/PlayerStats.kt:27 — `kdr` is unbounded when `deaths == 0`

**What:** `if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths` — a player with 1000 kills and 0 deaths reports KDR = 1000.0, which dominates the leaderboard.

**Why it matters:** Cosmetic concern, but combined with the `StatType` finding above it amplifies the ranking issue. The conventional normalization is `kills / (deaths + 1)` or `kills / max(1, deaths)`.

**Suggested fix:** Use the conventional formula or cap at, say, 100.0. Document the chosen convention.

**Confidence:** low

---

### [SEV: low] src/main/kotlin/net/lumalyte/lumasg/domain/GamePhase.kt:8-12 — `Countdown`, `Grace`, `Active`, `Deathmatch`, `Ended` are immutable, complicating in-place phase mutation

**What:** All `GamePhase` subclasses use `val` for their state (`secondsLeft`, `secondsRemaining`, `winner`). Updating a tick counter therefore requires `currentPhase = currentPhase.copy(secondsLeft = currentPhase.secondsLeft - 1)`, which is a new object each tick.

**Why it matters:** Not a bug, but it forces a `var GamePhase` holder somewhere, and the holder is now a race-condition magnet. For a sealed-class state machine, the safer idiom is a separate `GameState` holder class with a synchronized tick method. Worth a code-smell flag.

**Suggested fix:** Keep the sealed types but introduce a `GameState` wrapper that owns the mutable secondsRemaining internally and exposes a `phase: GamePhase` derived value. Or accept the allocation cost (it's a single object per tick — fine) but document the holder as single-threaded.

**Confidence:** low

---

### [SEV: low] src/main/kotlin/net/lumalyte/lumasg/domain/ArenaTemplate.kt:19-25 — `applyTo` silently ignores several arena fields

**What:** `applyTo` only copies `displayName`, `minPlayers`, `maxPlayers`, `radius`, and `allowedBlocks` into the new arena. It does not touch `name`, `worldName`, `spawnPoints`, `center`, `chestLocations`, `lobbySpawn`, `spectatorSpawn`, or `enabled`. The docstring says "Apply this template's settings to an existing arena" but the unmentioned fields are quietly preserved.

**Why it matters:** A future maintainer who adds `description` to `ArenaTemplate` and assumes it cascades will be surprised. Also, if the template's intent is to *fully* define the arena, half-applying is more confusing than a full copy or a documented partial merge.

**Suggested fix:** Either (a) document explicitly which fields are template-controlled, or (b) make `applyTo` exhaustive and validate required fields per template.

**Confidence:** low

---

### [SEV: low] src/main/kotlin/net/lumalyte/lumasg/util/MiniMessageUtils.kt:143-166 — `convertLegacyToMiniMessage` is case-sensitive and lossy

**What:** The chain of `.replace("&a", "<green>")` etc. only matches lowercase Minecraft legacy codes. Real legacy `&A` is not handled. Additionally, the `&` character is never escaped, so a player who *meant* to type a literal `&c` in chat (rare, but possible in lore text) will have it silently replaced with `<red>`.

**Why it matters:** Cosmetic / defensive-coding. Not security (no template injection — these are simple string replaces, not MiniMessage parsing at this stage).

**Suggested fix:** Pre-lowercase the string for the replacements, or expand the table with uppercase variants. For literal `&` survival, offer an opt-in escape.

**Confidence:** low

---

### [SEV: low] src/main/kotlin/net/lumalyte/lumasg/util/MiniMessageUtils.kt:176-182 — `processPlaceholders` iteration order is non-deterministic

**What:** Iterates over a `Map<String, String>` whose order is implementation-defined (e.g., `kotlin.collections.Map` is a JVM `LinkedHashMap` in most cases, but the contract is unspecified). If a placeholder value contains another placeholder's key syntax (e.g., `value = "<other_placeholder>"`), the result depends on iteration order.

**Why it matters:** Edge case (placeholder values normally don't contain `<...>` patterns), but the function is exposed for general use. Cheap to fix.

**Suggested fix:** Sort the keys before iterating, or document that placeholder values must not contain other placeholder syntax.

**Confidence:** low

---

### [SEV: low] src/main/kotlin/net/lumalyte/lumasg/util/MiniMessageUtils.kt:191-192 — `deserializeLegacy` replaces every `&`, including literals

**What:** `legacyText.replace('&', '\u00A7')` replaces *all* ampersands. A lore line like `"Coins: & 5"` becomes `"Coins: § 5"`.

**Why it matters:** Cosmetic. Worth a defensive escape.

**Suggested fix:** Document the constraint, or only do the replace if a recognized color-code suffix follows (e.g., `&[0-9a-fl-or]`).

**Confidence:** low

---

### [SEV: low] src/main/kotlin/net/lumalyte/lumasg/util/ItemUtils.kt:310-318 — `isArmorMaterial` doesn't recognize `TURTLE_HELMET`, `LEATHER_HORSE_ARMOR`, `SADDLE`, etc.

**What:** The substring check on `material.name` covers `LEATHER_`, `CHAINMAIL_`, `IRON_`, `GOLDEN_`, `DIAMOND_`, `NETHERITE_`. `TURTLE_HELMET` and `LEATHER_HORSE_ARMOR` are valid `ArmorMeta` materials but are not matched.

**Why it matters:** When the config sets `trim` for a `TURTLE_HELMET` (which is a real thing in vanilla 1.21+), the trim is silently not applied. Cosmetic / feature gap.

**Suggested fix:** Add the missing material names to the predicate, or replace with a positive `Material` allowlist.

**Confidence:** low

---

### [SEV: low] src/main/kotlin/net/lumalyte/lumasg/util/ItemUtils.kt:320-355 — `applyArmorTrim` falls through to random trim if `pattern`/`material` keys are empty strings

**What:** `trimSection.getString("pattern", "")` returns `""` when the key is missing or empty. The registry lookup `Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(""))` returns null, the `if (pattern != null && material != null)` is false, and the function falls into the `else` branch (line 339) and applies a *random* trim. A config that intentionally sets `pattern: ""` to mean "no trim" silently gets a random one instead.

**Why it matters:** Cosmetic / config-author footgun. Not a security issue. Worth logging a debug or warn when pattern is explicitly empty.

**Suggested fix:** When `patternKey.isBlank()`, skip trim application entirely (or accept an explicit `none` sentinel).

**Confidence:** low

---

### [SEV: low] src/main/kotlin/net/lumalyte/lumasg/util/MeteorUtils.kt:180-196 — `spawnVisualDebris` `FallingBlock` entities are never cleaned up

**What:** `world.spawnFallingBlock(...)` creates real entities. `dropItem = false` and `setHurtEntities(false)` are set, but there is no `setTicksLived(N)` cap, no scheduled removal, and no `setRemoveWhenFarAway(true)`. The entities live until they hit a solid block, the void, or vanilla despawn (5 minutes in most cases).

**Why it matters:** A storm of meteor explosions in a short window can leave hundreds of falling blocks lingering in the world, which:
- (a) chunks the world and bloats region files;
- (b) can be visually confusing for players;
- (c) can be picked up by a Paper bug where `dropItem = false` is ignored server-side under some conditions, silently restoring an item drop exploit.

**Suggested fix:** `fallingBlock.setTicksLived(100)` (5 seconds) and `fallingBlock.removeWhenFarAway = true`. Or schedule a Bukkit task to remove them after N ticks. Verify Paper actually honors `dropItem = false` for `FallingBlock` spawned programmatically (it does in 1.20+, but the contract has changed historically).

**Confidence:** med

---

## Test coverage gaps (named)

| Where | Missing test | Suggested test name |
|-------|-------------|---------------------|
| `domain/PlayerStats.kt:27` | `kdr` is unbounded when `deaths == 0` | `PlayerStatsTest.kdr_unbounded_when_deaths_zero_does_not_throw_and_does_not_dominate_leaderboard` |
| `util/InvitationManager.kt:29-35` | Removal listener must clean up `playerActiveInvitations` on TTL eviction | `InvitationManagerTest.expired_invitation_removes_playerActiveInvitations_entry` |
| `util/InvitationManager.kt:46-63` | Concurrent `createInvitation` for the same invitee must not lose the newer mapping | `InvitationManagerTest.concurrent_createInvitation_for_same_invitee_keeps_newest_mapping` |
| `util/InvitationManager.kt:89-95` | `acceptInvitation` removes the mapping in `playerActiveInvitations` | `InvitationManagerTest.acceptInvitation_clears_playerActiveInvitations` |
| `util/InvitationManager.kt:103-111` | `declineInvitation` removes the mapping in `playerActiveInvitations` | `InvitationManagerTest.declineInvitation_clears_playerActiveInvitations` |
| `util/InvitationManager.kt:127-142` | `removePlayerInvitations` clears both invitee and inviter roles | `InvitationManagerTest.removePlayerInvitations_clears_both_inviter_and_invitee_mappings` |
| `util/ErrorHandlingUtils.kt:283-340` | `CircuitBreaker` opens after threshold and resets after timeout | `CircuitBreakerTest.opens_after_threshold_failures_and_resets_after_timeout` |
| `util/ErrorHandlingUtils.kt:39-81` | `executeWithRetry` retries only recoverable errors | `ErrorHandlingUtilsTest.executeWithRetry_does_not_retry_non_recoverable_errors` |
| `util/InventorySerializer.kt:43-55` | Fallback returns null, not an empty payload | `InventorySerializerTest.serializeInventory_returns_null_on_failure_not_empty_array` |
| `util/InventorySerializer.kt:65-76` | Deserialization of corrupted Base64 returns null, not partial | `InventorySerializerTest.deserializeInventory_corrupted_base64_returns_null` |
| `domain/Arena.kt:35-54` | `scanForChests` does not freeze the main thread for large radii | `ArenaTest.scanForChests_completes_within_main_thread_budget_for_default_radius` (likely a load test, not a unit test — mark as integration) |
| `domain/ArenaTemplate.kt:19-25` | `applyTo` preserves non-templated fields exactly | `ArenaTemplateTest.applyTo_preserves_unrelated_fields` |
| `util/ItemUtils.kt:195,180` | Configured enchantments above vanilla max are clamped | `ItemUtilsTest.createItemFromConfig_clamps_enchant_level_to_registry_max` |
| `util/ItemUtils.kt:248-293` | Unknown potion effect type does not throw | `ItemUtilsTest.createItemFromConfig_unknown_potion_effect_type_logs_warning_and_skips` |
| `util/MeteorUtils.kt:228-247` | Explosion damage falls off linearly with distance | `MeteorUtilsTest.spawnExplosion_damage_falls_off_linearly_with_distance` |
| `util/MeteorUtils.kt:249-290` | `launchMeteor` handles `target == start` (zero distance) | `MeteorUtilsTest.launchMeteor_zero_distance_target_does_not_divide_by_zero` |
| `util/MiniMessageUtils.kt:143-166` | `convertLegacyToMiniMessage` handles empty and unknown codes | `MiniMessageUtilsTest.convertLegacyToMiniMessage_handles_empty_string_and_unknown_codes` |
| `util/MiniMessageUtils.kt:70-77` | `parseMessage` with empty placeholder map works | `MiniMessageUtilsTest.parseMessage_with_empty_placeholders_returns_parsed_component` |
| `permissions/RankPermissions.kt:46-47` | `hasAdminAccess` is true for OPs even without explicit permission | `RankPermissionsTest.hasAdminAccess_true_for_op_players` |
| `permissions/RankPermissions.kt:49-55` | `getPermissionLevel` ordering (admin > moderator > leader > player > guest) | `RankPermissionsTest.getPermissionLevel_returns_correct_level_for_each_tier` |
| `util/ConfigurationManager.kt:30-39` | `init` does not crash when a default resource is missing | `ConfigurationManagerTest.init_succeeds_when_a_default_config_is_missing` (defensive test) |
| `util/ConfigurationManager.kt:61-87` | `updateConfig` preserves existing user values | `ConfigurationManagerTest.updateConfig_preserves_user_values` |
| `util/ConfigurationManager.kt:111-142` | `updateConfigSection` adds missing nested sections | `ConfigurationManagerTest.updateConfigSection_adds_missing_nested_section` |
| `util/InvitationManager.kt:153-158` | `getStats` does not throw when cache is empty | `InvitationManagerTest.getStats_empty_cache_returns_string_with_zero_size` |
| `util/InventorySerializer.kt:122-136` | `calculateSize` with empty array does not produce NaN | `InventorySerializerTest.calculateSize_empty_array_returns_zero_average` |
| `util/MeteorUtils.kt:138-148` | `spawnGroundCircle` handles negative and zero radius | `MeteorUtilsTest.spawnGroundCircle_zero_radius_does_not_throw` |

---

## Methodology notes

- No SQL strings were found in this batch; the SQL-injection check is N/A.
- No secret material (tokens, webhooks) is hardcoded; the secret-leakage check is N/A.
- No Java native deserialization is used; NBT (`ItemStack.serializeAsBytes`) is safe-by-construction.
- No path-traversal sinks in this batch.
- All Bukkit-thread concerns are listed under thread-safety in their respective findings.
- Confidence labels are calibrated to "high" when the failure mode is provable from the code alone, "med" when it depends on a consumer (e.g., `StatType.columnName` accuracy depends on the leaderboard SQL), and "low" when the impact is mostly cosmetic / future-proofing.
