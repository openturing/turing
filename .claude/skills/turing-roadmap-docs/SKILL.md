---
name: turing-roadmap-docs
description: The Turing-specific rules around the roadmap that roadkeep does not enforce — the one-task-one-commit + /loop discipline, the next block letter and docs/last-task.md, and propagating a shipped user-facing task into the separate end-user documentation site (vigletdocs docs-turing) via its COVERAGE.md gate. The four docs/ files (ROADMAP.md, CHANGELOG.md, IMPROVEMENTS.md, STRATEGY.md) are written by the roadkeep tools and never by hand — for adding, shipping, retiring, sectioning, picking or querying a task use the `roadkeep` skill instead. Use when finishing a task, opening a block, or after shipping anything user-facing.
---

# Turing roadmap & docs — what roadkeep does not cover

## The four `docs/` files belong to roadkeep

`roadkeep.toml` at the repo root declares `docs/ROADMAP.md`, `docs/CHANGELOG.md`,
`docs/IMPROVEMENTS.md` and `docs/STRATEGY.md` as governed files, with Turing's own
prefix (`T`), markers, outline pointers and limits. **Never edit them by hand** — the
`Edit`/`Write` guard denies it and names the command to call instead.

The write path and the query surface are the **`roadkeep` skill**, which ships with the
tool: `add`, `block add`, `claim`, `status`, `amend`, `ship`, `retire`, `defer`, `section
add`, `non-goal add`, plus `brief`, `pick`, `list`, `deps` and `lint`. Prefer the
`mcp__roadkeep__*` tools where they are available. Everything this file used to spell out
— the single-responsibility split, moving a line to the ledger on ship, deleting the
shipped task's rationale section, keeping a line to one sentence, the binding non-goals —
is enforced there, at the point the text is created. **It is deliberately not repeated
here**: a rule written in two files is a rule two files can disagree about.

Two consequences worth stating, because they replace habits this file used to teach:

- **The next `T<n>` is derived, never read off a counter.** `roadkeep next-id` scans the
  roadmap, the ledger and `agents.md`, so the non-contiguity hazard is the tool's problem
  now. Do not infer a number from a block's header range, and do not trust a hand-kept
  counter over `next-id`.
- **The pointer into `IMPROVEMENTS.md` is derived and has to resolve.** `add --section` /
  `section add` writes the rationale in the same transaction as the line.

`roadkeep lint` is the backstop and runs in CI (`.github/workflows/roadkeep.yml`) against
a baseline, so it fails on what a branch added and forgives the corpus's standing
adoption debt. Run `roadkeep lint --baseline HEAD` after your writes to see only yours.

## ⛔ One task, one commit (non-negotiable)

**You may NOT do more than one task before committing.** This is the single most violated
rule, so it is stated up front and it is absolute:

- **One task → one `run-commit.cmd`.** The moment a task is complete and validated, `cd`
  to the repo root and run `run-commit.cmd -m "<ascii conventional-commits title>"`
  **before touching the next task.** Finishing a task means *the commit landed* — the code
  and **what `ship` wrote** in the same commit, so the docs never describe a state that
  did not ship.
- **A multi-task request (e.g. "execute Phase 1 of Block AI", a block, or a list of
  `T<n>`s) is NOT permission to batch.** It is a request to run tasks **one-at-a-time,
  committing after each.** Never implement task 2 while task 1 is uncommitted. A single
  giant diff spanning many tasks with one commit (or none) at the end is the failure this
  rule exists to prevent.
- **For any batch of ≥2 tasks you MUST drive it with the `/loop` skill** (self-paced):
  exactly one task per iteration, `run-commit.cmd` at the end of the iteration, then let
  the loop advance. Do not hand-roll a loop that defers commits.
- **Self-check before starting task N+1:** run `git status`/`git log -1`. If the previous
  task's work is not already committed, STOP and commit it first.

The propagation to the end-user docs repo is a **separate** commit — see below.

## The next block letter, and `docs/last-task.md`

`roadkeep block add <label> --title "…"` is the only thing that declares a block heading,
and it takes the **label** as its argument — that letter is the one fact the tool cannot
derive for you.

