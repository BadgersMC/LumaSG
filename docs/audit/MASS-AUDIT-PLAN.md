# LumaSG Mass Audit — Methodology & Batch Plan

Findings-only audit driven by ONE Hermes coordinator running **DeepSeek v4 Flash**
(`deepseek/deepseek-v4-flash`, 1M ctx) with **6 subagents**, one per batch below.
**No source files are modified.** Each subagent audits one disjoint batch and writes a
single report at `docs/audit/<batch>.md`. The coordinator commits all six reports plus a
brief `docs/audit/COORDINATOR-SUMMARY.md` (counts per severity per batch), then pushes
branch `fix/audit-lumasg` to the `fork` remote. The operator (Claude, locally) fetches
the branch, triages false positives, and synthesizes a consolidated master report for one
docs-PR to `BadgersMC/LumaSG`.

Repo facts: 86 main Kotlin files under `src/main/kotlin/net/lumalyte/lumasg/`, 29 test files.

## What every subagent hunts for (priority order)

1. **Correctness** — null/async misuse, Bukkit main-thread violations (world/entity access
   off-thread), race conditions in game state transitions, wrong edge cases, broken
   invariants, resource leaks (unclosed `Statement`/`ResultSet`/`Connection`/streams).
2. **Security** — SQL injection (string-concatenated queries vs parameterized), unsafe
   deserialization (item NBT/serialized stacks), permission/command bypass, secret leakage
   (Discord tokens/webhooks), path traversal in config or arena file handling.
3. **Game-integrity** — duplication exploits (chest refill, death drops, spectator
   inventory), reward/statistics manipulation, state desync between game phase and player
   state.
4. **Test-coverage gaps** — name the specific untested behavior and the test that should
   exist (only 29 test files vs 86 main).

The coordinator also runs `detekt` once for free quality signal and folds real hits into
the relevant batch report.

## Severity rubric

| Sev | Meaning |
|-----|---------|
| crit | Exploitable security hole or data-loss/corruption bug reachable in normal use |
| high | Logic bug that breaks a feature, or an exploit a player can trigger in-game |
| med  | Edge-case bug, resource leak under load, missing test for important logic |
| low  | Style/quality, defensive-coding nit, minor untested branch |

Report format, one section per finding:

```
### [SEV: crit|high|med|low] <relative/path>.kt:<line> — <one-line title>
**What:** <the problem, concretely>
**Why it matters:** <impact / exploit / failure mode>
**Suggested fix:** <direction, not full code>
**Confidence:** high | med | low
```

## Batches (disjoint file sets, 86 files, 6 subagents)

All packages relative to `src/main/kotlin/net/lumalyte/lumasg/`.

| # | Batch (report file) | Packages | Files |
|---|---------------------|----------|-------|
| 1 | `audit-game` | `game/**` | 16 |
| 2 | `audit-items` | `items/**`, `chest/**` | 13 |
| 3 | `audit-gui-commands` | `gui/**`, `commands/**`, `listeners/**` | 14 |
| 4 | `audit-persistence` | `persistence/**`, `statistics/**`, `config/**`, `service/**` | 11 |
| 5 | `audit-domain-util` | `domain/**`, `permissions/**`, `util/` (non-cache) | 17 |
| 6 | `audit-hooks-misc` | `hooks/**`, `discord/**`, `debug/**`, `util/cache/**`, root files | 15 |

Batch 4 carries the SQL-injection focus; batches 1–2 carry the dupe-exploit focus;
batch 6 carries the secret-leakage focus (Discord integration).

Each subagent receives its exact file list (the coordinator enumerates the package globs
at runtime with `git ls-files`) and writes ONLY its own `docs/audit/<batch>.md`. Subagents
share the read-only working tree — disjoint output files mean no collisions.

## Operator workflow (single dispatch)

1. **Prereqs (one-time):**
   - Clone `BadgersMC/LumaSG` to `/opt/data/LumaSG` in the Hermes container.
   - Create fork `Hermes-Enthusia/LumaSG` on GitHub; add it as the `fork` remote on the
     box (push-auth via the Hermes PAT). Verify `git push fork` works and
     `git push origin` 403s.
   - Push this plan doc to BadgersMC origin on branch `docs/audit-plan`
     (`dispatch.sh` reads it via `git show origin/docs/audit-plan:docs/audit/MASS-AUDIT-PLAN.md`).
2. `HERMES_REPO=/opt/data/LumaSG dispatch.sh --job audit-lumasg --docs-branch docs/audit-plan
   --plan-path docs/audit/MASS-AUDIT-PLAN.md --prompt /tmp/audit-lumasg.txt
   --model deepseek/deepseek-v4-flash` (branch on box becomes `fix/audit-lumasg`).
3. `poll.sh --job audit-lumasg` — done when `fix/audit-lumasg` appears on the fork.
4. Locally: `git fetch hermes refs/heads/fix/audit-lumasg:refs/heads/hermes-audit`,
   read all six `docs/audit/*.md`.
5. Triage (expect 30–50% false positives from DeepSeek, especially "SQL injection" claims
   on parameterized queries), dedupe, rank → `docs/audit/MASS-AUDIT-<date>.md`, one PR.

## Coordinator rules (baked into the dispatch prompt)

- Findings-only: NEVER modify any file outside `docs/audit/`.
- Spawn 6 subagents, one per batch; each writes only its own report file.
- No gradle build gate (nothing to compile-verify) — but DO run detekt once.
- Push to the `fork` remote ONLY. Finish by printing `AUDIT-LUMASG_DONE` plus per-batch
  finding counts; print `AUDIT-LUMASG_HALT` with the error if blocked 3 times.
