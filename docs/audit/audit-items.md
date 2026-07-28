# Audit Report: audit-items

Scope: `src/main/kotlin/net/lumalyte/lumasg/chest/ChestItem.kt`,
`chest/ChestManager.kt`, and `items/{AirdropFlareItem,AirstrikeItem,BombItem,CustomItem,CustomItemsManager,FireBombItem,GliderItem,KnockbackStickItem,PlayerTrackerItem,PoisonBombItem,SmokeGrenadeItem}.kt` (13 files).
Focus: dupe-exploits, main-thread/Bukkit-API violations, race conditions, NBT/serialization safety, test coverage.

Existing test coverage in this surface area: zero. The test tree has only
`persistence/`, `game/`, `domain/` subpackages — no tests exercise any
custom item, the chest manager, or `CustomItemsManager` despite the code
running 9 distinct items with stateful listeners and shared coroutine
timers.

---

### [SEV: high] chest/ChestManager.kt:154-204 — `fillChest` only fills one half of a double chest, leaving orphaned loot

**What:** `fillChest` reads `state` (one half of a `Chest` block) and calls
`state.inventory.clear()` followed by `inventory.setItem(...)` on a single
`Inventory` (size 27 for a single chest, 54 for a double). The companion
half of a double chest is never cleared, and items previously placed in
the other half are not re-randomised or synchronised.

**Why it matters:** In an arena with double chests (common in SG maps), a
player opening the chest sees 27 fresh items on one side and whatever
items were in the other half (from a previous refill, a chunk-load fill
that used the *other* half as the entry-point `state`, or admin-placed
loot). It is also reachable in a real dupe path: a `ChunkLoadEvent`
firing while a double chest is split across a chunk boundary can fill
each half independently, and if `filledChests` is cleared on refill
(ChestListener:159) the next pass can re-fill both halves with newly
generated loot while the old loot from the unfilled half is still
present in tile-entity state.

**Suggested fix:** Detect double-chests via `state.inventory.holder` and
when the other half is a `Chest`, operate on the merged inventory. The
`org.bukkit.block.Chest` API exposes `getInventory()` returning the
merged double-chest view; use `((DoubleChestInventory) inv).getHolder()`
or, more reliably, loop both halves via `state.getOtherHalf()` and
`state` and clear/set both inventories. For double-chests, allocate from
the *full* merged 54-slot inventory, not the 27-slot half view.

**Confidence:** high

---

### [SEV: high] items/CustomItemsManager.kt:99-104 — `reload()` re-registers Bukkit listeners without unregistering, causing duplicate event handling

**What:** `reload()` calls `shutdownItems()` (which only stops BukkitRunnables
and clears state — does not unregister listeners), then `registry.clear()`,
then `registerDefaults()`. The `registerDefaults` body (lines 50-54) calls
`item.register()` for `GliderItem`, `SmokeGrenadeItem`, and
`AirstrikeItem`. Each `register()` calls
`plugin.server.pluginManager.registerEvents(this, plugin)`. Bukkit allows
the same Listener instance to be registered multiple times; every event
subsequently invokes the handler twice.

**Why it matters:** After a `/sg reload` (or any path that hits `reload()`),
the airstrike / glider / smoke-grenade handlers run twice. For
`AirstrikeItem`, the charge state-machine is idempotent (guarded by
`chargeStates.containsKey`), but `onDrop`, `onItemHeld`, `onQuit` and
`onDamage` each emit `cancelCharge` / `cancelGlide` redundantly — cheap,
but the PlayerTrackerItem-style `updateTask` is started in `register()`
without being stopped first, so the periodic compass update fires twice
per tick after reload (double CPU, double `sendActionBar` calls, the
client sees the second write win). For GliderItem, the `glideTask` is
similarly restarted without cancelling, doubling particle/sound work
and potentially confusing the `state.glidingActive` flag.

**Suggested fix:** Either (a) make `register()` idempotent via a
`@Volatile var registered = false` guard around the
`registerEvents` call, or (b) in `shutdownItems`, also call
`HandlerList.unregisterAll(this)` for the Listener items, or (c) drop
the manual `register()` calls from `registerDefaults` and instead
expose the items as DI services so the framework only registers them
once.

**Confidence:** high

---

### [SEV: high] items/AirstrikeItem.kt:293-298 — airstrike immune-list logic makes the caller the only team member who can take damage

