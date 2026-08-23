---
name: turing-bento-ui
description: How to build and fix pages in the Turing "bento" admin UI (the frosted-glass iOS-flavored redesign under /bento) so they match the gold-standard pattern — the BentoHero header, BentoEntityShell identity + scroll-linked save-bar morph (and BentoFormHero, the single drop-in that gives that same hero→sticky-save-bar morph to own-hero form pages like the persona editor), BentoFormSection frosted cards with hover-lift animation, BentoListPage/BentoEntityTile mosaic, and the pill tab-bar for multi-section/functional surfaces. Explains the shared scaffold in components/bento/, the bento-tile/bento-glass animation classes, and the exact swaps to convert a page that still reuses console chrome (StickyPageHeader / SectionCard / SubPage / GridList / useSidebar) into a proper bento page. Use whenever creating a new /bento page, or when a bento page "doesn't look bento" (no frosted hover cards, wrong header, a leftover console sidebar) and needs to be brought up to the standard.
---

# Bento UI — build & fix pages to the standard

The **bento UI** is Turing's frosted-glass, iOS-flavored admin redesign living under
`/bento` (routes in [`app/routes/bento.routes.tsx`](../../frontend/apps/turing-app/src/app/routes/bento.routes.tsx),
shell in `app/bento/bento.root.page.tsx`). Every surface is a thin page built from
the shared scaffold in
[`components/bento/`](../../frontend/apps/turing-app/src/components/bento/) — never a
bespoke layout, and never the console chrome.

> **Gold standard = the LLM instance page.** When in doubt, copy
> [`app/bento/llm/bento.llm.instance.page.tsx`](../../frontend/apps/turing-app/src/app/bento/llm/bento.llm.instance.page.tsx)
> + [`bento.llm.instance.form.tsx`](../../frontend/apps/turing-app/src/app/bento/llm/bento.llm.instance.form.tsx).
> The AI-agent page is the reference for a **dashboard**-style detail.

