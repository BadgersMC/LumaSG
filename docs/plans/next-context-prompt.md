# Context Window Continuation Prompt

Use this prompt to continue the LumaSG Kotlin rewrite in a new context window.

---

## PROMPT:

I'm doing a Kotlin rewrite of the LumaSG Minecraft Survival Games plugin. The work is in a git worktree at `D:/BadgersMC-Dev/LumaSG/.worktrees/kotlin-rewrite/` on branch `feature/kotlin-rewrite`. The Java source is on the `main` branch (`remotes/origin/main`).

### What We've Done

We've completed the initial Kotlin rewrite scaffold — all major files exist, the project builds, and core gameplay works. The rewrite uses:
- **Paper 1.21.11 API** (NOT 1.21.1)
- **Nexus DI framework** (@Service, @PostConstruct, @PreDestroy, constructor injection)
- **Kotlin coroutines** with BukkitDispatcher for main-thread work
- **Kotlin Exposed** for database layer (replacing raw JDBC)
- **InvUI** for GUI menus
- **Adventure text / MiniMessage** for all chat formatting
- **Sealed classes** for GamePhase (replacing GameState enum)
- **Coroutine-based game lifecycle** instead of Java's manager-class delegation pattern

### Core User Directives (NON-NEGOTIABLE)

1. **"The front end experience should be identical."** — Player-facing behavior must match Java exactly.
2. **"This is just an underlying arch/framework change, and a transition from java to kotlin."** — No feature changes unless explicitly listed below.
3. **"Mirror the java package structure"** — but we decided to KEEP the modern ports-adapters architecture. The Kotlin package layout (`domain`, `service`, `persistence`, etc.) stays.
4. **"Mirror the methods we are doing, unless I specifically mentioned a change"** — ALL Java public methods must have Kotlin equivalents.

### Explicit Changes From Java (User-Requested)

These are the ONLY behavioral changes from Java:
- **PoisonBomb** uses wind charge projectile (not splash potion)
- **FireBomb** is a pure molotov (fire only, no knockback, no debris)
- **New BombItem** that does the knockback + visual debris (FallingBlock entities)
- **Custom items** moved from `chest.items` to `items` package
- **LumaGuilds** integration instead of KingdomsX (with team-aware PvP override)
- Items package named `items` (NOT `customitems`)

### The Gap Analysis

Read the exhaustive gap analysis at:
`D:/BadgersMC-Dev/LumaSG/.worktrees/kotlin-rewrite/docs/plans/java-kotlin-gap-analysis.md`

This document compares EVERY public method from Java against Kotlin and identifies what's missing. Your first task is to **verify this analysis** by spot-checking several Java files against the Kotlin equivalents. Read both sides and confirm the gaps are real.

### Your Task

1. **Verify the gap analysis** — Read 5-10 Java files (via `git -C "D:/BadgersMC-Dev/LumaSG" show remotes/origin/main:src/main/java/net/lumalyte/FILE`) and their Kotlin equivalents. Confirm the documented gaps are accurate.

2. **Prioritize the gaps** — Separate into:
   - **P0 (Blocks gameplay):** Missing methods that would cause crashes, broken gameplay, or missing player-facing features
   - **P1 (Degrades experience):** Missing methods that degrade the experience but don't block core gameplay
   - **P2 (Nice to have):** Missing methods that are internal/utility and can be deferred

3. **Create an implementation plan** — Write a step-by-step plan to close ALL P0 and P1 gaps. Group related changes into logical batches.

4. **Execute the plan** — Implement the changes batch by batch, committing after each logical group.

### Key Technical Notes

- `GamePhase` is a sealed class: `Waiting` (data object), `Countdown(secondsLeft)`, `Grace(secondsRemaining)`, `Active(secondsRemaining)`, `Deathmatch(secondsRemaining)`, `Ended(winner: UUID?)`
- Game lifecycle is a single `suspend fun runLifecycle()` with coroutine delay loops — NOT separate timer manager classes
- Use `WorldBorder.changeSize(double, ticks)` not deprecated `setSize(double, long)`
- Discord: `discordService?.announce(GameEmbed.gameStarted(...))` and `GameEmbed.gameEnded(...)`
- FallingBlock debris: tagged with "lumasg_debris" metadata, cancelled on EntityChangeBlockEvent
- Coroutine game end uses `GameEndSignal` exception for flow control
- **Use Context7** MCP tool to verify Paper API, Kotlin Exposed, InvUI, or other library APIs before writing code. Don't guess.

### What NOT to Do

- Do NOT restructure the Kotlin packages (keep `domain`, `service`, `persistence`, etc.)
- Do NOT recreate the Java manager-class pattern (keep coroutine lifecycle)
- Do NOT change any of the user's explicit changes listed above
- Do NOT add features that don't exist in Java
- Do NOT use deprecated APIs
- Do NOT skip methods just because they seem redundant — document your reasoning if omitting

### Current Branch State

The branch `feature/kotlin-rewrite` is 18 commits ahead of origin. The project builds successfully with only deprecation warnings. Last commit: `ca18ba0` — moved items from `chest.items` to `items` package.

### The Plan Document

The original rewrite plan is at:
`D:/BadgersMC-Dev/LumaSG/docs/plans/2026-03-14-lumasg-kotlin-rewrite-phase2.md`

All 40 tasks in that plan are complete. We are now in a **gap-closing phase** to ensure 1:1 method parity with Java.