**What:** Inside the per-meteor `onImpact` lambda:
```kotlin
val immuneUUIDs = mutableSetOf<UUID>()
val callerTeam = game.teamManager.getTeamForPlayer(callerUuid)
if (callerTeam != null) {
    immuneUUIDs.addAll(callerTeam.members)
}
immuneUUIDs.remove(callerUuid)
```
The team (which contains the caller) is added to the immune set, then
the caller is removed. Net effect: the caller's teammates are immune,
the caller is not.

**Why it matters:** The caller's *own* meteor kills them but spares
their teammates. In a duo/trios/squads game this is a grief-vector: a
player can drop an airstrike on a teammate (locked onto shared
position) and the teammate takes zero damage while the caller is the
only one punished — or, more commonly, in normal use the caller is
penalised for an ability that was supposed to be team-safe. It is
also the opposite of what the surrounding code (AirdropFlareItem
explosion at line 100, which has *no* team immunity) suggests is the
intended design.

**Suggested fix:** Remove the `immuneUUIDs.remove(callerUuid)` line. If
the design is "caller immune, team not," reverse the order: add caller
to a base set, then add team members. Document the chosen policy.

**Confidence:** high

---

### [SEV: high] items/AirdropFlareItem.kt:99-108 — chest is placed inside the player or on top of an arbitrary block with no safety check

**What:** `MeteorUtils.spawnExplosion(center = dropLocation, plugin = plugin)`
runs first (line 100), which can damage the player. Then
`findSolidGround(dropLocation)` (line 103) walks down from the player's
*current* location — the player may have been pushed by the explosion
or another player — and sets `chestLoc.block.type = Material.CHEST`
(line 104) unconditionally. There is no check that the target block is
air, that the location is within the arena, or that the chest location
is not occupied by a player, mob, or block entity (e.g. another chest,
sign, bed).

**Why it matters:** Multiple game-breaking scenarios:
1. If the player stands still and the impact location is the same as
   the player location, `findSolidGround` descends to the floor under
   the player and the chest is placed at floor+1 — directly under or
   inside the player, suffocating or trapping them.
2. The chest silently overwrites non-air blocks (a sign, a previously
   placed chest, a redstone component). For non-vanilla tile entities
   this destroys their contents with no log.
3. The chest is placed at the player's *current* location, not the
   flare-aimed location. If the player moves during the 5-second
   indicator + 2-second pre-impact, the airdrop can end up nowhere
   near where the flare was thrown.
4. No null check on `findSolidGround` reaching the void — the function
   stops at y=0 and places a chest at y=1 (floating in the air if no
   solid block exists).

**Suggested fix:** Capture the target location from the player's look
direction (or from a snapshot at flare-use time, not at impact time).
Before placing the chest, verify the block is air and that the
resulting chest location is within the arena bounds. For occupied
blocks, queue the chest placement to a clear neighbouring block or
abort with a message. Snapshot the player location at `onUse` time
instead of recomputing via `player.location.clone()` so the impact is
predictable.

**Confidence:** high

---

### [SEV: high] items/AirstrikeItem.kt:182-191 — held item decremented before game lookup, so charges can be lost to a no-op game resolution

**What:** In `handleLocked`:
```kotlin
if (state.lockTicks >= cfg.lockOnDurationTicks) {
    toRemove.add(uuid)
    val held = player.inventory.itemInMainHand
    if (isAirstrikeItem(held)) held.amount--
    player.sendActionBar(Component.empty())
    val game = gameManager.getGameForPlayer(uuid) ?: return
    launchAirstrike(state.targetLocation, game, uuid, player.name)
}
```
The item is decremented and the action bar cleared *before* the game
lookup. If `getGameForPlayer` returns null (player left, game ended,
race), the function `return`s without launching the strike. The
charge-state is also already removed via `toRemove.add(uuid)`, so the
item is consumed but no strike fires.

**Why it matters:** A boundary-condition dupe: if a player holds the
charge through the lock-on duration and then disconnects, reconnects,
or has their game ended by an external event in the same tick, the
airstrike item is consumed without any effect. The same window
exists for `PlayerQuitEvent` racing the task — the `onQuit` handler
calls `cancelCharge` which removes from `chargeStates`, but the task
may have already advanced `lockTicks` past the threshold and executed
the decrement. Charge is consumed, strike is never called.