This skill is the migration/authoring playbook. The always-locked rules live in
[`components/bento/CONVENTIONS.md`](../../frontend/apps/turing-app/src/components/bento/CONVENTIONS.md)
(which is a pointer since T999 — the authoring contract itself is the design
system's `docs/BENTO-AUTHORING.md`, and what stays in this repository is only
what is about Turing's own routes, privileges and entities)
— read it too.

## What makes a page "look bento"

Three things, in order of how often they're the missing piece:

1. **`BentoHero` header** (not the console `StickyPageHeader`/`SubPageHeader`/`PageHeader`).
   Large airy iOS "navigation title": eyebrow (usually a back-`<Link>` to the list),
   a tonal gradient icon chip in `leading`, the title, a subtitle, and a `trailing`
   slot for actions. Animates in via `.bento-shell-header`.
   > **Back-link eyebrows lead with a left arrow.** When the eyebrow is a link back
   > to the list, prefix it with `<IconArrowLeft size={14} aria-hidden />` (inside the
   > `<Link>`, `inline-flex items-center gap-1`). Without the arrow the link reads as a
   > plain label and users miss that it's the way back. **`BentoEntityShell` does this
   > for you** — it always wraps `eyebrow` in a Link to `listRoute` with the arrow, so
   > pass just the label text. Only add the arrow by hand when you hand-roll a
   > `BentoHero` whose eyebrow is a back-link (workspace/detail pages that don't use the
   > Shell). Don't add it to non-navigational eyebrows (a plain section label).
2. **`BentoFormSection` cards** (not the console `SectionCard`). Each field group is a
   frosted `.bento-tile .bento-glass` card with a tonal icon chip — and it gets the
   **hover-lift animation** for free: `.bento-tile:hover` lifts `translateY(-3px)` with
   a colored shadow, `:active` presses to `scale(0.985)`, and `.bento-grid` children
   stagger their entrance. This is the "cada seção tem animação de hover do card" the
   console `SectionCard` lacks.
3. **No console shell chrome.** The bento shell has **no `SidebarProvider`, no
   breadcrumb bar**. Anything pulling `useSidebar()` (i.e. `SubPage`, `SubPageHeader`,
   `SidebarTrigger`, `PageHeader`) renders blank or crashes. `useSubPageBreadcrumb` is
   harmless (the `BreadcrumbProvider` wraps the whole app) but its output isn't shown.

The frosted look + animation come from CSS classes the design system ships and
`components/bento/index.ts` imports as `@viglet/viglet-design-system/bento.css` (T1007 —
the local `bento.styles.css` this used to name was orphaned by T988 and deleted):
`bento-tile` (hover-lift + spring entrance), `bento-glass` (frosted surface),
`bento-tile-clickable` (cursor + press), `bento-grid` (staggered children). `BentoHero`,
`BentoFormSection`, `BentoEntityTile`, `BentoTile` already apply them — use those
components and you inherit the animation. All animations are disabled under
`prefers-reduced-motion`; a new keyframe on a shared class is added to that guard **in
the design system**, not here.

## The three page shapes

### A. Entity detail with identity (the gold standard)

An entity that has **title / description / icon / enabled** → wrap
[`BentoEntityShell`](../../frontend/apps/turing-app/src/components/bento/bento-entity-shell.tsx).
The Shell owns the hero (inline-edit title/description via `BentoInlineEdit`, icon
picker, Active/Idle status pill), the **scroll-linked hero → sticky `BentoSaveBar`
morph** (driven by `--bento-fade` on `<html>`, a rAF loop — zero React re-renders),
and the delete flow. The page is a thin wrapper; the form is a render-prop child.

```tsx
// page — wire query hooks, drop the form in. NOTHING else.
<BentoEntityShell
  entity={entity} isNew={isNew} headlineFallback={t("llm.newLanguageModel")}
  eyebrow={t("llm.title")} listRoute={ROUTES.BENTO_LLM_INSTANCE}
  icon={IconCpu2} tone="indigo" formId={BENTO_LLM_FORM_ID} feature={t("llm.title")}
  hasStatus
  onUpdate={isNew ? undefined : (next) => updateMutation.mutateAsync(next)}
  onDelete={isNew ? undefined : () => deleteMutation.mutateAsync(entity)}
>
  {({ staged, onStateChange }) => (
    <BentoLLMInstanceForm value={entity} isNew={isNew} staged={staged} onStateChange={onStateChange} />
  )}
</BentoEntityShell>
```

The **form** (see the LLM form): `<form id={FORM_ID}>` groups fields in
`BentoFormSection`s, lifts identity fields (title/description/icon/enabled) OUT into
the hero, reports `{isDirty, isSubmitting}` up via `onStateChange`, merges `staged`
into the create/update payload, and renders its own fixed `BentoSaveBar` inside the
`--bento-fade` wrapper:

```tsx
{showSaveBar && (
  <>
    <div aria-hidden className="bento-save-bar-spacer" />
    <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
      <BentoSaveBar title={stagedTitle} disabled={titleMissing} loading={isSubmitting} onCancel={() => navigate(urlBase)} />
    </div>
  </>
)}
<BentoFormSection icon={IconSettings} tone="blue" title={...} description={...}> …fields… </BentoFormSection>
```

Extra props for BYO-infra: `readOnly` (GLOBAL pool, T372) + `badge`/`notice`;
`hideIcon` for entities with no icon column; `extraActions` for the `⋮` menu.

### B. List → `BentoListPage` + `BentoEntityTile`

One call, a `renderTile`, a dashed "New" tile, an empty-state, per-item emphasis, and
(with `listId`) drag-reorder. Reference:
[`bento.token.instance.list.page.tsx`](../../frontend/apps/turing-app/src/app/bento/token/bento.token.instance.list.page.tsx).

```tsx
<BentoListPage
  items={items} error={error} tryAgainUrl={ROUTES.BENTO_TOKEN_INSTANCE}
  eyebrow={t("home.sections.management.label")} heroIcon={IconCode} tone="slate"
  title={t("apiToken.title")} subtitle={t("apiToken.description")}
  newRoute={`${ROUTES.BENTO_TOKEN_INSTANCE}/new`} newLabel={t("apiToken.newInstance")}
  itemKey={(x) => x.id} emptyTitle={...} emptyDescription={...}
  renderTile={(x, emphasis) => (
    <BentoEntityTile to={`${base}/${x.id}`} emphasis={emphasis} defaultIcon={IconCode} tone="slate" title={x.title} description={x.description} />
  )}
/>
```

**Do NOT reuse the console `GridList`** for a bento list — it renders console cards,
not the frosted mosaic. (Shared SN helper: `app/bento/sn/bento.sn.section-list.tsx`.)

### C. Multi-section / functional surface → `BentoHero` + pill tab-bar + `<Outlet/>`

For a suite of related content pages, a shared-form multi-section editor, or a
read-mostly tool. `BentoHero` on top, a frosted pill nav, then `<Outlet/>`.
References: `app/bento/ai-analytics/bento.ai-analytics.page.tsx`,
`app/bento/admin/bento.admin.page.tsx`, and the persona editor's `chrome="bento"`
branch in
[`persona.instance.form.layout.tsx`](../../frontend/apps/turing-app/src/components/persona/persona.instance.form.layout.tsx).

**If this surface is a `<form>` with Save/Cancel** (like the persona editor), use
[`BentoFormHero`](../../frontend/apps/turing-app/src/components/bento/bento-form-hero.tsx)
**instead of a raw `BentoHero`** — it is the *single drop-in* that reproduces the
`BentoEntityShell` "hero → sticky save bar" morph (Save/Cancel in the hero fade
away on scroll; a fixed frosted bar fades in) for pages that render their own
hero and so can't use the Shell. It wraps `BentoHero` (injecting the fade-out
Save/Cancel into `trailing`, after any extra `trailing` you pass) **and** renders
the `BentoScrollSaveBar` twin, so the two halves can't drift apart. Render it as
the first child **inside the `<form>`** it submits — both Save buttons are plain
`type="submit"`. If the page saves **imperatively** (`onClick`, no form submit),
pass your own buttons via `actions` — it replaces the default pair in *both*
copies and lets the hero live outside a `<form>`. Do **not** hand-wire a
`bento-fade-out BentoSaveBar` + a separate `BentoScrollSaveBar` (the old
two-piece dance; T738 collapsed the last of them, so `BentoScrollSaveBar` has no
consumer outside `BentoFormHero` — keep it that way), and **never** use the
console `StickySaveBar` in bento — it has no scroll morph. Destructive actions go
in `trailing`, never in the save bar.

