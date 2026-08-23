# Bento conventions — Turing's half

**The authoring contract is the design system's:**
[`docs/BENTO-AUTHORING.md`](https://github.com/openviglet/viglet-design-system/blob/2026.3/docs/BENTO-AUTHORING.md)
(local checkout: `viglet-design-system/docs/BENTO-AUTHORING.md`). It carries the
three page shapes, the identity-in-the-hero and save-bar-morph rules, what the
package will not hold, colour as a token, the a11y baseline, the responsive grid
and the testing rules. Read it first; it names no product, which is the point.

This file used to be a copy of all that (T550), addressed to this application.
Two copies of a contract drift exactly as two copies of a component do — T999
made it a pointer. What stays below is only what is genuinely about **this
product's routes, privileges and entities**, which the package cannot state.

## 1. Which folder a component goes in

- Shared bento chrome is the package's, reached through the shims in
  [`components/bento/`](.) — a page imports `@/components/bento` and gets the
  package's component under its familiar name.
- This product's **commercial** tiles — activation card, quota banner, Viglet
  activation dialog, no-LLM state, first-run tour — live in
  [`components/turing-bento/`](../turing-bento/) (T995). A new tile whose subject
  is what a licence buys goes there, never in `components/bento/`.

## 2. Making a surface reachable

- Routes are declared in [`app/routes/bento.routes.tsx`](../../app/routes/bento.routes.tsx)
  with `React.lazy()`.
- A surface appears on the rail and in `⌘K` by having its entry in
  [`bento-nav.config.ts`](./bento-nav.config.ts) — this product's array over the
  package's `BentoNavItem` schema. Fill in `bentoRoute`; `fallbackRoute` is where
  a reader goes while a surface has no bento route of its own.
- `TurBentoNavItem` carries the two things only this product can answer:
  `privilege` (the synthetic `__admin__` gates admin-only areas) and
  `requiresLlm`. The rail and the palette are handed the **already filtered**
  list — visibility is never the package's decision.

## 3. i18n — this product's namespaces

- Reuse the namespaces the bento pages already pull: `home.*`, `llm.*`,
  `aiAgent.*`, `forms.common.*`, `forms.formActions.*`. Do **not** mint a
  parallel `bento.<entity>.*` key when a console key already says the same thing
  — the console and bento render the same entities.
- Bento-chrome-only strings (nav, palette) live in the `bento.*` namespace,
  `i18n/locales/{en,pt}/bento.json`. Add every new key to **both** `en` and `pt`.
- Always pass a default via the **object** form: `t("key", { defaultValue: "…" })`.
  The package's contract says the same; here it is also load-bearing for the
  suite, because the i18n mock in [`src/test/setup.ts`](../../test/setup.ts) only
  honours the object form and renders the raw key for a string-form call.

## 4. The own-hero forms in this product

These render `BentoFormHero` as the first child inside the `<form>` they submit,
per the package's morph rule: agent settings, intent settings, admin
user/role/group, global settings, the SN sub-forms and the custom-facet item.
Since T738 there is no hand-wired two-piece form left here — do not reintroduce
one. Pattern reference for a detail screen:
[`bento.llm.instance.page.tsx`](../../app/bento/llm/bento.llm.instance.page.tsx).

## 5. Tests — the parts specific to this app

The package's contract covers the general rules. Here:

- Privilege and admin behaviour is driven by mocking `@/contexts/user.context`;
  use `vi.hoisted` for a mutable user holder, because `vi.mock` is hoisted above
  every declaration in the file.
- The i18n passthrough in `src/test/setup.ts` means an assertion reads either the
  object-form `defaultValue` or, for a `t(key)` call with no default, the **key**.
- A shim is asserted by **identity**, not by rendering: `expect(SectionCard).toBe(AdaptiveSectionCard)`.
  A component that renders is no evidence it is the shared one.

## 6. A rule the package's contract does not state yet

- **A back-link eyebrow leads with a left arrow** (`IconArrowLeft`), so it reads
  as the way back to the list rather than as a label. `BentoEntityShell` does this
  for you — pass just the label. Hand-rolling a `BentoHero` whose eyebrow is a
  back-link means including `<IconArrowLeft size={14} aria-hidden />` inside the
  `<Link>` (`inline-flex items-center gap-1`). Do not add it to a
  non-navigational eyebrow.

  This is a rule about the package's own components, so it belongs in the
  package's contract; it is kept here until it is moved there rather than dropped
  on the way past. Filed as **VDS75** in `viglet-design-system` — when that
  lands, this whole section goes.