**Suggested fix:** Reorder: resolve the game first, then consume the
item, then launch. Add a `if (game == null) return` early-out that
preserves the charge state (i.e., don't add to `toRemove` until the
launch is actually scheduled).

**Confidence:** med

---

### [SEV: med] items/GliderItem.kt:216, 247-250 — `recentlyGliding` map grows unboundedly for offline players

**What:** `recentlyGliding: ConcurrentHashMap<UUID, Long>` is populated
on every glide end (line 154) and only cleaned up in `onQuit` (line
249). Players who glide, land, and then take fall damage within the
5-tick grace window are properly removed (line 231). But players who
glide, land, and *never* take fall damage leave an entry that persists
until they quit. The map also persists across server restarts in
memory (it's a plain HashMap, not persisted — so it dies on restart,
but accumulates within a single uptime).

**Why it matters:** A long-uptime server with thousands of glider-uses
accumulates UUID→fullTime entries. Not a critical memory leak (two
Longs per entry ≈ 64 bytes), but it is unbounded. The `onQuit`
handler is the only cleanup, and players who crash-disconnect without
firing `PlayerQuitEvent` (rare, but possible during netty errors)
leave permanent entries.

**Suggested fix:** Cap the map (Caffeine cache with size limit and
expiry), or evict entries older than the grace window (5 ticks)
periodically. Alternatively, since the only consumer is the
5-tick-window fall-damage check, drop the map entirely and use a
`Set<UUID>` of "just-landed" players that is cleared by a delayed
task.

**Confidence:** high

---

### [SEV: med] items/SmokeGrenadeItem.kt:173-203 — `updateVisibility` only iterates observer/target pairs within 3× the cloud radius

**What:** `nearbyPlayers` is computed via `getNearbyEntities(center,
checkRange, ...)` where `checkRange = radius * 3` (line 177). The
double `for` loop (lines 182-183) iterates `for (observer in
nearbyPlayers) for (target in nearbyPlayers)`. Targets that are in
line-of-sight of the observer through the cloud but lie *outside*
the 3× radius envelope (e.g. a target 50 blocks away behind a
nearby cloud) are never considered — so they are never hidden even
though the cloud is on the ray between them.

**Why it matters:** The smoke grenade is described as "blocks vision
and nametags", but this implementation only hides players who are
themselves within the 3× radius of the cloud. Snipers, distant
enemies, or anyone beyond `radius * 3` is unaffected regardless of
whether the cloud is directly in their line of sight. This is a
gameplay integrity failure — the item doesn't do what its lore
promises, and players can infer the radius boundary and use it to
their advantage.

**Suggested fix:** Iterate all online players (or use a much larger
search radius) as the candidate observer set, and for each observer
check whether any active cloud blocks their line of sight to each
target. Use the existing `rayIntersectsSphere` per (observer, target,
cloud) — it's already O(c) per pair, just need a wider candidate
set.

**Confidence:** high

---

### [SEV: med] items/SmokeGrenadeItem.kt:264-273 — `restoreAllVisibility` unconditionally `showPlayer`s every hidden target, including those still legitimately hidden

**What:** On `@PreDestroy shutdown`, `restoreAllVisibility` iterates
`hiddenPlayers` and calls `observer.showPlayer(plugin, target)` for
every entry. There is no `activeClouds.values.any { ... }` check
(compare to `restoreVisibilityForCloud` line 251 which does have
this check). If the plugin is reloaded or the server shuts down with
active smoke clouds, all hidden targets are revealed, breaking the
in-flight smoke screen.

**Why it matters:** The intended behaviour on shutdown is debatable,
but the asymmetry with `restoreVisibilityForCloud` (which respects
other clouds) is a real bug. After a hot-reload, all smoke cover
collapses instantly.

**Suggested fix:** In `restoreAllVisibility`, mirror the
`stillBlocked` check from `restoreVisibilityForCloud`: only
`showPlayer` for entries where no other cloud still blocks the
visibility.

**Confidence:** med

---

### [SEV: med] items/PlayerTrackerItem.kt:247-261 — top-killer calculation only counts online players, missing the actual leader during a disconnect

**What:** `findTopKiller` returns the UUID of the player with the most
kills among currently-online players in the game. A player with the
leading kill count who briefly disconnects (network blip) is skipped
entirely — the next-highest online killer is shown.

