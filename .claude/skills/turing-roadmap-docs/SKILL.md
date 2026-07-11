---
name: turing-roadmap-docs
description: How to maintain the Turing roadmap/docs — the four docs/ files (ROADMAP.md, CHANGELOG.md, IMPROVEMENTS.md, STRATEGY.md), their single-responsibility split, and the cross-file update rules. Also how to propagate a shipped, end-user-relevant task into the separate end-user documentation site (vigletdocs docs-turing) via its COVERAGE.md gate. Use whenever adding a new task, marking a task shipped, editing any of those four docs, or picking the next T-number (docs/last-task.md). Covers task numbering, non-goals, keeping the files in sync, and the end-user-docs decision.
---

# Roadmap & docs maintenance

## ⛔ READ FIRST — one task, one commit (non-negotiable)

**You may NOT do more than one task before committing.** This is the single most
violated rule, so it is stated up front and it is absolute:

- **One task → one `run-commit.cmd`.** The moment a task is complete and validated,
  do the doc sync + `cd` to the repo root + `run-commit.cmd -m "<ascii title>"`
  **before touching the next task.** Finishing a task means *the commit landed* —
  code + `ROADMAP`/`CHANGELOG`/`last-task.md` sync in that one commit.
- **A multi-task request (e.g. "execute Phase 1 of Block AI", a block, or a list
  of `T<n>`s) is NOT permission to batch.** It is a request to run tasks
  **one-at-a-time, committing after each.** Never implement task 2 while task 1 is
  uncommitted. A single giant diff spanning many tasks with one commit (or no
  commit) at the end is the failure this rule exists to prevent.
- **For any batch of ≥2 tasks you MUST drive it with the `/loop` skill**
  (self-paced): exactly one task per iteration, `run-commit.cmd` at the end of the
  iteration, then let the loop advance. Do not hand-roll a loop that defers commits.
- **Self-check before starting task N+1:** run `git status`/`git log -1`. If the
  previous task's work is not already committed, STOP and commit it first. If you
  are about to edit files for a new task and the working tree still shows the prior
  task's changes, you have already broken this rule — commit now.

