# Multi-Tenancy (Block J)

> One JVM process, many isolated tenants. This is the operator + developer guide
> for Turing's multi-tenancy (tasks T257–T284). For the design rationale see the
> git history of `docs/IMPROVEMENTS.md` §XIV; for status see `docs/CHANGELOG.md`.

## The model in one paragraph

Tenant data isolation is a **discriminator column**: Hibernate's native
`@TenantId` adds the partition column to inserts and a `WHERE tenantId = ?` to
every read, driven by a `CurrentTenantIdentifierResolver` that reads a
request-scoped `TurTenantContext`. Identity is a **single Keycloak realm + a
`tenant` claim**; a `TurTenant` aggregate and a `TurTenantMembership` join let
one user belong to many tenants. Shared infrastructure (LLM/embedding/store/SE/
MCP/integration instances) is **tenant-owned (BYO keys) with an optional shared
pool** — a nullable `tenantId` where `null` is the platform-provided global
instance.

## The non-negotiable invariant — off means legacy

Everything hides behind **`turing.tenancy.enabled` (default `false`)**. When off,
every request resolves the immutable `DEFAULT` tenant, the discriminator is a
constant equality, and single-tenant installs are byte-for-byte unchanged. No
existing test needs revalidation; no asset/core/secret needs migration.

## Where isolation actually lives (the leak-surface checklist)

The `@TenantId` columns are the easy 80% — Hibernate enforces them once wired.
The breach surface is every path that bypasses the ORM. Each is closed:

| Surface | Tenant-safe form | Where |
|---|---|---|
| JPA reads/writes | `@TenantId` discriminator | `TurSNSite` (pilot) + all aggregate roots |
| Hazelcast `@Cacheable` | key prefixed with tenant | `TurTenantCacheKeyGenerator` (default key generator) |
| Solr/ES cores | `t<shortId>_<name>` prefix (name-keyed cores only) | `TurTenantCoreNaming` |
| Object storage | `tenants/<id>/` prefix + traversal guard | `TurTenantScopedStorageService` |
| Lucene vector store | `…/lucene-vector/<id>/<collection>` | `TurLuceneStoreProvider` |
| Secret crypto | `PBKDF2(master, tenantId)` derived AES key | `TurSecretCryptoService` |
| Artemis JMS | `turingTenantId` message header | `TurJmsTenantPropagation` |
| `@Scheduled` sweeps | per-active-tenant fan-out | `TurTenantScheduledFanOut` |
| Reactive / `@Async` hops | Micrometer context propagation + `TaskDecorator` | `TurReactiveTenantPropagationConfig` |

> **Note on UUID-keyed cores.** `rag_<storeId>` and `intent_<agentId>` use
> globally-unique UUIDs, so they cannot collide across tenants; the SN core uses
> the human-chosen site *name* and therefore IS prefixed.

> **On-demand SN core provisioning (T335).** The prefixed SN core is created at
> site/locale creation (`TurSNTemplate.createSolrCore`). On a **standalone Solr**
> (`turing.solr.cloud=false` — the Cloud topology, started with
> `solr-precreate turing`) a tenant whose core was never provisioned (engine
> offline at creation time, restored without the core, etc.) would otherwise
> `404` on its first index. `TurSNProcessQueue` now self-heals: before the first
> CREATE for a `(site, locale)` in a batch it verifies the core exists and
> creates it on demand from the locale's configset (deduped per batch — one
> `indexExists` check per distinct core, not per document). **Operator
> requirement:** the standalone Solr must expose the `en`/`es`/`pt`/`ca`
> configsets (the project Solr image copies them to
> `/var/solr/data/configsets`); a bare `solr-precreate turing` image only ships
> `_default`, so mount/add the configsets or core creation will fail with a
> missing-configset error.

## How to make a NEW entity tenant-safe

1. **Tenant-owned content** (an agent's/site's data): add
   `@org.hibernate.annotations.TenantId @Column(name = "tenantId", length = 40)
   private String tenantId;` and a Liquibase column (nullable + `defaultValue:
   DEFAULT`, then backfill NULLs to `DEFAULT`, then flip NOT NULL in a follow-up
   changelog). Hibernate does the rest.