**Why it matters:** The compass is the dominant information tool
during late-game deathmatch. If the leading killer rage-quits at
2 kills ahead, the compass points to the 2nd-place online player
until the leader reconnects. This is a state desync between the
real leader (GamePlayer.kills) and what the compass shows, and
players can grief it by re-logging at strategic moments.

**Suggested fix:** Don't filter by online status; base the top-killer
purely on `gp.kills`. If the player is offline, the compass entry
won't be drawn (because `Bukkit.getPlayer(uuid)` is null in the
target loop), but the `isTopKiller` flag is already set on the
correct UUID, so when they reconnect, the marker immediately
attaches to the right target.

**Confidence:** med

---

### [SEV: med] items/AirstrikeItem.kt:110-142 — `chargeTask` self-cancels when `chargeStates` is empty but doesn't survive a single-tick exception

**What:** `ensureTaskRunning` creates a BukkitRunnable. Inside `run()`,
the first guard is `if (chargeStates.isEmpty()) { cancel();
chargeTask = null; return }`. There is no try/catch around the body.
If the loop body throws (e.g. from a Bukkit API call on a removed
entity, an NPE in `handleCharging` when the player teleports
mid-raycast, or a deserialise failure on a malicious config string),
Bukkit catches the exception and logs it — but the task *continues
running* on the next tick. `chargeTask` is still set, so the
guard `if (chargeTask != null) return` blocks recreation. The task
keeps ticking with stale state, but if the exception happened before
`toRemove.forEach { chargeStates.remove(it) }`, a stuck state can
leak.

**Why it matters:** A single transient exception leaves the charge
state machine running but non-functional. Players who right-click
to start a new charge will hit `chargeStates.containsKey(player.uniqueId)`
and silently no-op (the entry is still there from a previous
aborted run). The fix is small and the impact is "charge
occasionally deadlocks until restart" — not a dupe, but a
playability issue.

**Suggested fix:** Wrap the body in `try { ... } catch (e: Exception) {
logger.error("Airstrike charge task failed", e); cancel(); chargeTask
= null }`. Or use `runTaskTimer` with a coroutine wrapper and
`runCatching`.

**Confidence:** med

---

### [SEV: med] items/SmokeGrenadeItem.kt:275-290 — `cleanupVisibility` Elvis fallback `continue` is correct but reads like a bug; also misses the target-set cleanup in the outer scope

**What:** Lines 287-288:
```kotlin
if (set.remove(uuid)) {
    val observer = Bukkit.getPlayer(observerUuid) ?: continue
    observer.showPlayer(plugin, player ?: continue)
}
```
The `?: continue` Elvis-fallback to `Nothing` is functionally
correct, but the second `continue` is inside a `for` whose iteration
variable is `set` (a per-observer set) — `continue` skips to the
next `(observerUuid, set)` entry, not the inner `for (targetUuid in
hidden)`. So if `player` is null, all remaining target UUIDs in
`hiddenPlayers` are skipped, not just the current one. The
correct `continue` target is unclear, suggesting the author wasn't
sure.

Also, `cleanupVisibility` is only called from `onQuit` (line 294).
It cleans up the quitting player's own hidden set and their
appearances in other observers' sets, but it does *not* remove
references to the quitting player from the `target` side of
`activeClouds` (clouds don't hold target references, so this is
fine — but the inner `for` on line 284-289 iterates
`hiddenPlayers.entries`, which is fine).

**Why it matters:** The Elvis-`continue` issue means that if the
quitting player object is `null` (server has already removed the
Player reference), the entire `hiddenPlayers` cleanup loop
short-circuits and other observers are left with stale `hidden`
entries. After several quit-while-in-smoke events, observers can
accumulate hidden-by-UUID entries that block legitimate
`showPlayer` calls (Bukkit silently no-ops on showPlayer for
unknown UUIDs, but the bookkeeping drifts).

**Suggested fix:** Replace the Elvis-`continue` pattern with
explicit `let { ... } ?: continue@outerLoop` or extract the
cleanup into a helper that takes a non-null `Player?` and returns
`Boolean`. Add a guard `if (player == null) { for ((obs,
set) in hiddenPlayers) set.remove(uuid); return }` to drop the
target's UUID from every observer's set regardless of Player
availability.

**Confidence:** med

---

### [SEV: med] items/AirdropFlareItem.kt:51-138 — `item.amount--` outside the coroutine references the original stack; item use can double-consume if the player swaps hands

