# Turing: Enterprise Search Intelligence Platform

## Development Guidelines

### Project Structure

- **turing-app**: Spring Boot 4 backend (Java 21), main Maven module
- **frontend/**: pnpm + Turborepo monorepo with the React apps and SDK packages
  - **frontend/apps/turing-app**: React 19 + TypeScript frontend (Vite, Tailwind v4, shadcn/ui)
  - **frontend/apps/marketplace**: marketplace example apps (mythical-creatures, space-missions, vinyl-records)
  - **frontend/apps/showcase**: `turing-showcase` ("Atlas Store") — the Block Y reference app that stresses the maximum platform surface (typed catalog, faceted+hybrid search, RAG chat, agents, skills, creative bets). Self-seeds a 200-product catalog; `pnpm dev:showcase` / `build:showcase`; e2e `TurShowcaseSmokeIT`; coverage audit in its `COVERAGE.md`
  - **frontend/packages/react-sdk**: `@viglet/turing-react-sdk` — hooks and UI components for the Turing API
  - **frontend/packages/sdk**: `@viglet/turing-sdk` — **vanilla JS, zero-dependency** SDK (framework-agnostic client + observable controllers for search/chat/autocomplete/slots). Built for Adobe Edge Delivery Services (EDS) blocks, plain `<script>`, or any bundler. Ported from the React SDK's `core/` (axios → native `fetch` via an explicit `createTuringClient`). Ships a self-contained ESM vendor file (`dist/turing-sdk.js`) + UMD + types via Vite lib mode. EDS example blocks live in `frontend/packages/sdk/examples/eds/`. Build: `pnpm --filter @viglet/turing-sdk build` (root alias `build:sdk`). The React SDK keeps its own `core/` copy for now; a future change may have it depend on this package.
  - **frontend/packages/shared-types**: `@viglet/turing-shared-types`
- **turing-commons**, **turing-spring**, **turing-java-sdk**: shared libraries
- Root Maven POM is multi-module
- The legacy `turing-react/` directory is a stub (only contains `node/`); it has been migrated to the monorepo above

### Key Paths

- Backend source: `turing-app/src/main/java/com/viglet/turing/`
- Backend tests: `turing-app/src/test/java/com/viglet/turing/`
- Frontend source: `frontend/apps/turing-app/src/`
- Frontend route definitions: `frontend/apps/turing-app/src/app/routes/` (all use `React.lazy()` since 2026.2.6)
- Frontend query hooks: `frontend/apps/turing-app/src/api/queries/` (React Query v5)
- Liquibase changelogs: `turing-app/src/main/resources/db/changelog/`

### Roadmap & docs maintenance

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

### Build & Test Commands

```bash
# Backend tests only (skip frontend npm build)
mvn test -pl turing-app -Dskip.npm=true

# Run specific test class
mvn test -pl turing-app -Dskip.npm=true -Dtest="ClassName"

# Backend integration tests (requires Docker — uses Testcontainers)
mvn verify -pl turing-app -Dskip.npm=true

# Frontend compile (from frontend/apps/turing-app)
pnpm run build           # tsc -b && vite build
pnpm exec tsc -b --noEmit  # type-check only, no emit
pnpm dev                 # local dev server

# Frontend tests (vitest + React Testing Library) — from repo root or frontend
pnpm --filter turing-app test        # vitest run (CI mode)
pnpm --filter turing-app test:watch  # watch mode

# Full Maven build
mvn clean install

# Design system iteration: copy-ds.cmd at repo root (rebuilds + copies viglet-design-system dist)
```

> **Windows PowerShell**: quote `-D` flags, e.g. `"-Dskip.npm=true"`

### Backend Conventions

- **Spring Boot 4** with **Spring AI 2.0.0-M2** for LLM integration
- Persistence: JPA/Hibernate with H2 (dev) and Liquibase migrations
- REST controllers in `api/` package, services in dedicated packages
- Use constructor injection (no `@Autowired` on fields)
- Records for DTOs/API request-response types
- Avoid injecting `ObjectMapper` in controllers — use `@RequestBody` / `@RequestPart` for auto-deserialization
- `TurSecretCryptoService` handles API key encryption/decryption

### File Header Convention

- **Every new Java file** must include a `@since` tag in the class-level Javadoc with the current project version (check the `<version>` in the root `pom.xml`)
- Current version: **2026.3.4**
- Example:
  ```java
  /**
   * Description of the class.
   *
   * @author Alexandre Oliveira
   * @since 2026.3.4
   */
  ```
- When the project version changes, update the "Current version" line above so new files always reference the correct version

### JPA & Database Column Naming

- **No implicit naming strategy** is configured — Hibernate maps Java field names **literally** to column names (no snake_case conversion)
- A Java field `sitePrompt` maps to column `sitePrompt`, **not** `site_prompt`
- When adding `@Column` annotations, use the exact camelCase field name (or omit `name` to let Hibernate use the default)
- **Never use snake_case** in Liquibase column names unless the existing table already uses it — always match the convention of the target table
- Existing tables like `sn_site_genai` use camelCase columns: `systemPrompt`, `sitePrompt`
- Existing tables like `sn_site` use lowercase: `description`, `name`, `facet`
- When in doubt, check the existing columns in the entity class or run a query against the H2 dev database

### Liquibase Conventions

- The user may run the app between turns; editing a changeset that may already
  have run breaks Liquibase checksums. Fix follow-ups with a new changelog file
  instead of changing the prior one.

- **Always create a new file** — never modify existing changelog files
- Naming: `v{currentVersion}.{subversion}.yaml` with incremental subversion (e.g., if current version is `2026.1.14` and `v2026.1.14.0.yaml` exists, the next file is `v2026.1.14.1.yaml`)
- Include in `db.changelog-master.yaml`
- Use `preconditions` with `onFail: MARK_RAN` for idempotent changesets
- Column size changes: use `modifyDataType`; new data: use `insert`
- **Column names must match the JPA field name exactly** (camelCase) — see "JPA & Database Column Naming" above
- **Large text columns**: use `longtext` instead of `clob` — `longtext` is compatible with H2, MySQL, and PostgreSQL, while `clob` may not map correctly on MySQL

- Use lowercase portable Liquibase types (`boolean`, `longtext`), not uppercase
  `BOOLEAN`/`CLOB`; `TurLiquibaseChangelogAgnosticTest` checks this.

### Visual Identity & Brand Colors

- **Product name**: Viglet Turing ES (Enterprise Search)
- **Logo**: `frontend/apps/turing-app/public/favicon-96x96.png` (also available at 32x32, 16x16)
- **Primary gradient**: `blue-600 (#2563eb)` → `indigo-600 (#4f46e5)` — used in buttons, avatars, switches, headers
- **Dark mode primary**: `blue-500 (#3b82f6)` → `indigo-500 (#6366f1)`
- **Semantic colors**:
  - Success: `emerald-600 (#059669)` → `teal-600 (#0d9488)`
  - Destructive: `red-600 (#dc2626)` → `rose-600 (#e11d48)`
  - Secondary: `slate-600 (#475569)` → `slate-700 (#334155)`
- **Gradient components**: `GradientButton`, `GradientAvatar`, `GradientSwitch` — all use the blue→indigo gradient
- **Email templates**: use inline styles with the same blue→indigo palette; dark background `#0a0a0f`, card background `#16161f`, border `#1e1e2e`
- **Icons**: `@tabler/icons-react` (primary), `lucide-react` (some components)
- Always reference the product as **Viglet Turing ES** in user-facing text (emails, reports, footers)

### Frontend Conventions

- **React 19** + **TypeScript** + **Vite**
- **Tailwind CSS v4** with `@tailwindcss/typography` plugin
- **shadcn/ui** components in `src/components/ui/`
- Custom gradient components: `GradientButton`, `GradientAvatar`, `GradientSwitch`
- Icons: `@tabler/icons-react` (primary), `lucide-react` (some components)
- Routing: `react-router-dom` with routes defined in `src/app/routes/`. **All route files use `React.lazy()`** — when adding a new route, follow the pattern (`const FooPage = lazy(() => import("..."))`) so the new bundle is code-split automatically
- Services: class-based in `src/services/`, use `axios` for REST, native `fetch` for SSE streaming
- Models: TypeScript interfaces in `src/models/`
- CSRF: For non-axios requests, read `XSRF-TOKEN` cookie or fetch from `/csrf` endpoint
- Place `useCallback`/`useMemo` after the hooks and values they depend on.
  Destructured hook values are bound at their line, not hoisted; using them
  earlier can create a runtime TDZ that TypeScript may not catch.
- Do not assign to an outer variable inside a `setState` updater and read it on
  the next line. React queues the updater for commit; use a `useRef` mirror when
  synchronous access is needed.
- Avoid `import * as Icons` plus dynamic property access in Vite/Rolldown. That
  pattern disables tree-shaking and keeps every export. Use static imports or
  Iconify for runtime icon lookup.

#### Data fetching with React Query (since 2026.2.6)

Read paths use **`@tanstack/react-query` v5**. Don't reach for `useState` + `useEffect` + `axios.then()` — that pattern is being phased out.

- **Singleton client** in `src/lib/query-client.ts` — defaults: 30s staleTime, 5min gcTime, 1 retry, no refetch-on-window-focus.
- **Query hooks** live in `src/api/queries/{feature}.queries.ts` and wrap a service:
  ```ts
  // src/api/queries/foo.queries.ts
  const service = new TurFooService();
  export function useFoos() {
    return useQuery({
      queryKey: queryKeys.foos.list(),
      queryFn: () => service.query(),
    });
  }
  // For entity-scoped lists:
  export function useFoos(parentId: string | undefined) {
    return useQuery({
      queryKey: parentId
        ? queryKeys.foos.listByParent(parentId)
        : ["foos", "pending"],
      queryFn: () => service.query(parentId as string),
      enabled: Boolean(parentId),
    });
  }
  ```
- **Query keys** are centralised in `src/api/queries/keys.ts` so mutations can invalidate them deterministically. Always extend `queryKeys` rather than inlining string arrays.
- **Dependent query** (e.g. Keycloak vs JPA backend in admin pages): pass the discriminator as a parameter, set `enabled: discriminator !== null`, and split the query key by source so toggling invalidates cleanly. See `src/api/queries/admin.queries.ts` for the canonical example.
- **Mutations** (when adding new write paths): use `useMutation` and call `queryClient.invalidateQueries({ queryKey: queryKeys.foos.all() })` in the `onSuccess` handler so cached lists refresh automatically.
- **Page pattern** — replace the legacy `useState`/`useEffect` block with one line:
  ```ts
  const { data: foos, isError } = useFoos();
  const error = isError
    ? t("common.connectionError", { resource: "foos" })
    : null;
  ```

### Testing Conventions

- Backend: JUnit 5 + Mockito with `@ExtendWith(MockitoExtension.class)`
- Mockito strict stubbing: stub all methods that will be called (use `any()` for broad matching)
- Integration tests use `@SpringBootTest` with full context — keep controllers lean to avoid bean issues
- Always run `mvn test -pl turing-app -Dskip.npm=true` after backend changes

#### Integration test (`*IT.java`) `@SpringBootTest` requirements

Every new IT that boots the full Spring context **must** either:

1. **Extend `AbstractTuringSpringIT`** ([turing-app/src/test/java/com/viglet/turing/testutil/AbstractTuringSpringIT.java](turing-app/src/test/java/com/viglet/turing/testutil/AbstractTuringSpringIT.java)) — gives you an ephemeral H2 datasource, snapshot seeding, JMX enabled, **per-JVM Artemis data directory**, and proper cleanup. **Preferred**. The subclass **must NOT redeclare `@SpringBootTest`** — that would override (not merge with) the parent's annotation and silently drop `spring.jmx.enabled=true`:

   ```java
   // ✅ correct — inherits @SpringBootTest from AbstractTuringSpringIT
   class TurFooIT extends AbstractTuringSpringIT {
       @Test
       void something() { ... }
   }

   // ❌ wrong — own @SpringBootTest overrides parent's annotation,
   //    JMX property is lost, context fails on TurQueueBrowserService
   @SpringBootTest
   class TurFooIT extends AbstractTuringSpringIT { ... }
   ```

2. **Or, when subclassing isn't possible** (rare — e.g. needs a different base or test slice), explicitly enable JMX on the annotation:

   ```java
   @SpringBootTest(properties = "spring.jmx.enabled=true")
   class TurFooIT { ... }
   ```

   When going this route, also override `spring.artemis.embedded.data-directory` via `@DynamicPropertySource` to a unique per-JVM path (see `AbstractTuringSpringIT.overrideArtemisDataDirectoryWithEphemeralPath`), otherwise concurrent ITs and a locally-running app will collide on the same Artemis file lock and hang for several minutes on context startup.

**Why JMX must be passed via `properties = ...`**: `TurQueueBrowserService` injects `javax.management.MBeanServer` in its constructor (mandatory dep, not optional). In Spring Boot 4, the `JmxAutoConfiguration` that supplies `MBeanServer` is **not** activated in `@SpringBootTest` from `application.yaml` alone — the property has to be passed via `properties = ...` on the annotation. Without it the context fails with `No qualifying bean of type 'javax.management.MBeanServer' available` and Failsafe burns ~30s per attempt before giving up.

**Why per-JVM Artemis data dir matters**: the production default is `spring.artemis.embedded.data-directory=store/queue` — a CWD-relative path. With embedded Artemis + `persistent: true`, two JVMs sharing that path (e.g. a running app + a forked Surefire IT, or two parallel ITs) compete for `server.lock` and serialize through `FileLockNodeManager`'s sleep-retry loop. Symptom: the IT hangs on Spring context startup with no error logged, until you `Stop-Process` the other JVM. `AbstractTuringSpringIT` redirects this to a unique per-JVM temp dir, eliminating the class of bug entirely.

`AbstractTuringSpringIT` also sets
`spring.artemis.embedded.persistent=false`. Keep it that way: multiple cached
Spring contexts in the same Maven fork can otherwise share the per-JVM Artemis
directory and block for minutes on `FileLockNodeManager`.

`@TestConfiguration` classes with `@Primary` mocks must live outside the main
component scan (`com.viglet.turing.*`). Put reusable test config under
`com.viglet.testsupport.*`; otherwise a mock can leak into every
`@SpringBootTest`.

#### JPA fetch strategy: LAZY by default

**Always use `FetchType.LAZY` for associations.** This is a project-wide rule, not a per-case decision.

- `@OneToOne`: JPA default is EAGER → **must** override with `@OneToOne(fetch = FetchType.LAZY)`.
- `@ManyToOne`: JPA default is EAGER → **must** override with `@ManyToOne(fetch = FetchType.LAZY)`.
- `@OneToMany`, `@ManyToMany`: default is LAZY — leave it.
- **Never use `FetchType.EAGER`** unless you have a measured, documented reason. Add a `// reason:` comment when you do.

**Why this is non-negotiable**: EAGER associations cascade across the object graph and explode load cost. Real incident — search latency went from **200ms → 5s** on sites with an AI Agent configured because `TurSNSite → TurSNSiteGenAi → TurAIAgent → 4 @ManyToMany` all defaulted to EAGER, even though search never touches GenAi. Reverting that one chain to LAZY brought search back to ~200ms.

**Safety net for legacy code paths**: [turing-app/src/main/resources/application.yaml](turing-app/src/main/resources/application.yaml) has `hibernate.enable_lazy_load_no_trans=true`, so LAZY access outside a `@Transactional` boundary still works (Hibernate opens a temporary session). This is a fallback for callers we may have missed during the EAGER → LAZY migration — **don't rely on it for new code**. New write paths and chat/RAG flows should access lazy associations inside an `@Transactional` or service-managed session.

**When loading a graph in one shot is genuinely needed**, use:

- `@EntityGraph(attributePaths = {...})` on the repository method, or
- `JOIN FETCH` in a `@Query`

— not `FetchType.EAGER` on the entity.

#### Other testing rules

- Always check `application.yaml` defaults before writing test-specific properties — most ITs should inherit project conventions.

#### Frontend Testing (vitest + React Testing Library)

The frontend gained its first test runner in 2026.3 (`turing-app` only).
Stack: **vitest 3.2.4 + @testing-library/react 16 + jsdom + fake-indexeddb**.

- **Run**: `pnpm --filter turing-app test` (CI) / `test:watch`. vitest 3.2.4
  runs fine against Vite 8 despite the peer-range warning; bump to vitest 4
  only if a Vite upgrade forces it.
- **Config**: standalone [`vitest.config.ts`](frontend/apps/turing-app/vitest.config.ts) —
  deliberately does **NOT** extend `vite.config.ts` (its Module Federation /
  Tailwind / viglet boot-loader plugins break under jsdom). Only the React
  plugin + `@` alias + `environment: "jsdom"`, `globals: true`, `css: false`,
  `clearMocks`/`restoreMocks: true`.
- **Test location**: `src/**/__tests__/*.test.tsx`. These ARE included by
  `tsconfig.app.json`, so `tsc -b` (the build) type-checks them — keep tests
  type-clean (`noUnusedLocals`/`Params`, `verbatimModuleSyntax` → `import type`).
  `src/test/vitest-globals.d.ts` provides `/// <reference types="vitest/globals" />`.
- **Global mocks** live in [`src/test/setup.ts`](frontend/apps/turing-app/src/test/setup.ts)
  (applied to every test): `react-i18next` (passthrough `t` returning the key
  or its `defaultValue`), `react-markdown` → raw children (skips the heavy
  rehype/remark ESM tree), `@/contexts/user.context` `useCurrentUser` stub, and
  the shared **`axios`** singleton (both admin services and the SDK's `get`
  helper import the default axios — one mock covers all reads). Plus jsdom
  polyfills: `scrollIntoView`, `ResizeObserver`, `matchMedia`.
- **Network boundary**: `postAgentChat`/`postLlmChat` use `fetch` (not axios) —
  stub `global.fetch` with an SSE `ReadableStream` of `data: {type:"token"|"options",content}`
  lines (see `chat/__tests__/test-utils.tsx` `makeSseFetch`). Slot polling and
  all other reads go through the mocked `axios.get`.
- **Full-page renders** (e.g. `ChatPage`) must be wrapped in a
  `QueryClientProvider` (the header's session-info sheet uses React Query) and a
  `MemoryRouter` — `renderWithRouter` in `test-utils.tsx` does both. Component-
  level renders (e.g. `AgentChatTab`) usually need neither.
- **IndexedDB**: `import "fake-indexeddb/auto"` at the top of the test file
  before anything touches the session store.

### Common Pitfalls

- `@Column(length=N)` must accommodate all enum/ID values (e.g., "AZURE_OPENAI" = 12 chars)
- **MySQL primary key limit**: Max key length is 3072 bytes. With `utf8mb4` (4 bytes/char), `VARCHAR` PKs must be ≤ 768 chars. Example: `VARCHAR(1024)` PK fails on MySQL — use `VARCHAR(768)` instead
- Spring AI 1.1.2: `AzureOpenAiEmbeddingModel` has no builder — use constructor
- Anthropic and Gemini providers don't support embedding — throw `UnsupportedOperationException`
- Frontend npm builds may fail with EBUSY on Windows if IDE locks files — use `-Dskip.npm=true` for backend-only testing

### Search Engine & Lucene Notes

- `TurSearchEnginePlugin` is the search-engine plugin lifecycle interface;
  resolve plugins through `TurSearchEnginePluginFactory` (`getPluginForSite`,
  `getPluginForInstance`, `getPlugin(String type)`). Plugin type strings come
  from `getPluginType()` and are lowercased.
- The Lucene search-engine plugin lives under
  `turing-app/src/main/java/com/viglet/turing/lucene/` and uses Lucene 10.
  Lucene 10 removed `FacetsCollector.search()`; do not use it.
- `TopScoreDocCollector.create(int, int)` does not exist in Lucene 10. Use
  separate `searcher.search()` calls where needed.
- `TurSNSiteFieldExt.getFacet()`, `getEnabled()`, and `getMultiValued()` are
  primitive `int` flags; compare with `== 1`, not null checks. Only
  `getFacetPosition()` is nullable.
- The embedded Lucene vector store is separate from the search-engine Lucene
  plugin. It is exposed through `TurLuceneStoreProvider` with plugin type
  `lucene`.

### Pluggable Storage Architecture

The storage layer is abstracted via `TurStorageService` interface with three implementations:

**Configuration** (`application.yaml`):

```yaml
turing:
  storage:
    type: none # none | minio | filesystem
    filesystem:
      path: ./store/assets
    minio:
      endpoint: http://localhost:9000
      access-key: admin
      secret-key: minha_senha_forte
      bucket: turing-assets
```

**Key classes** (all in `service/storage/`):

- `TurStorageType` — enum: `NONE`, `MINIO`, `FILESYSTEM`
- `TurStorageService` — interface: `isEnabled()`, `getType()`, `listObjects()`, `downloadObject()`, `statObject()`, `uploadObject()`, `uploadStream()`, `deleteObject()`, `deleteObjectsWithPrefix()`, `createFolder()`, `guessContentType()`
- `TurStorageObjectStat` — neutral DTO replacing MinIO's `StatObjectResponse`
- `TurStorageContentTypes` — shared static MIME type utility
- `TurMinioStorageService` — MinIO implementation
- `TurFilesystemStorageService` — filesystem implementation with path traversal protection (`resolveSafe()`)
- `TurNoOpStorageService` — disabled/no-op implementation (lists return empty, mutations throw)
- `TurStorageConfig` — `@Configuration` with `@Bean` factory that selects implementation based on `turing.storage.type`

**Properties** (in `properties/`):

- `TurStorageProperty` — `type` (enum), nested `minio` (TurMinioProperty), nested `filesystem` (TurFilesystemProperty with `path`)
- `TurMinioProperty` — `endpoint`, `accessKey`, `secretKey`, `bucket` (no `enabled` field — type selection controls this)
- Access pattern: `configProperties.getStorage().getMinio()` or `configProperties.getStorage().getFilesystem().getPath()`
- Old `turing.minio.enabled` and `turing.minio.*` are **removed** — all storage config lives under `turing.storage.*`

**Frontend feature flag**: `FeaturesResponse.storageEnabled` (renamed from `minioEnabled`)

- Sidebar hides "assets" and "pages" when `storageEnabled=false`
- System Info shows storage section with dynamic type label and property key

### LLM Summary Service (Cached AI Insights)

Reusable service for generating cached LLM summaries with regenerate support:

**Backend** (`system/TurLlmSummaryService`):

- `generate(cacheKey, data, systemPrompt, regenerate)` → `SummaryResult(success, error, content, canRegenerate)`
- Uses `TurLlmCacheService` for in-memory caching (respects Global Settings TTL)
- Uses default LLM from `TurGlobalSettingsService.getDefaultLlmId()`
- `isAvailable()` — checks if default LLM is configured and enabled

**Used by**:

- `TurSNSiteSummaryAPI` — `GET /api/sn/{id}/summary?regenerate=false` for SN site insights
- `TurSystemInfoAPI` — `GET /api/system/info/insights?regenerate=false` for system info AI insights
- Both follow same pattern: collect data → call `llmSummaryService.generate()` → return `SummaryResult`

**Frontend pattern** (same for both SN insights and System Info insights):

```typescript
const response = await axios.get<InsightsResponse>(url, {
  params: regenerate ? { regenerate: true } : {},
});
// InsightsResponse: { success, error, content, canRegenerate }
```

- Show regenerate button when `canRegenerate=true` (controlled by `llmCacheRegenerate` in Global Settings)
- System Info uses a resizable side panel; SN insights uses a full page

### ResizablePanel Customization (shadcn)

The `ResizablePanel` component (`components/ui/resizable.tsx`) has a custom `overflowHidden` prop:

- `react-resizable-panels` injects an internal wrapper `div` with `style="overflow: auto"` that cannot be controlled via props
- `overflowHidden` prop applies `overflow: hidden` on the Panel element and `[&>div]:overflow-hidden!` (with `!important`) to override the inline style on the internal wrapper
- Use `<ResizablePanel overflowHidden>` when the panel content manages its own scroll (e.g., a Card with `overflow-auto` on its body)
- In `react-resizable-panels` v4, numeric sizes are pixels. Use strings for
  percentages (`defaultSize="60"`), otherwise panels can render tiny/squeezed.

### Cache, Scheduling & Serialization Notes

- This project uses Jackson 3 in `turing-app`: imports are `tools.jackson.*`,
  not `com.fasterxml.jackson.*`.
- For Keycloak admin REST URL derivation, preserve the `issuer-uri` base path
  (for example `/kc`); do not rebuild URLs from only scheme/host/realm.
- Spring Boot 4 clustered cache requires `com.hazelcast:hazelcast-spring` for a
  Hazelcast-backed `CacheManager`; without it Boot may silently fall back to
  `ConcurrentMapCacheManager`.
- Cluster-wide-once `@Scheduled` jobs should use ShedLock via
  `@SchedulerLock`; `TurSchedulerLockConfig` provides the Hazelcast-backed
  `LockProvider`.
#### Caching policy: never on repositories (Block AC / T486)

**Repositories are uncached.** `@Cacheable`/`@CacheEvict`/`@Caching` must not
appear on any repository — `TurCacheAnnotationConventionsTest` (under
`turing-app/src/test/java/com/viglet/turing/db/`) fails the build if they do.
The reason: a repository finder caches a **JPA entity**, and entities carry
lazy `@ManyToOne` proxies. A cached entity outlives its session, so any hot-path
read that dereferences a lazy association outside a transaction throws
`LazyInitializationException` (the bug that surfaced when
`hibernate.enable_lazy_load_no_trans` was turned off). T486 removed the whole
class — including the per-finder cache-name constants. The now-redundant
`findByIdNoCache` band-aid is retired separately in T488.

When you do need to cache for the search/chat hot path:

1. **Cache an immutable read-model / DTO in a service-level cache, never a JPA
   entity.** The parsed `ChatFlowGraph`, an SN site + field-config DTO, router
   decisions, static prompts, rerank scores, prices — values with no lazy
   proxies and no session affinity, safe to read anywhere. Admin/CRUD stays
   uncached and uses `@Transactional` + `@Query ... LEFT JOIN FETCH` to load the
   graph it needs (see T488).
2. **`@Cacheable` is proxy-based — never call it via `this`.** A
   `this.cacheableMethod()` from the same class bypasses the Spring proxy and
   silently no-ops the cache (and its eviction). For intra-class calls, inject
   the bean into itself with `@Lazy` and route through that reference. See
   [`TurChatFlowStaticPromptCache`](turing-app/src/main/java/com/viglet/turing/genai/flow/TurChatFlowStaticPromptCache.java)
   for the canonical pattern.
3. **Pair every service cache name with an eviction.** Evict on the matching
   admin write (e.g. the reranker-config setters evict `turRagRerankScore`).
4. **Cache names follow lowerCamelCase** — `turXxx<Purpose>` for service-level
   caches (e.g. `turChatFlowRouterDecision`, `turPersonaStaticPrompt`).
5. **Hazelcast vs in-process is a deploy-time concern.** The cache topology is
   decided by whether `com.hazelcast:hazelcast-spring` is on the classpath; the
   `@Cacheable` site stays identical either way.

### Agent Operational Memory (Repository)

- In `react-hook-form`, `disabled` fields are omitted from submission payloads; use `readOnly` when a value must be non-editable but still sent.
- `TurAuthenticationEntryPoint` tests should validate `commence(...)` output directly: HTTP 401, `application/json`, and `{"error":"Unauthorized"}`.
- For custom `/api/login` session auth alongside `httpBasic`, explicitly use `HttpSessionSecurityContextRepository` with `SessionCreationPolicy.IF_REQUIRED` to avoid browser requests returning 401 with valid `JSESSIONID`.
- In SN field-check flows, `TurSolrFieldBean.getType()` may be `null`; prefer `Objects.equals(actual, expected)` over calling `.equals()` on nullable values.
- For write-only secret fields modeled as JPA `@Transient` (`apiKey`, `credential`), MockMvc/Jackson binding may skip values; extract secrets from raw request payload map in controller tests and runtime paths.
- Liquibase updates on reused local H2 files can fail with `Duplicate column name`; make changeSets idempotent with `preConditions` (`columnExists` + `onFail: MARK_RAN`).
- In PowerShell, quote Maven test filters like `"-Dtest=A,B"` to avoid comma parsing issues.
- In JUnit `@CsvSource`, quote regex values containing commas (for example `'1[.,]5B'`) and use `nullValues = "null"` when a true `null` is required.
- In this Windows workspace, `py` may be unavailable; prefer `python` (for example `python -m py_compile ...`).
- If `rg` is unavailable in PowerShell, use `Get-ChildItem` + `Select-String` for scripted searches.
- Always route `ToolCallback[]` through `TurToolCallbackPipeline.decorate(...)`
  before passing callbacks to a `ChatModel`. `@Tool` annotations may carry
  placeholder descriptions; real tool descriptions live under
  `prompts/tools/**/*.md`.
- Avoid structured-output guardrails with `gpt-4o-mini`; it has historically
  truncated JSON, dropped commas, or mixed fences even with schema guidance.
  Prefer provider tool calling with schema, or the bilingual `LlmJudge` path.
- When executing the multi-phase test plan in `docs/IMPROVEMENTS.md`, do not
  mark a task done for skeleton-only tests that still contain `fail()` or only
  `@Disabled`. Implement real assertions first.

### Frontend Authoring & Design System Notes

- AI authoring side-by-side mode uses one global `ResizablePanelGroup` in
  `ConsoleRootPage`; pages portal authoring content into the slot through
  `useGlobalSidePanel`.
- Viglet Design System source lives at
  `d:/git/viglet/viglet-design-system`; inspect `src/components/ui/` there
  when debugging design-system component behavior.

### Frontend TypeScript Check (Windows EBUSY workaround)

When `node_modules` native binaries are locked by the IDE/Vite dev server:

1. **Don't loop** retrying `npm ci`, `npm install`, `npx tsc`, or other install variations — they will all fail with EBUSY/EPERM while the IDE holds locks.
2. **Rely on IDE diagnostics** from VSCode hook feedback (`ide_diagnostics` in tool responses) — these provide real-time TypeScript error checking without needing `tsc`.
3. **Backend compile is the primary gate** — run `mvn compile -pl turing-app -Dskip.npm=true` to verify Java code. For the frontend, prefer `pnpm exec tsc -b --noEmit` from `frontend/apps/turing-app` when locks clear.

---

## Reusable Playbook: Decimal and Currency Demands

Use this checklist whenever a demand involves numeric localization (dot/comma), currency fields, Solr schema updates, and React admin forms.

### Quick Checklist (10 lines)

1. Confirm `TurSEFieldType`/API/frontend type options are aligned.
2. Keep enum persistence as `@Enumerated(EnumType.STRING)`.
3. Map `CURRENCY` to Solr `currency` (`CurrencyFieldType`), never `pdouble`.
4. Ensure `currency.xml` exists in all configsets and packaged zip configsets.
5. Index currency as `amount,ISO4217` (example: `150.00,BRL`).
6. Use one global decimal source (`DOT`/`COMMA`) and normalize centrally.
7. Normalize decimal-capable React inputs on blur (`FLOAT`/`DOUBLE`/`CURRENCY`).
8. Preserve date-like values in mixed fields (normalize only numeric-looking strings).
9. Validate backend first (`mvn test -Dskip.npm`; in PowerShell use `"-Dskip.npm"`), then frontend (`npm run compile`).
10. For Windows npm issues, use resilient deps fallback (`npm ci` -> `npm install` -> `--legacy-peer-deps` when needed).

### 1) Backend and Persistence Rules

- Keep field enums aligned end-to-end (`TurSEFieldType`, API type lists, frontend type selectors).
- Persist enum fields as strings (`@Enumerated(EnumType.STRING)`) to avoid ordinal drift across versions.
- If introducing global numeric behavior, centralize in a dedicated service (e.g., global settings + normalizer component) and avoid scattered parsing logic.

### 2) Solr Currency Rules (Mandatory)

- Map `CURRENCY` to Solr `CurrencyFieldType` (`currency`), not `pdouble`.
- Ensure schema field type exists with these attributes:
  - `class="solr.CurrencyFieldType"`
  - `amountLongSuffix="_l_ns"`
  - `codeStrSuffix="_s_ns"`
  - `defaultCurrency="USD"`
  - `currencyConfig="currency.xml"`
- Ensure `currency.xml` exists in all configsets (`en`, `pt`, `es`, `ca`) and in packaged zip configsets used at runtime.
- Currency payload format for indexing must be `amount,ISO4217` (example: `150.00,BRL`).

### 3) Decimal Separator Behavior (Global)

- Use one global config source (`DOT` or `COMMA`) and normalize values before indexing/query persistence.
- Frontend forms should normalize on blur for decimal-capable fields (`FLOAT`, `DOUBLE`, `CURRENCY`).
- Keep date-like values untouched in mixed inputs (e.g., custom facet ranges) and only normalize when value looks numeric.

### 4) React/Admin Integration Pattern

- Add dedicated route + page for global settings.
- Expose typed model and service for `/api/system/global-settings`.
- Use a reusable hook for decimal symbol retrieval and normalization helpers.
- Update placeholders/descriptions dynamically to reflect active separator and expected currency format.

### 5) Validation Workflow

- Backend-first validation: run focused tests with `mvn test -Dskip.npm`.
- Frontend validation: prefer `npm run compile`.
- If Windows `EPERM`/lock affects `npm ci`, use resilient scripts with fallback.

### 6) Resilient npm Script Pattern

- Prefer split scripts:
  - `compile:deps` (or `build:deps`) for dependency step.
  - `compile:build` (or `build:run`) for actual build.
  - top-level `compile`/`build` chaining both.
- Recommended deps fallback pattern:
  - `npm install --no-audit --no-fund`
- For workspaces with peer-resolution instability, extend fallback with:
  - `npm install --no-audit --no-fund --legacy-peer-deps`

### 7) Delivery Discipline

- Prefer targeted fixes over broad refactors.
- Regenerate only artifacts that must reflect schema/runtime changes.
- If validation reveals unrelated pre-existing issues, report them separately and keep scope explicit.

## Spring AI Integration (v2.0.0-M2)

### LLM Provider Architecture

Turing uses a pluggable provider pattern for LLM integration via Spring AI:

- **Interface**: `TurGenAiLlmProvider` — defines `getPluginType()`, `createChatModel()`, `createEmbeddingModel()`
- **Factory**: `TurGenAiLlmProviderFactory` — resolves the correct provider based on the vendor's plugin type
- **Supported providers**: OpenAI, Ollama, Anthropic (Claude), Google Gemini (native GenAI SDK), Google Gemini (OpenAI-compatible), Azure OpenAI (Copilot)

### Streaming Chat API

The chat endpoint supports **Server-Sent Events (SSE)** for token-by-token streaming:

```java
// Backend: Flux-based SSE streaming
@PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ChatResponse> chat(...) {
    return chatModel.stream(prompt)
        .map(response -> new ChatResponse("assistant", response.getResult().getOutput().getText()))
        .filter(r -> !r.content().isEmpty());
}
```

```typescript
// Frontend: fetch + ReadableStream for SSE consumption
const response = await fetch(url, {
  method: "POST",
  headers,
  credentials: "include",
  body,
});
const reader = response.body.getReader();
// Parse "data:" lines, extract JSON, call onToken(parsed.content)
```

### Multimodal Support (File Attachments)

The chat API accepts file uploads via multipart form data, using Spring AI's `Media` API:

```java
// Build UserMessage with media attachments
UserMessage.builder()
    .text("Analyze this document")
    .media(Media.builder()
        .mimeType(MimeType.valueOf("application/pdf"))
        .data(new ByteArrayResource(fileBytes))
        .name("document.pdf")
        .build())
    .build();
```

**Supported formats**: Images (PNG, JPEG, GIF, WebP), Documents (PDF, CSV, DOC/DOCX, XLS/XLSX, HTML, TXT, Markdown), Video formats.

### Spring AI Message Types

- `UserMessage` — supports text + media attachments via builder
- `AssistantMessage` — text + tool calls + media
- `SystemMessage` — system prompts
- `Prompt` — accepts `List<Message>` for proper conversation history

### Provider-Specific Notes

| Provider        | Chat | Embedding | Notes                                                                                           |
| --------------- | ---- | --------- | ----------------------------------------------------------------------------------------------- |
| OpenAI          | Yes  | Yes       | Full support                                                                                    |
| Ollama          | Yes  | Yes       | Local models                                                                                    |
| Anthropic       | Yes  | No        | No embedding API — throws `UnsupportedOperationException`                                       |
| Gemini (native) | Yes  | No        | Uses `GoogleGenAiChatModel` with `com.google.genai.Client`; plugin type `gemini`                |
| Gemini (OpenAI) | Yes  | No        | Uses OpenAI-compat endpoint at `generativelanguage.googleapis.com`; plugin type `gemini-openai` |
| Azure OpenAI    | Yes  | Yes       | `AzureOpenAiEmbeddingModel` uses constructor (no builder)                                       |

### Tool Calling (Semantic Navigation Chat)

The Semantic Navigation chat uses Spring AI's tool calling with `ToolCallingChatOptions`:

```java
// Define tools via @Tool annotation in a service
@Tool(name = "search_site", description = "...")
public String searchSite(String siteName, String locale, String query, ...) { ... }

// Use MethodToolCallbackProvider to create callbacks
ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
    .toolObjects(toolService).build().getToolCallbacks();

// Pass tools via DefaultToolCallingChatOptions with internal execution
var options = DefaultToolCallingChatOptions.builder()
    .toolCallbacks(callbacks)
    .internalToolExecutionEnabled(true)
    .build();

Prompt prompt = new Prompt(messages, options);
chatModel.stream(prompt); // Model automatically executes tools
```

- `internalToolExecutionEnabled(true)` — lets the ChatModel handle the tool call loop internally
- Tools: `list_sites`, `get_site_fields`, `search_site` (mirrors Turing MCP Server tools)
- Endpoint: `/api/v2/llm/{id}/semantic-chat`

### API Key Management

- API keys stored encrypted via `TurSecretCryptoService`
- Decrypted at runtime when creating provider instances
- Provider options (JSON) stored in `TurLLMInstance.providerOptionsJson`

---

## Further reading (moved out of this file)

Platform overview, AI-agent capabilities, SDK usage examples, deployment, and
research positioning were moved to
[`docs/products/turing-platform-overview.md`](docs/products/turing-platform-overview.md)
to keep this per-turn instructions file lean. That doc is human/reference
material — not per-turn coding guidance.
