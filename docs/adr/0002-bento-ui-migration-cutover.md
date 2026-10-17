# ADR 0002: Bento UI: complete the interface migration & cutover strategy

- **Status:** Accepted (2026-07-04)
- **Deciders:** Alexandre Oliveira
- **Task:** T547 (Block AG): the gate that fixes scope for T548–T572.
- **Related:** IMPROVEMENTS §XXXI (design rationale), ROADMAP Block AG, the
  settled detail pattern in `bento.llm.instance.page.tsx` (BentoHero +
  inline-edit + scroll-linked BentoSaveBar morph + BentoActionsMenu +
  BentoFormSection).

## Context

A Bento (iOS-flavored frosted-glass mosaic) redesign of the admin console was
started and lives **in parallel** at `/bento`, behind a "Preview" badge, mounted
by `BentoRootPage` (`app/routes/bento.routes.tsx`). Only **3 surfaces** exist so
far:

- `bento/home`: a privilege-gated launcher mosaic with an inline `ChatStarter`.
- `bento/ai-agent`: list + detail dashboard.
- `bento/llm`: list + instance detail + form.

The legacy console (the ~28 route files under `app/routes/*.routes.tsx`, rendered
inside the sidebar-based `ConsoleRootPage`) remains the real interface. The read
paths already use React Query hooks (`api/queries/*.queries.ts`) that the bento
pages consume unchanged: there is **no data-layer work per screen**, only a
re-skin of layout.