**What:** `onUse` reads `item: ItemStack` (the ItemStack passed by
`CustomItemListener.onInteract` at line 70 of that listener, which
passes `event.item`). Then `item.amount--` runs on line 137 *after*
the `game.scope.launch { ... }` block has been queued. The coroutine
inside does not touch `item`. The decrement is on the same stack
object that the listener holds.

But: `event.item` in `PlayerInteractEvent` is the *interact* item
snapshot at event-fire time. If the player switches hands or the
inventory is reorganised before the listener returns, the
`item.amount--` mutates the *original* stack, not the current
inventory slot. This is normally fine — the stack still has the
correct amount — but a concurrent Bukkit task that mutates the
held item (e.g., a re-give on respawn) could race.

**Why it matters:** A duplicated-consume could occur if the same
`ItemStack` is being passed by reference to two event handlers and
both decrement. In this code, only `CustomItemListener.onInteract`
calls `onUse`, so single-decrement is correct in normal use. But
the comment in `CustomItemListener` line 64-67 only debounces by
1-second cooldown, not by stack identity. If the player's
cooldown expires mid-launch and they re-trigger, the same
`item.amount--` from a *different* use-event decrements the
already-decremented stack to -1 (which Bukkit clamps to 0 but
firing the second `onUse` could matter for coroutine state).

**Why lower:** Mostly defensive; the 1-second cooldown (line 42 of
`CustomItemListener`) prevents rapid double-fire. But there is no
`isCancelled` early-out after `item.amount--` for the case where
`game.scope.launch` is queued but the coroutine's first `withContext`
suspends long enough for another right-click to fire (e.g. the
player click-spams while the announcement is in flight). Each
click would re-enter `onUse`, re-launch a coroutine, re-decrement
the item. The coroutines are not deduplicated by player, so multiple
flares can be in flight simultaneously per player.

**Suggested fix:** Add a per-player `flaringPlayers: Set<UUID>` set
on `AirdropFlareItem` and check it at the top of `onUse`. Remove
the entry when the chest-glow phase ends (line 122-134, the 9-second
loop) or on `onQuit`.

**Confidence:** med

---

### [SEV: low] chest/ChestManager.kt:132-138 — `createEnrichedSection` silently swallows `tier-weights` overrides nested under non-top-level keys

**What:** `for (key in itemSection.getKeys(true))` returns all keys
recursively (e.g., `enchantments.knockback`). The filter `!key.startsWith("tier-weights")`
removes any key whose path begins with `tier-weights`, but if an admin
nests a custom override under another section, it is copied verbatim.
The `tier` and `chance` keys are also unconditionally overwritten with
the *per-tier-loop* values, so a section like
`some-section.tier: "rare"` is also overwritten by `enriched.set("tier", tier)`.

**Why it matters:** No security implication — admins control the
config. Minor correctness issue: the merging logic is fragile and
non-obvious, making custom-config debugging harder. A future feature
that adds nested config like `attributes.attack_damage` would be
copied correctly, but a feature that adds `tier-weights` as a nested
key elsewhere would be incorrectly filtered.

**Suggested fix:** Document the merge contract in a comment; or use
a typed builder that explicitly selects the keys to forward.

**Confidence:** low

---

### [SEV: low] chest/ChestItem.kt:84 — `nexoResolver` is a `@Volatile` static on a non-final data class

**What:** `@Volatile var nexoResolver: ((String) -> ItemStack?)? = null`
on the `companion object` of `ChestItem`. The function reference is
captured at `init()` time and never refreshed. If `NexoHook` is
reloaded (e.g. Nexo plugin is disabled mid-game and re-enabled), the
resolver points at a stale closure and resolves to whatever the
`NexoItems.itemFromId` call returns at that moment — possibly null
silently (the wrapper at NexoHook:33 swallows exceptions).

**Why it matters:** The fallback (a diamond with a warning lore) covers
this case visually, but the resolver never being refreshed means
chests filled after a Nexo reload use the same cached `(id → null)`
mapping they had at startup. If a new Nexo item is added at runtime,
chest items referencing it fall back to the diamond.

**Suggested fix:** Reassign `nexoResolver` in
`CustomItemsManager.reload()` (or in a Nexo plugin-enable listener)
to the current `nexoHook::getNexoItem` reference. Or, more
defensively, drop the cached resolver entirely and look up
`NexoHook` directly via DI in `resolveItemStack`.

**Confidence:** low

---

