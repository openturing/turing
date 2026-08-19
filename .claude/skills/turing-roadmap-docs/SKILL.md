---
name: turing-roadmap-docs
description: The Turing-specific rules around the roadmap that roadkeep does not enforce — that ROADMAP.md stays minimal (headings + open lines only, no block descriptions or shipped notes, block drop when a block's last line ships), that new functionality REUSES an existing block (or a generic standing one) instead of minting a new letter, the one-task-one-commit + /loop discipline, the next block letter and docs/last-task.md, and propagating a shipped user-facing task into the separate end-user documentation site (vigletdocs docs-turing) via its COVERAGE.md gate. The four docs/ files (ROADMAP.md, CHANGELOG.md, IMPROVEMENTS.md, STRATEGY.md) are written by the roadkeep tools and never by hand — for adding, shipping, retiring, sectioning, picking or querying a task use the `roadkeep` skill instead. Use when planning where a new feature or a cluster of findings goes, when finishing a task, when about to open a block, or after shipping anything user-facing.
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

`roadkeep lint` is the backstop and runs in CI (`.github/workflows/roadkeep.yml`).
**It is absolute since T967** — the adoption debt was cleared, so nothing is forgiven by
name and a finding is something your branch introduced. `roadkeep lint --baseline HEAD`
still narrows the output to your own writes while you work; the gate itself takes no
baseline, and reintroducing one to turn a red run green hides the next real finding
behind the same list.

**When a local lint and CI disagree, CI's copy decides.** Two roadkeeps are in play: the
plugin this session runs, and the SHA `.github/workflows/roadkeep.yml` pins (T969).
`roadkeep engines` names them. The pinned SHA is the one that fails the build, so a
finding your session reports and the pin does not — or the reverse — is answered by
`/plugin update` to align the local copy, never by editing the four files to satisfy the
older rule. A fix released upstream reaches this repo only when somebody bumps that pin.

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

## ⛔ The roadmap is minimal: a heading and its open lines

`docs/ROADMAP.md` holds **nothing but** the title, its four-line pointer note, the block
headings, the open task lines, and the Non-goals section — which is exactly what
`roadkeep init` scaffolds. It was cut from 308 lines to 98 once; keep it there.

**Everything else has a home that already owns it:**

| Do not put this in `ROADMAP.md` | Where it belongs |
| ------------------------------- | ---------------- |
| What a block is / the problem it solves | `IMPROVEMENTS.md` `§` — the `→ §` pointer on each line already goes there |
| "✅ Block X fully shipped, see CHANGELOG" | `CHANGELOG.md`, which *is* that statement |
| A shipped task, or a note about one | `CHANGELOG.md` (`ship` moves the line for you) |
| "⏸ ON HOLD because …" | the block's `§` intro (or `defer <id> --reason`, which is the verb for a pause) |
| A marker legend / symbol table | `roadkeep.toml` `[markers]` |
| "How to pick work: lowest-numbered …" | `roadkeep brief` / `pick`, which print the reason |
| Cluster sub-labels grouping lines | the `§` outline the lines point at |

**Never write a prose note under a block heading.** No roadkeep verb writes one, which is
the tell: it can only arrive through a hand edit the guard denies, or an approved `Bash`
write that leaves the file's bytes unattested. It also **blocks `block drop`** — `_held`
counts loose prose as work filed under the heading, so a note is what stops a
fully-shipped block from ever leaving the roadmap.

**When a block's last open line ships, drop the block:** `roadkeep block drop <x>` takes
the heading out of `ROADMAP.md` only and reports the ledger as *"N entries filed under it:
history keeps the heading it was filed under"*. That is the routine end of every block —
Blocks **AV, BR, BS, BT** were each sitting in the roadmap as a heading plus a
"fully shipped" paragraph, which is the ledger's sentence written twice. Reopening later is
step 4 of the section below, so dropping costs nothing.

## ⛔ Reuse a block before opening one (default: no new letter)

**A block is a durable theme, not a changelog of one afternoon's findings.** When new
functionality arrives — or a cluster of defects found while shipping something else — the
default is to **file it under a block that already exists**. Opening a new letter is the
exception, and it needs a reason you can state out loud.

This is a rule because the corpus drifted hard the other way. Blocks **BR–BW** are each one
narrow cluster — "Block AV follow-through", "The POST-native default left its consumers
behind", "The quality gate stops at the package boundary", "The search cache is off by a
thousand" — and every one of them had a home that already existed (SDK/client parity, CI &
release gates, search contract, caching). The cost is not cosmetic: the labels ran out of
single letters, `list --block` answers fragment, one subsystem's history is spread across
five headings, and the block list stops being readable as the shape of the plan.