The ask ("ajuste 100% de toda a interface usando bento: fluido, conciso,
amigável") raises exactly one architectural fork that blocks all downstream work:
**is Bento the future default (console retired) or a permanent alternative?**
Every later decision (whether to invest in a shared scaffold, whether to build
bento navigation, whether to write redirects) depends on the answer. This ADR
settles it and fixes the migration scope so it is not discovered mid-flight.

## Decision

**Bento becomes *the* interface. The legacy console is migrated screen-by-screen,
then retired.** Preview-forever is rejected: maintaining two parallel admin UIs
indefinitely doubles the surface for every future feature and is the worst
long-run state.

### Cutover mechanism: route swap + redirects, not a runtime feature flag

We migrate **in place under the existing `/bento` tree** and keep the console
live until every surface is migrated (Phase 5). Then we **cut over by routing**,
not by a per-user runtime flag:

1. **During Phases 1–4** (T551–T568): each newly migrated surface is added to
   `bento.routes.tsx`. The console keeps its own routes untouched and reachable.
   Both trees coexist; `/bento` still carries the "Preview" badge. Shipping a
   half-migrated state is safe because nothing is *removed*: a console user
   never lands on a missing bento screen.
2. **Cutover (T569):** make `/bento` (or its post-cutover path) the default
   landing interface, drop the "Preview" badge, and add **console → bento
   redirects** so old bookmarks/deep-links resolve. Redirects are declarative
   `<Navigate replace>` route entries (the pattern already used inside
   `bento.routes.tsx` for `/bento/ai-agent → /bento/ai-agent/instance`), mapping
   each retired console path to its bento equivalent: no runtime flag branch.
3. **Retirement (T570):** delete the migrated `console/*` pages and their dead
   routes/components once the redirects are in place and no orphaned imports
   remain.

**Why route swap over a runtime feature flag:** a per-user/per-tenant flag would
require every screen to exist in *both* trees simultaneously and stay in sync for
the flag's whole lifetime: precisely the double-maintenance we are trying to
end. The `/bento` tree already gives us a safe half-migrated state (additive, no
removals), so the rollout risk a flag would mitigate is already handled by the
parallel-tree structure. A flag adds cost without buying safety we lack.

### The path decision (deferred to T569, constrained here)

Whether the cutover keeps the `/bento` URL prefix or promotes bento to `/` (with
`/bento/*` → `/*` redirects) is a T569 detail. **Constraint set now:** whichever
is chosen, retired console paths MUST resolve via a redirect, and the choice MUST
be a pure routing change (no dual-tree flag). Keeping the `/bento` prefix is the
lower-risk default: it needs only *console→bento* redirects, not a full path
rewrite of the already-migrated tree.

### Scope is fixed here (not discovered mid-flight)

The migration covers **exactly** the surfaces below and nothing else. New admin
features land directly in bento; they are not added to this backlog.

## Surface inventory (the fixed scope)

Migrated (3): **home**, **ai-agent** (list+detail), **llm** (list+detail+form).

Remaining, grouped by phase (one console route file ≈ one surface unless noted):

| Phase | Task | Surfaces (console route file) |
| ----- | ---- | ----------------------------- |
| 1: simple / BYO-infra CRUD | T551 | Search Engine (`se.routes`) |
| | T552 | Embedding Store (`store.routes`) + Embedding Model (`embedding-model.routes`) |
| | T553 | API Token (`token.routes`) + Integration (`integration.routes`) |
| | T554 | MCP Server (`mcp-server.routes`) + Custom Tool (`custom-tool.routes`, keeps Groovy editor in a BentoFormSection) |
| | T555 | Routine (`routine.routes`) + Chat Webhook (`webhook.routes`) |
| | T556 | Persona (`persona.routes`) + Git (`git.routes`) |
| | T562 | Page (`page.routes`) |
| 2: complex CRUD & editors | T557 | Semantic Navigation instance (`sn.routes`): largest CRUD |
| | T558 | SN fields sub-CRUD + manifest/coverage surfaces (nested under `sn.routes`) |
| | T559 | SN onboarding wizard (multi-step, under `sn.routes`) |
| | T560 | Skill folder editor / mini-VS-Code (`skill.routes`) |
| | T561 | Asset browser/manager (`asset.routes`) |
| 3: functional & dashboards | T563 | Chat workspace (`chat.routes`) + Conversation detail (`conversation.routes`) |
| | T564 | AI Analytics suite (`ai-analytics.routes`: cost-governance, chat-analytics, parked-conversations, capability-matrix) |
| | T565 | GraphQL explorer (`graphql.routes`) + Dashboard + Marketplace (`marketplace.routes`) + Voice Kiosk (`voice-kiosk.routes`) |
| | T566 | Global Settings (`global-settings.routes`) + Import/Exchange + Logging + System Info |
| 4: admin & tenancy | T567 | Admin settings (`admin.routes`: users/groups/roles/settings/tokens, preserves the Keycloak-vs-JPA dependent-query pattern) |
| | T568 | Tenant settings + Tenant admin (`tenant.routes`) + User account (`user.routes`) |
| 5: cutover & retirement | T569–T572 | route cutover + console→bento redirects, delete `console/*`, full a11y/responsive/perf/e2e pass, delight polish |

Non-migration route files intentionally out of scope: `bento.routes` (the target
tree itself). This inventory is the single source of truth for "is Block AG
done?": done = every row above migrated, console deleted, redirects live.

## Rationale

1. **One admin UI is the only sustainable end state.** Two parallel consoles
   double the per-feature cost forever. The redesign is only worth doing if it
   *replaces* the console, not if it accretes beside it.
2. **The detail pattern is already settled and cheap to replicate**: the
   BentoHero identity block, the scroll-linked save-bar morph, the actions menu,
   and BentoFormSection wrapping the *existing* react-hook-form fields mean each
   remaining screen is a re-skin, not a rewrite. That makes 100% tractable, which
   is what tips the fork toward "retire the console" over "preview forever".
3. **Additive parallel tree > runtime flag for a long migration.** The `/bento`
   tree already ships a safe half-migrated state without a flag, because nothing
   is removed until Phase 5. A feature flag would force dual-tree maintenance for
   the entire migration: the exact cost we are eliminating.
4. **Fixing scope now prevents the migration from ballooning.** The inventory
   above is closed; new features go straight to bento and are never added to this
   backlog. Block AG has a definition of done.

## Consequences

- **T548** (the lever) extracts `BentoEntityShell` + `BentoListPage` from the
  hand-rolled ~360-line `bento.llm.instance.page.tsx` Shell so each Phase 1–4
  surface is a thin config, not a bespoke page. Everything after it is priced
  against this task.
- **T549** adds persistent bento navigation + a `⌘K` command palette so the
  editor/dashboard surfaces (unreachable from the home mosaic) have an entry
  point before fan-out.
- **T550** locks conventions (i18n key reuse, a11y baseline, responsive
  breakpoints, vitest coverage of the shared components) so 20 screens don't
  drift.
- **Phases 1–4 (T551–T568)** are additive: each adds bento routes; the console
  stays live and reachable. No redirects, no deletions yet.
- **T569** performs the route cutover + console→bento redirects and drops the
  Preview badge; **T570** deletes the migrated console pages; **T571** runs the
  full a11y/responsive/perf/e2e pass; **T572** (💭) adds delight polish.
- The "Bento is the interface" decision is mirrored into IMPROVEMENTS §XXXI and
  agent memory so it guides day-to-day work without a doc dive.