```tsx
<form onSubmit={handleSubmit(onSubmit)}>
  <BentoFormHero
    eyebrow={<Link to={listRoute}>{feature}</Link>}
    leading={<IconChip/>}
    title={watchedTitle || fallback}
    subtitle={t("…")}
    trailing={!isNew && <BentoActionsMenu actions={[deleteAction]} />}
    onCancel={() => navigate(urlBase)}
    loading={form.formState.isSubmitting}
    dirty={isNew || form.formState.isDirty}
    titleMissing={!watchedTitle?.trim()}
    stickyTitle={watchedTitle || fallback}
  />
  {/* pill nav + <Outlet/> sections … */}
</form>
```

```tsx
<nav className="mb-6 flex flex-wrap gap-2">
  {tabs.map((tab) => (
    <NavLink key={tab.to} to={tab.to} end={tab.end}
      className={({ isActive }) =>
        `bento-tile bento-tile-clickable inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm backdrop-blur transition-colors ${
          isActive ? "border-primary/40 bg-primary text-primary-foreground"
                   : "border-border/60 bg-card/60 text-muted-foreground hover:text-foreground"}`
      }>
      <tab.icon size={16} />{tab.label}
    </NavLink>
  ))}
</nav>
```

## Migration playbook — "this page doesn't look bento"

Symptom → the page reuses a console page/form verbatim, so it shows the console
sidebar/`SectionCard`/`StickyPageHeader` and has no hover cards. (Example that was
wrong: `bento/sn/instance/:id/behavior` reusing `SNSiteBehaviorForm` with
`StickyPageHeader` + `SectionCard`.)

Fix — do the swaps, don't rewrite the field logic:

| Console (wrong in bento) | Bento (right) |
| --- | --- |
| `SubPage` / `SubPageHeader` / `PageHeader` | `BentoHero` (+ pill tab-bar if multi-section) |
| `SectionCard` / `SectionCard.Header` | `BentoFormSection` (`icon`, `tone`, `title`, `description`) |
| `StickyPageHeader` / `StickySaveBar` + its Save/Cancel | `BentoEntityShell` (identity CRUD) · `BentoFormHero` (own-hero form page — single drop-in with the morph) · raw `BentoSaveBar` in the `--bento-fade` wrapper only if you must hand-place it |
| console `GridList` | `BentoListPage` + `BentoEntityTile` |
| `<Card>` list items | `BentoEntityTile` / `BentoTile` |
| hardcoded `ROUTES.*` console nav in a reused form | a `baseRoute` prop (default = console) so save/cancel/back stay in `/bento` |

### Reusing a console form without rewriting it

When the form's field logic is heavy and you don't want to duplicate it, keep the
component and make it bento-capable with **props** (the established pattern):

- **`baseRoute?: string`** (default `ROUTES.<X>_INSTANCE`): replace the form's internal
  `urlBase = ${ROUTES.SN_INSTANCE}/…` with `${baseRoute}/…` so all its
  `navigate(...)` and row links resolve inside `/bento`. Thread it through nested
  list/column components too. Examples: `SNSiteFieldForm`, all the SN section forms
  (T576), the admin forms (`AdminUserForm`, T567).
- **`header?: ReactNode`** (like T566 `SystemInfoPage`): when a reused page hardcodes a
  `SubPageHeader`, add `const headerNode = header !== undefined ? header : <SubPageHeader…/>`
  so bento passes a `<BentoHero/>` (or `null`) and the sidebar-coupled header is
  dropped. Used by `top-terms`, user `profile`/`preferences`.