The full commit + batch mechanics are rules 7–8 below; the propagation to the
end-user docs repo is a **separate** commit (see "Propagating to end-user
documentation").

---

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

**`docs/last-task.md` is a terse INDEX, not a memory.** It holds the counter, the
next **block letter**, the structural notes, and a **one-line-per-task log** —
nothing more. The header also reserves the next block letter (e.g. `AQ`): the
block letter has the same non-contiguity hazard as the T-number, and because the
roadmap is periodically pruned of fully-shipped blocks, **`CHANGELOG.md` (not
`ROADMAP.md`) is authoritative for the real maximum block** — grep it to confirm,
then bump the letter when you create a block.

- **A log entry is exactly ONE line**, of the form
  `- **T<n> SHIPPED** (Block X §Y — short title) — YYYY-MM-DD.` (truncate right at
  the date). The full implementation story — files, gotchas, test counts — goes in
  the **commit message** and the **`CHANGELOG.md` one-liner**, *never* here.
- **Do not turn a log entry into an implementation report.** Multi-sentence
  paragraphs with gotchas/decisions are the anti-pattern this file exists to avoid
  (it ballooned to ~105 KB once before being compacted back to an index). A
  genuinely reusable gotcha belongs in auto-memory / `agents.md`, not here.

**The cross-file update rules — follow these every time:**

1. **When a task ships:** move its one-liner from `ROADMAP.md` → `CHANGELOG.md`
   (under its block), and **delete** its detailed design subsection from
   `IMPROVEMENTS.md`. `git log` is the history — do **not** leave a shipped
   implementation report in `IMPROVEMENTS.md` or re-accrete one. This is the
   founding rule; the doc grew to 539 KB once by violating it. **Then run the
   end-user-docs decision** (see "Propagating to end-user documentation" below)
   — a shipped user-facing feature that never reaches the docs site is a bug.
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
7. **Commit the instant a task finishes — before starting the next (see the
   ⛔ block at the top).** A task is not "done" until `run-commit.cmd -m
   "<conventional-commits title>"` has landed. Do the doc sync (rules 1–2, bump
   `docs/last-task.md`) **in the same commit** as the code so
   `ROADMAP`/`CHANGELOG`/`last-task.md` never drift from what actually shipped.
   `cd` to the repo root before running it, and keep the `-m` title ASCII. One
   commit per finished task — never one commit covering several tasks, never an
   uncommitted pile at the end.
8. **A batch of ≥2 tasks MUST run under `/loop` — this is mandatory, not a
   suggestion.** When the ask covers multiple tasks (a whole phase/block like
   "execute Phase 1 of Block AI", or an explicit list of `T<n>`s), drive it with
   the `/loop` skill self-paced: **exactly one task per iteration, `run-commit.cmd`
   at the end of that iteration (rule 7), then advance.** Do not implement task 2
   while task 1 is uncommitted, and do not hand-roll a loop that defers commits to
   the end. A single giant uncommitted diff spanning the whole phase is the exact
   failure mode this forbids. Only a genuinely single-task ask skips `/loop`.

## Propagating to end-user documentation

The four `docs/` files above are the **internal** engineering roadmap. The
**end-user** documentation is a *separate Docusaurus repo*:

```
D:\Git\viglet\vigletdocs\vigletdocs.github.io\docs-turing
```

Its gate is [`docs-turing/COVERAGE.md`](file:///D:/Git/viglet/vigletdocs/vigletdocs.github.io/docs-turing/COVERAGE.md)
— a matrix mapping every shipped `CHANGELOG.md` block/feature to **either** an
end-user page (with anchor) **or** an explicit "deliberately internal — no public
page" row. A shipped user-facing subsystem with no page is an **orphan** (🔴) and
counts as a bug in the docs. (`_documentation-update-plan.md` is retired — do not
resurrect it; `COVERAGE.md` is the only living gate.)

**Every time a task ships, after the internal doc sync (rule 1), run this decision:**

1. **Is it user-facing?** Ask: would someone *using* Turing (admin configuring a
   feature, developer calling the API/SDK, end user in chat/search) do something
   differently because this shipped? If **no** — it's internal engineering,
   repo/licensing, a shared-library lift, or a marketing-site concern — it does
   **not** get an end-user page. Account for it with a row in `COVERAGE.md`'s
   **"Deliberately internal — no public page"** table (with a one-line _why_), and
   stop. Don't invent thin pages for internal refactors.
2. **If yes, find its home.** Most features extend an existing page (a new section
   or anchor on `ai-agents.md`, `rag.md`, `chat-flow.md`, `llm-instances.md`,
   etc.) rather than warranting a brand-new page. Fold into the closest existing
   page (mark it 🧩 in `COVERAGE.md`) unless the surface is genuinely new and
   sizeable enough to stand alone (then a new `.md` + a ✅ row).
3. **Write for the user, not the commit.** The end-user docs explain *what the
   feature does and how to use it* — configuration keys, admin UI steps, API/SDK
   calls, examples. They are **not** a changelog or design-rationale dump. Keep
   the tense/voice of the surrounding page; match its structure (most pages have
   an intro, a config/reference section, and examples). Never paste
   `IMPROVEMENTS.md` rationale verbatim.
4. **Update `COVERAGE.md` in the same pass.** Add or update the feature's row
   (page + anchor + ✅/🧩/🔒 status) so the matrix never drifts from what shipped.
   Confirm the "Orphans" section still reads **None**.
5. **Product name & brand.** User-facing text says **Viglet Turing ES**; public
   links point at the `openviglet/turing-ce` repo (the public one), never the
   private `openviglet/turing`.

**Commit the docs separately.** `docs-turing` lives in a *different git repo*, so
its changes are their own commit in that repo (`cd` into the vigletdocs repo
first — see the `run-commit.cmd` "stages relative to CWD" rule). The Turing-repo
commit (rule 7) carries the code + internal `docs/` sync; the vigletdocs commit
carries the end-user page + `COVERAGE.md`. Keep `-m` titles ASCII.

**Batch note.** When shipping a run of tasks under `/loop` (rule 8), the
end-user-docs decision runs per task, but it's fine to accumulate several small
page edits and land them in one focused vigletdocs commit at a natural boundary
(e.g. once a whole block ships) rather than a trivial commit per task — as long as
`COVERAGE.md` never claims a page that isn't there yet.
