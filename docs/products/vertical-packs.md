# Sub-Product Brief — Vertical Packs

> **One line:** pre-built **persona + flow + tools + golden-set** bundles for a
> specific vertical (Education, Financial Services), sold as digital products so a
> customer goes from zero to a tuned agent in minutes.
>
> Status: **viable as an add-on / lead-gen, not a recurring engine on its own**
> (see §5). Ties to STRATEGY.md §VIII.3 (verticalize). Best monetized *through*
> [Agent Eval](agent-eval.md).

---

## 1. The idea

Today Turing is "an agent platform for anything" and wins no category. A Vertical
Pack is an opinionated bundle for one market:

- A **persona** tone-calibrated to the vertical (`TurPersona`, `TurPersonaTone`,
  `TurPersonaLanguageStyle` already model this).
- **Flow templates** (e.g. *Educational Lead Capture*, *Mortgage Pre-Qualification*,
  *Patient Intake*).
- **Pre-built Custom Tools** (DSpace adapter for academia, Bloomberg/Quandl for
  finance, FHIR for healthcare).
- **Compliance defaults** (LGPD-aggressive, PII redaction by default).

## 2. Who buys it

A team that picked Turing (or is evaluating it) for a known vertical and wants a
running start instead of authoring personas/flows from scratch. Low price point,
self-serve, impulse-buy territory.

## 3. What's reused

Almost everything exists — a pack is *configuration + content*, not new engine
code: `TurPersona` family, flow definitions, Custom Tools, and the compliance
knobs. A pack is essentially an exportable JSON bundle + documentation.

## 4. Monetization

Pure digital product — **zero infra**: sell as a one-off download on a marketplace
(Gumroad / GitHub Marketplace), $X per pack. New revenue comes only from shipping
*new* packs, because each pack is bought once.

## 5. The honest limitation — and the fix

A pack alone **re-creates the "install and forget" disease**: the customer buys
it, installs it, and never needs you again. No recurrence. On the matrix in
[README.md](README.md#the-matrix) it scores zero on the recurring-need axis.

**The fix — bundle it with [Agent Eval](agent-eval.md):** the *same* vertical
content does double duty. The conversations that prove the pack works become the
**golden-set / eval pack** for that vertical. Sold together:

> *"Here's a tuned Education agent (one-off pack) — and a subscription that proves
> it keeps working as models change (Agent Eval + the Education golden-set)."*

That turns a one-shot download into the front door of a subscription, and gives
the vertical content a reason to be re-run forever. **Don't ship Vertical Packs
as a standalone bet; ship them as the content layer that feeds Agent Eval and as
low-friction lead-gen for the verticalization in STRATEGY.md §VIII.3.**

## 6. Why it's still worth doing

It's the cheapest possible product (no engine, no infra), it sharpens the
positioning (§VIII.8's "Education + Finance" message), and the content is reusable
three ways: as a sellable pack, as an eval golden-set, and as a demo/case-study
asset for distribution — the actual bottleneck.