**[`docs/last-task.md`](docs/last-task.md) reserves it** (e.g. `BX`) along with the
structural notes and the one-line-per-task log. The block letter has the same
non-contiguity hazard the T-number had, and because the roadmap is periodically pruned of
fully-shipped blocks, **`CHANGELOG.md` (not `ROADMAP.md`) is authoritative for the real
maximum block** — `roadkeep list` to confirm, then bump the letter when you open a block.

`last-task.md` is a terse **INDEX, not a memory**, and it is not a governed file — it is
yours to edit:

- **A log entry is exactly ONE line**, of the form `- **T<n> SHIPPED** (Block X §Y — short
  title) — YYYY-MM-DD.` (truncate right at the date). The full implementation story goes
  in the **commit message** and the ledger entry `ship --why` wrote, *never* here.
- **Do not turn a log entry into an implementation report.** Multi-sentence paragraphs
  with gotchas are the anti-pattern this file exists to avoid (it ballooned to ~105 KB
  once before being compacted back to an index). A genuinely reusable gotcha belongs in
  auto-memory / `agents.md`.
- **The task counter in its header is no longer the source of truth** — `roadkeep next-id`
  is. Do not hand-bump a number and then trust it over the tool.

## Propagating to end-user documentation

The four `docs/` files are the **internal** engineering roadmap. The **end-user**
documentation is a *separate Docusaurus repo*:

```
D:\Git\viglet\vigletdocs\vigletdocs.github.io\docs-turing
```

Its gate is [`docs-turing/COVERAGE.md`](file:///D:/Git/viglet/vigletdocs/vigletdocs.github.io/docs-turing/COVERAGE.md)
— a matrix mapping every shipped `CHANGELOG.md` block/feature to **either** an end-user
page (with anchor) **or** an explicit "deliberately internal — no public page" row. A
shipped user-facing subsystem with no page is an **orphan** (🔴) and counts as a bug in
the docs. (`_documentation-update-plan.md` is retired — do not resurrect it;
`COVERAGE.md` is the only living gate.)

**Every time a task ships, after `roadkeep ship`, run this decision:**

1. **Is it user-facing?** Ask: would someone *using* Turing (admin configuring a feature,
   developer calling the API/SDK, end user in chat/search) do something differently
   because this shipped? If **no** — it's internal engineering, repo/licensing, a
   shared-library lift, or a marketing-site concern — it does **not** get an end-user
   page. Account for it with a row in `COVERAGE.md`'s **"Deliberately internal — no
   public page"** table (with a one-line _why_), and stop. Don't invent thin pages for
   internal refactors.
2. **If yes, find its home.** Most features extend an existing page (a new section or
   anchor on `ai-agents.md`, `rag.md`, `chat-flow.md`, `llm-instances.md`, etc.) rather
   than warranting a brand-new page. Fold into the closest existing page (mark it 🧩 in
   `COVERAGE.md`) unless the surface is genuinely new and sizeable enough to stand alone
   (then a new `.md` + a ✅ row).
3. **Write for the user, not the commit.** The end-user docs explain *what the feature
   does and how to use it* — configuration keys, admin UI steps, API/SDK calls, examples.
   They are **not** a changelog or design-rationale dump. Keep the tense/voice of the
   surrounding page; match its structure (most pages have an intro, a config/reference
   section, and examples). Never paste `IMPROVEMENTS.md` rationale verbatim.
4. **Update `COVERAGE.md` in the same pass.** Add or update the feature's row (page +
   anchor + ✅/🧩/🔒 status) so the matrix never drifts from what shipped. Confirm the
   "Orphans" section still reads **None**.
5. **Product name & brand.** User-facing text says **Viglet Turing ES**; public links
   point at the `openviglet/turing-ce` repo (the public one), never the private
   `openviglet/turing`.

**Commit the docs separately.** `docs-turing` lives in a *different git repo*, so its
changes are their own commit in that repo (`cd` into the vigletdocs repo first — see the
`run-commit.cmd` "stages relative to CWD" rule). The Turing-repo commit carries the code
+ what `ship` wrote; the vigletdocs commit carries the end-user page + `COVERAGE.md`.
Keep `-m` titles ASCII.

**Batch note.** When shipping a run of tasks under `/loop`, the end-user-docs decision
runs per task, but it's fine to accumulate several small page edits and land them in one
focused vigletdocs commit at a natural boundary (e.g. once a whole block ships) rather
than a trivial commit per task — as long as `COVERAGE.md` never claims a page that isn't
there yet.
