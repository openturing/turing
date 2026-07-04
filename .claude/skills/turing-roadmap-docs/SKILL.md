---
name: turing-roadmap-docs
description: How to maintain the Turing roadmap/docs — the four docs/ files (ROADMAP.md, CHANGELOG.md, IMPROVEMENTS.md, STRATEGY.md), their single-responsibility split, and the cross-file update rules. Use whenever adding a new task, marking a task shipped, editing any of those four docs, or picking the next T-number (docs/last-task.md). Covers task numbering, non-goals, and keeping the files in sync.
---

# Roadmap & docs maintenance

The roadmap is **split across four files in `docs/`** that must be kept in sync.
Each has one job — never duplicate content between them, and when you touch one,
check whether a sibling needs updating:

| File                                           | Single responsibility                                                                                                                           | Granularity               |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------- |
| [`docs/ROADMAP.md`](docs/ROADMAP.md)           | **Task status** — the _only_ source of truth for what's done/active. Active backlog only (📋 designed · 💭 idea · ⏳ partial · 🛠 in-progress). | one line per task         |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md)       | What has **shipped** — a searchable index; `git log` is authoritative for detail.                                                               | one line per shipped task |
| [`docs/IMPROVEMENTS.md`](docs/IMPROVEMENTS.md) | **Design rationale** (the what/why) for _unshipped_ sections only. No status tables, no shipped implementation reports.                         | prose per active section  |
| [`docs/STRATEGY.md`](docs/STRATEGY.md)         | Business/positioning decisions (pricing, markets, deprecations, bets).                                                                          | prose                     |

**Task numbering — the next free `T<n>` lives in [`docs/last-task.md`](docs/last-task.md).**
Read it before adding a task, use `T<n+1>`, then bump the counter + append a log
line. T-numbers are **non-contiguous across blocks**, so never infer the next
number from a block's header range or a `git log` scan — that mis-numbers tasks
and collides with already-shipped ones (it happened: Block N shipped T337–T341
_inside_ the Block J band).

**The cross-file update rules — follow these every time:**

1. **When a task ships:** move its one-liner from `ROADMAP.md` → `CHANGELOG.md`
   (under its block), and **delete** its detailed design subsection from
   `IMPROVEMENTS.md`. `git log` is the history — do **not** leave a shipped
   implementation report in `IMPROVEMENTS.md` or re-accrete one. This is the
   founding rule; the doc grew to 539 KB once by violating it.
2. **When you add a new task:** add the one-liner to `ROADMAP.md` (with `deps`
   - a `→ §x.y` pointer) and, if it needs design, add the rationale subsection
     to `IMPROVEMENTS.md`. Status lives **only** in `ROADMAP.md` — don't put
     ✅/📋 markers inside `IMPROVEMENTS.md` prose.
3. **Status belongs to exactly one file.** If you find a status marker in
   `IMPROVEMENTS.md` that disagrees with `ROADMAP.md`/`CHANGELOG.md`, the
   roadmap files win — fix or remove the stale marker.
4. **Keep entries terse.** A task line is _what + why + pointer_ (~1 sentence).
   Implementation detail goes in code/commits, not the table cell. Never put
   multi-paragraph release notes inside a markdown table cell.
5. **Strategy ≠ backlog.** Pricing/market/deprecation discussion goes in
   `STRATEGY.md`, never as a numbered task.
6. **Non-goals are binding.** `ROADMAP.md` → "Non-goals" + `IMPROVEMENTS.md`
   §I.6/§I.7 list things deliberately _not_ to build — check them before
   proposing new work.
7. **Always commit when a task finishes.** As soon as a task is complete and
   validated, run `run-commit.cmd -m "<conventional-commits title>"` (one commit
   per finished task — see the global `run-commit.cmd` guidance). Do the doc sync
   (rules 1–2, bump `docs/last-task.md`) **in the same commit** as the code so
   `ROADMAP`/`CHANGELOG`/`last-task.md` never drift from what actually shipped.
   `cd` to the repo root before running it, and keep the `-m` title ASCII.
8. **More than one task in the batch → drive it with `/loop`.** When the ask
   covers multiple tasks (a whole phase/block, or an explicit list of `T<n>`s),
   run them under the `/loop` skill self-paced: one task per iteration, commit at
   the end of each (rule 7), then let the loop advance to the next. This keeps
   each task atomic and reviewable instead of one giant uncommitted diff. A
   single task doesn't need `/loop`.
