---
name: brand-content-studio
description: >-
  Compose on-brand narrative content (articles, video scripts, newsletters, and
  one-pagers) for Acme Corp. Use this skill whenever the user asks to draft,
  write, restructure, or polish marketing or editorial content; needs a pull
  quote extracted from source material; wants a topic turned into a mind-map; or
  asks to export a draft to PDF, HTML, or Markdown. Enforces Acme's brand voice
  (beat-structured storytelling, approved vocabulary, legal disclaimers) and the
  per-format length and section rules. Do NOT use for code generation, data
  analysis, or non-Acme brands.
license: Apache-2.0
allowed-tools: Read, Write, Bash
metadata:
  version: 2.3.0
  authors:
    - acme-content-team
  homepage: https://intranet.acme.example/skills/brand-content-studio
  tags: [content, marketing, editorial, branding]
---

# Brand Content Studio

Produce publication-ready, on-brand content for Acme Corp across four formats:
**article**, **video-script**, **newsletter**, and **one-pager**. This file is
the entry point; load the bundled references and run the bundled scripts only
when the task actually needs them (progressive disclosure — keep context lean).

## Folder layout

```
brand-content-studio/
├── SKILL.md                     ← you are here (always loaded when relevant)
├── references/
│   ├── brand-voice.md           ← tone, beat model, do/don't vocabulary
│   ├── format-specs.md          ← per-format section + length rules
│   └── legal-disclaimers.md     ← mandatory disclaimers by content category
├── scripts/
│   ├── scaffold.py              ← build a beat-structured markdown skeleton
│   ├── pick_quote.py            ← extract the best pull quote from source text
│   ├── build_mindmap.py         ← turn abstracts into a Mermaid mind-map
│   └── export.py                ← render a draft to pdf / html / md
└── assets/
    └── templates/
        ├── article.md
        ├── video-script.md
        ├── newsletter.md
        └── one-pager.md
```

## When to use / when NOT to use

**Use when** the request is to create or transform Acme narrative content, pick
a quote, build a topic map, or export a draft.

**Do NOT use when:** the user wants code, spreadsheets/data analysis, content
for a brand other than Acme, or anything that isn't prose/editorial. In those
cases, say so and stop — do not force this skill.

## Core workflow

Follow these steps in order. Skip a step only if the user already supplied its
output.

1. **Classify the format.** Map the request to one of: `article`,
   `video-script`, `newsletter`, `one-pager`. If ambiguous, ask one clarifying
   question, then proceed.

2. **Load the rules for that format.** Read `references/format-specs.md` and
   find the section for the chosen format (required sections, target word
   count, tone overrides). Read `references/brand-voice.md` once per session for
   the global voice rules.

3. **Scaffold.** Run the scaffolder to get a skeleton you then fill in:
   ```bash
   python scripts/scaffold.py --format <format> --title "<title>" \
       --beats "Hook,Concept,Brazilian case,Immediate application,CTA"
   ```
   The script prints a markdown skeleton with one `##` section per beat. Beats
   default to the brand beat model (see `references/brand-voice.md`) when
   `--beats` is omitted.

4. **Draft the prose** into the skeleton, obeying every rule from step 2.
   Open each beat with a concrete hook; never bury the lede.

5. **Pull quote (optional).** If the content cites source material and needs a
   highlighted quote:
   ```bash
   python scripts/pick_quote.py --term "<focus term>" --min-length 40 \
       --file source.txt
   ```
   Heuristic: the longest sentence (≥ min-length) that mentions the term;
   falls back to the longest sentence overall. **Never fabricate a quote** —
   only surface text returned by the script.

6. **Mind-map (optional).** When planning a topic from multiple sources:
   ```bash
   python scripts/build_mindmap.py --term "<topic>" --file abstracts.txt
   ```
   Returns Mermaid `mindmap` source; render it inside a ```mermaid block.

7. **Compliance pass.** Read `references/legal-disclaimers.md`, find the
   disclaimer(s) matching the content category (finance, health, product
   claims), and append them verbatim. This step is mandatory — a draft without
   the required disclaimer is not publishable.

8. **Export (optional).** When the user asks for a deliverable file:
   ```bash
   python scripts/export.py --in draft.md --format pdf   # or html, md
   ```

## Brand voice (summary — full rules in references/brand-voice.md)

- Structure every piece as **beats**: a Hook beat, body beats, a Call-to-Action
  beat. Beats must be self-contained and reorderable.
- Voice: confident, concrete, jargon-free. Prefer the active voice.
- **Forbidden vocabulary** (never use): "synergy", "disruptive", "world-class",
  "leverage" (as a verb), "best-in-class".
- **Mandatory** for product pieces: name the product in the first beat.
- Read `references/brand-voice.md` for the complete vocabulary lists, the
  cargo-register guidance (STRATEGIC / MANAGERIAL / FOUNDATIONAL tone shifts),
  and worked before/after examples.

## Format quick-reference (authoritative version in references/format-specs.md)

| Format | Required sections | Target length |
|---|---|---|
| article | Hook · 3-5 beats · CTA | 800–1200 words |
| video-script | Cold open · beats with [B-roll] cues · Outro | 90–180 s read |
| newsletter | Subject line · Hook · 2-3 beats · Subscribe CTA | 250–450 words |
| one-pager | Headline · Problem · Solution · Proof · CTA | ≤ 1 page |

## Worked example (article)

> **User:** "Write an article about Acme Pay's new scheduled-payments feature."
>
> 1. Format → `article`. 2. Load specs + voice. 3. Scaffold with beats
> `Hook,Problem,How it works,Security,CTA`. 4. Draft prose, naming "Acme Pay"
> in the Hook beat (mandatory). 5. (skipped — no source quote). 6. (skipped).
> 7. Append the finance disclaimer from `legal-disclaimers.md`. 8. Export to
> PDF on request.

## Failure handling

- If a script errors, report the stderr to the user and fall back to doing the
  step manually (e.g., write the skeleton by hand) rather than aborting.
- If `references/` or `scripts/` are missing, degrade gracefully: apply the
  summarized rules in this file and tell the user the full ruleset wasn't
  available.
- If the user's request mixes Acme and a non-Acme brand, handle only the Acme
  portion and flag the rest.