### [SEV: low] items/SmokeGrenadeItem.kt:53-58 — `shutdown` cancels tasks but `activeClouds` may contain clouds whose tasks have already self-cancelled

**What:** The shutdown iterates `activeClouds` and calls
`cloud.task?.cancel()`, then `restoreAllVisibility()`, then
`activeClouds.clear()`. A cloud whose task already self-cancelled
(via `if (ticksElapsed >= cfg.durationTicks) { cancel();
activeClouds.remove(cloudId); restoreVisibilityForCloud(cloud);
return }` at line 98-103) is no longer in `activeClouds` by the
time shutdown runs. But for clouds still in the map at shutdown,
`restoreAllVisibility` runs *before* `activeClouds.clear()`. This
means the `stillBlocked` check in `restoreVisibilityForCloud` (line
251) sees the clouds that are about to be cleared — so it correctly
preserves legitimately-blocked visibility. OK on closer reading, but
the ordering is fragile.

**Why it matters:** None, on inspection. Flagging for defensive-coding
nit: a future refactor that swaps the `clear()` and
`restoreAllVisibility()` order would introduce a visibility-leak
bug.

**Suggested fix:** Add a comment explaining the ordering invariant.

**Confidence:** low

---

### [SEV: low] items/AirstrikeItem.kt:205-217 — `onItemHeld` and `onDrop` only cancel charge for the *previous* slot / dropped item, not the *new* slot

**What:** `onItemHeld` (line 204-209) checks `oldItem` for the
airstrike tag and cancels the charge. It does *not* check the
`newItem` — but starting a charge requires `onUse` (right-click),
not just holding, so this is correct. `onDrop` (line 212-217) is
symmetric.

**Why it matters:** None — flagged for completeness because the
*inverse* bug (always cancelling on any swap) would silently disable
the item. The current code is right.

**Suggested fix:** None. Add a test that asserts the charge survives
a hand-swap from off-hand to main-hand with the spyglass.

**Confidence:** low

---

### Test-coverage gaps (med)

The items package has **zero test coverage**. The following specific
behaviours are untested but reachable in normal play:

- **ChestManager fillChest double-chest behaviour** — should test that
  a double chest has both halves populated from a single call, that
  pre-existing items in the *other* half are cleared, and that the
  `toBlockKey` de-dup in `ChestListener` (line 76, 104) treats a
  double chest as one entry (currently it inserts two keys — one
  per half — meaning the second half can be filled twice or the
  halves can drift if `chunk.tileEntities` reports them in different
  orderings).
- **CustomItemsManager reload()** — should test that calling
  `reload()` once does not register listeners twice. Currently this
  is the only way to discover the duplicate-registration bug above.
- **AirstrikeItem immune-list logic** — should test that in a duo
  game where the caller throws an airstrike at the team's position,
  both teammates take zero damage and the caller takes the meteor
  damage (current behaviour is the opposite).
- **AirdropFlareItem findSolidGround at world void** — should test
  that calling with `impact.y = 0` does not place a floating chest
  or NPE.
- **PlayerTrackerItem findTopKiller while leader is offline** —
  should test that the compass still points at the leader's UUID
  slot once they reconnect, and that during the offline window the
  marker is not shown on the wrong player.
- **SmokeGrenadeItem rayIntersectsSphere edge cases** — should test
  the `t1 < 0 && t2 > 1` "ray passes through" branch with a known
  geometry (e.g. observer 5 blocks before sphere, target 5 blocks
  after, both outside the sphere).
- **GliderItem recentlyGliding grace window** — should test that
  fall damage is cancelled exactly within 5 ticks of landing, and
  taken exactly on the 6th tick.
- **AirstrikeItem charge task exception recovery** — should test
  that injecting an exception in `handleCharging` (e.g. by removing
  the player mid-task) doesn't deadlock the charge state machine.

---

Files in this batch that have **no findings**:
`CustomItem.kt`, `KnockbackStickItem.kt`, `PoisonBombItem.kt`,
`BombItem.kt`, `FireBombItem.kt`. The simple items are correctly
scoped: they spawn a single projectile with a metadata tag, the
behavioural side-effects (damage, fire, particles) are handled in
`CustomItemListener`, and there are no state machines to audit.
`KnockbackStickItem` is a single-enchantment stick; the only
subtlety is whether `Enchantment.KNOCKBACK, 5` is server-version
compatible, which is out of scope for this audit.