2. **Shared infrastructure** (BYO keys, a global pool): add a **plain** nullable
   `tenantId` (NOT `@TenantId`) and a `findVisibleToTenant(:t)` repository query
   (`tenantId = :t OR tenantId IS NULL`). Gate creation of `null`-tenant globals
   on `ROLE_PLATFORM_ADMIN`.
3. **Out-of-band resource** (a new cache / core / path / queue): prefix it with
   the current tenant using the matching helper above; add a negative assertion
   to the isolation suite (below).
4. **Never** read across tenants except through `TurPlatformAdminService`
   (`runForTenant` / `runAsSystem`) — the only sanctioned, audited bypass.

## Identity & access

- **Authorities**: `TurAuthorityResolver` adds `TENANT_<id>` and
  `TENANT_<id>_<ROLE>` per active membership, in both the OIDC and session paths.
- **Signup**: `POST /api/signup` creates a tenant + OWNER membership (idempotent,
  slug-validated, reserved-word guarded).
- **Switch / my tenants**: `GET /api/tenants/mine`, `POST /api/tenants/{slug}/switch`.
- **Platform admin**: `GET/POST /api/platform/tenants/**` (list / suspend /
  activate / impersonate) — `ROLE_PLATFORM_ADMIN` only.
- **Resolution priority** (`TurTenantResolutionFilter`): JWT `tenant` claim →
  session attribute → subdomain → `X-Turing-Tenant` header. A suspended tenant
  or a missing active membership is rejected with 403 (platform admins bypass).
- **Server-to-server propagation (T334)**: an internal product (e.g. Dumont
  calling `http://turing:2700` on behalf of an end user) carries no end-user
  membership. Set `turing.tenancy.internal-token` and have the caller send that
  value in `X-Turing-Internal-Token` alongside `X-Turing-Tenant`; the filter
  then honours the stamped tenant **without** the membership check (suspension
  still blocks; comparison is constant-time). Empty token = feature off. Prefer
  forwarding the end user's bearer token — the JWT-claim + membership path above
  then applies unchanged and no internal token is needed. Share the token only
  over a trusted internal network.

## Quotas (plan-based)

`TurTenantQuotaService` maps `TurTenant.plan` → limits (`FREE` is bounded; paid/
custom plans are unlimited). Enforced at create (402) and LLM admission (429).

## Teardown runbook

`TurTenantTeardownService` (platform-admin only, audited, **idempotent**):

- **Suspend** — flips status to `SUSPENDED`; the resolution filter then blocks
  the tenant's members (403).
- **Delete** — `dryRun=true` returns the plan without mutating. The real delete
  purges the storage prefix, deletes the Lucene directory, evicts caches, and
  removes the membership + tenant rows. The `@TenantId` content becomes
  *unreachable* immediately (the resolver never yields a deleted tenant id); a
  physical row sweep, the external Solr/ES core drop, and Keycloak attribute
  cleanup require the live engines and are deployment-specific runbook steps.

## MCP server (future, T282)

When Block I ships the Turing-**as**-MCP-server `/mcp` endpoint, it inherits
tenant binding for free: `TurTenantResolutionFilter` runs on every request and
already resolves the tenant from the inbound JWT `tenant` claim / header. No
MCP-specific tenant code is needed beyond exposing the claim on the `/mcp`
OAuth token.

## Escalation seam (future, T284)

`TurTenant.isolationMode` (`SHARED` | `SCHEMA` | `DB`, default `SHARED`) reserves
the path to migrate a heavy enterprise tenant to a dedicated schema/database via
a pluggable `CurrentTenantIdentifierResolver` + `MultiTenantConnectionProvider`,
without a data-model change. Only `SHARED` is implemented today.

## The ship gate

No SaaS endpoint goes live until the cross-tenant isolation suite passes:
`TurSNSiteTenantIsolationIT` (JPA), `TurTenantInfraVisibilityIT` (BYO infra
`current ∪ NULL`), `TurCrossTenantIsolationIT` (secret crypto end-to-end), and
the helper unit tests (cache key, core naming, storage prefix). Run with
`mvn test -pl turing-app -Dskip.npm=true -Dtest="*Tenant*"` (ITs boot the full
context with `turing.tenancy.enabled=true`).
