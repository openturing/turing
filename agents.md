# Turing: Enterprise Search Intelligence Platform

## Development Guidelines

> **Situational guidance now lives in skills.** Deep, subsystem-specific
> conventions were moved out of this always-loaded file into on-demand Claude
> Code skills under [`.claude/skills/`](.claude/skills/). Claude auto-invokes a
> skill when its `description` matches the task; you can also invoke one
> explicitly with `/<skill-name>`. The pointers below say which skill to reach
> for. This file keeps only the always-relevant core.
>
> | Skill | Reach for it when… |
> | ----- | ------------------ |
> | `roadkeep` | **the write path for `docs/ROADMAP.md`/`CHANGELOG.md`/`IMPROVEMENTS.md`/`STRATEGY.md`** — adding, shipping, retiring, deferring or sectioning a task, and every query about the backlog |
> | `turing-roadmap-docs` | the Turing rules `roadkeep` does not enforce — **where new work goes (reuse a block, don't mint a letter)**, one-task-one-commit + `/loop`, the next block letter, propagating a shipped user-facing task to the `docs-turing` site |
> | `turing-persistence` | writing JPA entities, `@Column` annotations, associations (LAZY rule), or Liquibase changelogs |
> | `turing-integration-tests` | creating or debugging a `*IT.java` / `@SpringBootTest` / context-startup hang |
> | `turing-frontend-testing` | adding or debugging a `*.test.tsx` (vitest + RTL) |
> | `turing-spring-ai` | LLM providers, streaming/multimodal chat, embeddings, Spring AI tool calling |
> | `turing-storage` | asset/file storage, MinIO, filesystem storage, uploads |
> | `turing-currency-decimal` | currency/decimal fields, Solr `CurrencyFieldType`, decimal-separator localization |
> | `turing-caching` | `@Cacheable`/`@Scheduled`, cache config, Jackson 3 serialization |
> | `turing-domain-layer` | `*Domain` records, `*RepositoryPort`, adapters/mappers, hexagonal layer decisions |
> | `turing-search-lucene` | search-engine plugins (Solr/ES/Lucene), Lucene 10, faceting, vector store |
> | `turing-llm-summary` | cached LLM insights / `TurLlmSummaryService` features |
> | `turing-resizable-panel` | `ResizablePanel` layouts, side-panel / side-by-side AI authoring |
> | `turing-bento-ui` | building or fixing a `/bento` page — `BentoHero`/`BentoEntityShell`/`BentoFormSection`/`BentoListPage`, frosted hover cards, or a page still showing console chrome (sidebar/`SectionCard`) |

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

### Worktrees & the `latest` alias

This repo is checked out as **git worktrees**, one directory per version, e.g.:

- `D:\Git\viglet\turing\2026.3` — branch `2026.3` (current default working branch)
- `D:\Git\viglet\turing\2026.2` — branch `2026.2`

`D:\Git\viglet\turing\latest` is a **directory junction** that always points at
the version currently being worked on (today `2026.3`). It is the default target
for cross-project tasks (e.g. from dumont/shio) when no version is named; name a
specific version only for the exception.

**To repoint `latest` to a new version** (e.g. after starting `2026.4`), run in
PowerShell:

```powershell
# 1. create the new version's worktree if it doesn't exist yet
git -C D:\Git\viglet\turing\2026.3 worktree add ..\2026.4 2026.4

# 2. repoint the latest junction (Remove-Item on a junction deletes only the
#    link, never the target's files)
Remove-Item D:\Git\viglet\turing\latest -Force
New-Item -ItemType Junction -Path D:\Git\viglet\turing\latest -Target D:\Git\viglet\turing\2026.4
```

Verify with `git -C D:\Git\viglet\turing\latest rev-parse --abbrev-ref HEAD`
(should print the new branch) and `git worktree list`.

### Key Paths

- Backend source: `turing-app/src/main/java/com/viglet/turing/`
- Backend tests: `turing-app/src/test/java/com/viglet/turing/`
- Frontend source: `frontend/apps/turing-app/src/`
- Frontend route definitions: `frontend/apps/turing-app/src/app/routes/` (all use `React.lazy()` since 2026.2.6)
- Frontend query hooks: `frontend/apps/turing-app/src/api/queries/` (React Query v5)
- Liquibase changelogs: `turing-app/src/main/resources/db/changelog/`

### Roadmap & docs maintenance

> **The four `docs/` files are written by [`roadkeep`](https://github.com/alegauss/roadkeep)
> and never by hand.** [`roadkeep.toml`](roadkeep.toml) declares which they are and this
> project's own prefix, markers, pointers and limits; the plugin's hook denies the `Edit` and
> names the command to call instead.
>
> **The rules are in the two skills, and deliberately not here.** The write path, what each
> command derives and the whole query surface are the **`roadkeep` skill**, which ships with
> the plugin and is therefore the same text in every adopting project. The Turing rules
> roadkeep does *not* enforce — where new work is filed, reusing a block instead of minting a
> letter, one-task-one-commit + `/loop`, the next block letter, propagating a shipped
> user-facing task into the `docs-turing` site — are the **`turing-roadmap-docs` skill**. A
> rule restated here is a rule that can disagree with the tool that enforces it.

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

- **Spring Boot 4** with **Spring AI 2.0.0-M2** for LLM integration (details → `turing-spring-ai` skill)
- Persistence: JPA/Hibernate with H2 (dev) and Liquibase migrations (details → `turing-persistence` skill)
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

### JPA, Liquibase & Persistence

> **Moved to the `turing-persistence` skill.** Column naming (no implicit
> snake_case — camelCase maps literally), Liquibase conventions (new file per
> change, idempotent preconditions, portable lowercase types, `longtext` not
> `clob`), and the `FetchType.LAZY`-by-default rule for all associations. Invoke
> the skill when writing entities, `@Column` annotations, or changelogs.

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
- **Layout components** (`ResizablePanel`, side-panel / side-by-side AI authoring)
  → `turing-resizable-panel` skill.

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
- **Writing a `*IT.java` (`@SpringBootTest`)?** → `turing-integration-tests` skill
  (extend `AbstractTuringSpringIT`, JMX-via-`properties`, per-JVM Artemis dir).
- **Writing a frontend `*.test.tsx`?** → `turing-frontend-testing` skill
  (vitest + RTL + jsdom, global mocks, SSE `fetch` stubbing).

### Common Pitfalls

- `@Column(length=N)` must accommodate all enum/ID values (e.g., "AZURE_OPENAI" = 12 chars)
- **MySQL primary key limit**: Max key length is 3072 bytes. With `utf8mb4` (4 bytes/char), `VARCHAR` PKs must be ≤ 768 chars. Example: `VARCHAR(1024)` PK fails on MySQL — use `VARCHAR(768)` instead
- Spring AI 1.1.2: `AzureOpenAiEmbeddingModel` has no builder — use constructor
- Anthropic provider doesn't support embedding — throws `UnsupportedOperationException`. Gemini **does** (T495): `TurGeminiEmbeddingModel` over `embedContent` with asymmetric task types (RETRIEVAL_DOCUMENT at index / RETRIEVAL_QUERY at query) + optional Matryoshka `outputDimensionality`
- Frontend npm builds may fail with EBUSY on Windows if IDE locks files — use `-Dskip.npm=true` for backend-only testing

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

### Frontend TypeScript Check (Windows EBUSY workaround)

When `node_modules` native binaries are locked by the IDE/Vite dev server:

1. **Don't loop** retrying `npm ci`, `npm install`, `npx tsc`, or other install variations — they will all fail with EBUSY/EPERM while the IDE holds locks.
2. **Rely on IDE diagnostics** from VSCode hook feedback (`ide_diagnostics` in tool responses) — these provide real-time TypeScript error checking without needing `tsc`.
3. **Backend compile is the primary gate** — run `mvn compile -pl turing-app -Dskip.npm=true` to verify Java code. For the frontend, prefer `pnpm exec tsc -b --noEmit` from `frontend/apps/turing-app` when locks clear.

---

## Subsystem deep-dives — see `.claude/skills/`

The following areas each have a dedicated skill (auto-invoked by description, or
`/<skill-name>`). They were moved out of this file to keep per-turn context lean:

- **Search Engine & Lucene** (`TurSearchEnginePlugin`, Lucene 10 gotchas, vector store) → `turing-search-lucene`
- **Pluggable Storage** (`TurStorageService`: NONE/MINIO/FILESYSTEM) → `turing-storage`
- **LLM Summary Service** (cached AI insights) → `turing-llm-summary`
- **Cache, Scheduling & Serialization** (incl. "never cache repositories", Jackson 3, ShedLock) → `turing-caching`
- **Domain layer** (ports & adapters, bounded by design) → `turing-domain-layer`
- **Decimal & Currency demands** playbook → `turing-currency-decimal`
- **Spring AI Integration** (providers, streaming, multimodal, tool calling) → `turing-spring-ai`

---

## Further reading (moved out of this file)

Platform overview, AI-agent capabilities, SDK usage examples, deployment, and
research positioning were moved to
[`docs/products/turing-platform-overview.md`](docs/products/turing-platform-overview.md)
to keep this per-turn instructions file lean. That doc is human/reference
material — not per-turn coding guidance.