**Before any `add`, run this:**

1. **Read the blocks that already exist — in *both* line files.** `roadkeep list` (and
   `list --block <x>`) for the roadmap, **and the `CHANGELOG.md` headings too**: the roadmap
   is periodically pruned of fully-shipped blocks, so the right home is very often a heading
   only the ledger still declares. **A block missing from `ROADMAP.md` is not unavailable** —
   see the reopen step below.
2. **Ask which subsystem or standing concern owns the work** — never which recent task
   exposed it. "Found while shipping T865" is provenance for the `why` field; it is never a
   block title.
3. **File under the closest existing theme** with `add --block <x>`, and extend that block's
   existing `§` outline with `add --section` / `section add` rather than opening a new
   top-level anchor. One theme, one letter, one rationale subtree.
4. **A pruned block is *reopened*, not replaced.** `roadkeep block add AG --title "<the exact
   title CHANGELOG already records>"` writes the heading into `ROADMAP.md` only and reports
   the ledger as *"already declares it"*. Keep the title byte-identical to the ledger's, so
   one letter is never described two ways. Do **not** mint `AG2`, "AG follow-ups" or a fresh
   letter for more work on a theme the ledger already owns.
5. **A wave large enough to want its own heading takes a sub-label, not a new letter.**
   `[ids]` puts no pattern on a label and the corpus already spells `D.1`, `E.7`, `F.16`,
   `J.9`, so `block add AD.1 --title "…"` files a distinct wave **inside** the theme that
   owns it: the deps, the `list` grouping and the reader's mental model all stay with the
   parent. This is the right answer for the case that produced BR–BW — a second round of
   work on one subsystem.

**Candidate standing homes** (confirm the letter with `roadkeep list` — this table is a
starting point, not the registry):

| The work is about… | Standing home |
| ------------------ | ------------- |
| Bento / admin UI migration & consistency | Block **AG** (AX is closed) |
| Security hardening | Block **AN** |
| Internal + end-user documentation parity | Block **AE**, Block **U** |
| Domain layer, JPA caching & persistence architecture | Block **AF**, Block **AC** |
| LLM provider coverage & cross-vendor parity | Block **AD** (Gemini: **F.16**) |
| SDK / client parity (vanilla, React, react-ui) | Block **S** |
| CI, release & quality gates across `frontend/` | Block **BV** |
| Search result & facet rendering, relevance robustness | Block **AR**, Block **BH** |
| Persona surfaces (match, dialogue, research) | Block **AI**, **AT**, **AU**, **AW** |
| Multi-tenancy / Cloud SaaS | Block **J** (+ `J.x`) |

**A new letter is justified only when all three hold:** the work is a genuinely new
subsystem or product surface with no existing owner; you expect it to keep receiving tasks
*after* this batch; and its title would still make sense a year from now **without naming
the task or incident that exposed it**.

**If nothing fits and it is not durable either — widen it into a generic standing block
instead of a one-shot.** Name the block for the recurring concern, so the *next* cluster of
the same kind lands in it too:

| ❌ one-shot title (a new letter each time) | ✅ what it should have been |
| ----------------------------------------- | -------------------------- |
| "The POST-native default left its consumers behind" | `AV.1` — "Consumer follow-through after a search-contract change" |
| "The quality gate stops at the package boundary" | Block **BV** — "Release & quality gates across the JS workspace" |
| "The search cache is off by a thousand and never evicted on write" | lines under Block **AC** (caching) |
| "Block AV follow-through" | no block at all — each line under the theme it belongs to |

A generic block gets **one** rationale section stating the standing concern once; every
later cluster adds lines under the same letter and extends the same outline. When you catch
yourself writing a block title that contains a task id, "follow-up(s)", "follow-through",
or the past tense of a specific incident, that is the signal to reuse or generalise.

**Reusing a block does not consume the reserved letter.** `docs/last-task.md`'s *"Next block
letter"* stays exactly as it is until a letter is actually spent — do not bump it for a
task filed under an existing block.

## The next block letter, and `docs/last-task.md`

Once the section above has actually justified a new letter,
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