- **`chrome?: "console" | "bento"`**: for a shared-form multi-section editor (persona),
  branch the wrapper between the console `SubPage` sidebar and the bento hero + pills +
  `<Outlet context={…}>`, keeping the single `useForm` + save bar.

> **⚠️ A `header` prop alone is NOT enough — the body cards must change too.**
> Passing a `BentoHero` via `header` gives the page a bento header but leaves the
> body's `SectionCard`s intact, so you get the airy hero on top of flat console
> cards **with no frosted surface and no hover-lift animation** — the page still
> "doesn't look bento". This is the #1 half-migration mistake (it's what the SE
> `system-info` page shipped with first). The animated card is `BentoFormSection`,
> **not** `SectionCard`. Whenever you add a `header`/`baseRoute` reuse, audit the
> body for `SectionCard` and swap each one.

To restyle the form body itself into frosted hover cards, swap its `SectionCard`s for
`BentoFormSection`s (the fields inside — `FormField`/`Input`/`Select`/`GradientSwitch`
— stay unchanged):

```tsx
<BentoFormSection icon={IconServer} tone="emerald" title={…} description={…}>
  {/* the exact same rows/fields that were in SectionCard.Content */}
</BentoFormSection>
```

If the console still renders the same component, branch on the bento signal instead
of forking. For a **read-only / detail page** reused via `header`, `header !== undefined`
*is* the bento flag — gate the wrapper on it and keep `SectionCard` for the console path:

```tsx
const isBento = header !== undefined;
const details = ( /* status row + info rows — rendered once, reused by both */ );
return (
  <>
    {headerNode}
    {isBento ? (
      <BentoFormSection icon={IconServer} tone="emerald" title={…} description={…}>{details}</BentoFormSection>
    ) : (
      <SectionCard variant="blue">
        <SectionCard.StaticHeader icon={IconServer} title={…} description={…} />
        <SectionCard.Content>{details}</SectionCard.Content>
      </SectionCard>
    )}
  </>
);
```

Extract the card *body* into a shared `const` so both branches render identical
content and only the wrapper differs. For a shared **form** that both consoles use,
prefer an explicit `chrome`/variant prop or fork a thin bento form that reuses the
field sub-components.

## Conventions that bite

- **i18n**: reuse existing console keys for the same entity; object-form defaults
  `t("k", { defaultValue: "…" })` (the test mock only honours the object form); add
  every new key to **both** `en` and `pt`. Bento-chrome-only strings → `bento.*`.
- **Routes** are declared in `app/routes/bento.routes.tsx` with `React.lazy()`; add the
  `bentoRoute` to the item in
  [`bento-nav.config.ts`](../../frontend/apps/turing-app/src/components/bento/bento-nav.config.ts)
  so it appears on the rail + `⌘K`. (All ~29 nav surfaces are migrated as of Block AG
  Phase 4 — the `⌘K` "Opens in console" fallback no longer fires.)
- **Which folder** (T995): shared bento chrome is the design system's, reached through the
  shims in `components/bento/`. This product's *commercial* tiles — the activation card,
  quota banner, Viglet activation dialog, no-LLM state and first-run tour — live in
  [`components/turing-bento/`](../../frontend/apps/turing-app/src/components/turing-bento/).
  A new tile whose subject is what a licence buys goes there, never in `components/bento/`.
- **a11y**: icon-only controls need `aria-label`; ARIA values are string literals; new
  animations go under the reduced-motion guard.
- **TDZ**: place `useCallback`/`useMemo` after the hooks they depend on.

## Verify

- `cd frontend/apps/turing-app && pnpm exec tsc -b --noEmit` (Windows EBUSY → rely on
  IDE diagnostics; see agents.md).
- `pnpm exec vitest run src/components/bento src/components/turing-bento` — both have RTL
  coverage; keep it green. New shared components need a test under
  `components/bento/__tests__/`, product tiles under `components/turing-bento/__tests__/`.
- Then click through the real page in the dev server (`/bento/...`) — confirm the hero,
  the frosted hover cards, save-bar morph on scroll, and that nothing links back to the
  console.

## Design rationale & history

Block AG (T547–T576) took the redesign from a 3-screen preview to 100% of the admin.
Design lives in [`docs/IMPROVEMENTS.md`](../../docs/IMPROVEMENTS.md) §XXXI; cutover
strategy in `docs/adr/0002-bento-ui-migration-cutover.md`; shipped log in
`docs/CHANGELOG.md` under "Block AG". Phase 5 (T569–T572) is the cutover: make `/bento`
the default, redirect the console, then retire it.
