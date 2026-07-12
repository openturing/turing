# Bento conventions (T550)

Lock these rules **before** Phases 1–4 fan out ~20 screens, so they don't drift.
The shared scaffold (`BentoEntityShell`, `BentoListPage`, `BentoEntityTile`,
`BentoNavRail`, `BentoCommandPalette`) already follows them; new screens must too.

## 1. Structure — thin config, not bespoke pages

- A **detail** screen = `BentoEntityShell` (identity hero + save-bar morph +
  delete) wrapping a form render-prop child that groups fields in
  `BentoFormSection`s and keeps its own fixed `BentoSaveBar`. Wire the query
  hooks in the page; put no Shell mechanics there. Pattern reference:
  [`bento.llm.instance.page.tsx`](../../app/bento/llm/bento.llm.instance.page.tsx).
- A **list** screen = one `BentoListPage` call with a `renderTile`. Use
  `BentoEntityTile` for the common icon-chip + status-pill + title + meta shape;
  only hand-roll a tile when the entity genuinely needs a different layout.
- Identity fields (title / description / icon / enabled) live in the **hero**,
  never as form `FormField`s — the shell owns them.
- A **back-link eyebrow leads with a left arrow** (`IconArrowLeft`) so it reads as
  the way back to the list — a plain link is easy to miss. `BentoEntityShell`
  renders this automatically (it wraps `eyebrow` in a Link to `listRoute` with the
  arrow), so pass just the label. When hand-rolling a `BentoHero` whose eyebrow is a
  back-link, include `<IconArrowLeft size={14} aria-hidden />` inside the `<Link>`
  (`inline-flex items-center gap-1`). Don't add it to non-navigational eyebrows.
- The save bar always uses the **hero → sticky bar morph** (same as
  `BentoEntityShell`), never a hand-rolled `<div className="sticky …">` wrapper
  or a permanently-visible bar. The controls live in the hero at the top and a
  fixed bar fades in only once the hero scrolls away — so nothing is duplicated
  on screen and the stick appears exactly when the title leaves.
  - **`BentoEntityShell`** detail pages get this for free (it owns the hero).
  - A **settings / non-shell form** (agent settings, intent settings, admin
    user/role/group, global settings, SN sub-forms, custom-facet item) composes
    it from shared pieces: the hero-anchored actions wrapped in `bento-fade-out`
    (or, when the hero is external, a `bento-fade-out`-wrapped `BentoSaveBar`
    placed right after it), followed by **`BentoScrollSaveBar`** — which renders
    the sentinel + `bento-save-bar-spacer` + the fixed `bento-fade-in`
    `BentoSaveBar`, all driven by the shared **`useBentoScrollFade`** hook
    (extracted from `BentoEntityShell` so both paths share one mechanic).
  - Keep the **destructive** action (delete dialog) in the hero/fade-out copy
    only — never pass it to `BentoScrollSaveBar`, since a controlled dialog
    rendered twice would open two modals at once. The sticky copy carries just
    Save/Cancel.

## 2. i18n — reuse keys, object-form defaults

- Reuse the existing namespaces the bento pages already pull: `home.*`,
  `llm.*`, `aiAgent.*`, `forms.common.*`, `forms.formActions.*`. Do **not** mint
  a parallel `bento.<entity>.*` key when a console key already says the same
  thing — the console and bento render the same entities.
- Bento-chrome-only strings (nav, palette) live in the `bento.*` namespace
  (`i18n/locales/{en,pt}/bento.json`).
- Always pass a default via the **object** form: `t("key", { defaultValue: "…" })`,
  **not** the string form `t("key", "…")`. The test i18n mock only honours the
  object form (string-form calls render the raw key), and the object form is the
  unambiguous i18next signature. Add every new key to **both** `en` and `pt`.

## 3. a11y baseline

- **Icon-only controls** get an `aria-label` (rail links, palette trigger,
  status pill, actions menu). Decorative glyphs get `aria-hidden`; convey their
  meaning with an adjacent `sr-only` span (see the palette's "Opens in console"
  indicator) — screen readers don't reliably announce `aria-label` on a bare
  `<svg>`, and RTL's `getByLabelText` won't match it either.
- **Keyboard**: inline-edit must be reachable and committable by keyboard; the
  palette is arrow-key + Enter navigable; active list rows use
  `aria-current="page"` (rail) / `aria-selected` (palette options).
- **ARIA values must be string literals** for the jsx-a11y lint:
  `aria-expanded="true"`, `aria-selected={active ? "true" : "false"}` — a bare
  attribute or a `{boolean}` expression is flagged.
- **Reduced motion**: every bento animation (`.bento-tile`, `.bento-pulse`,
  `.bento-blob*`, `.bento-shell-header`, `.bento-save-bar-enter`) is disabled
  under `@media (prefers-reduced-motion: reduce)` in `bento.styles.css`. Any new
  keyframe animation must be added to that guard.

## 4. Responsive

- The grid is `grid-cols-2 md:grid-cols-4 lg:grid-cols-6` with `col-span-2`
  tiles (featured = `row-span-2`). Keep tile spans in multiples of 2 so they
  reflow cleanly at every breakpoint.
- The `BentoNavRail` is desktop-only (`hidden md:flex`) and the shell reserves
  its gutter with `md:pl-16`; on mobile, navigation is the header `⌘K` trigger
  + global shortcut. Don't add a second always-visible nav that eats mobile
  width.

## 5. Tests

- Every shared component has vitest + RTL coverage under
  [`__tests__/`](./__tests__/); regressions surface centrally, once.
- Router-dependent components render inside `<MemoryRouter>`; mock `useNavigate`
  to assert navigation. Privilege/admin behavior is driven by mocking
  `@/contexts/user.context` (use `vi.hoisted` for a mutable user holder).
- Assert on the readable `defaultValue` strings (object-form `t`) or on i18n
  **keys** for `t(key)` calls with no default — matching the passthrough mock in
  `src/test/setup.ts`.
