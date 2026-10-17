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
pool**: a nullable `tenantId` where `null` is the platform-provided global
instance.

## The non-negotiable invariant: off means legacy

Everything hides behind **`turing.tenancy.enabled` (default `false`)**. When off,
every request resolves the immutable `DEFAULT` tenant, the discriminator is a
constant equality, and single-tenant installs are byte-for-byte unchanged. No
existing test needs revalidation; no asset/core/secret needs migration.

## Where isolation actually lives (the leak-surface checklist)

The `@TenantId` columns are the easy 80%: Hibernate enforces them once wired.
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
> (`turing.solr.cloud=false`: the Cloud topology, started with
> `solr-precreate turing`) a tenant whose core was never provisioned (engine
> offline at creation time, restored without the core, etc.) would otherwise
> `404` on its first index. `TurSNProcessQueue` now self-heals: before the first
> CREATE for a `(site, locale)` in a batch it verifies the core exists and
> creates it on demand from the locale's configset (deduped per batch: one
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
   (`runForTenant` / `runAsSystem`): the only sanctioned, audited bypass.

## Identity & access

- **Authorities**: `TurAuthorityResolver` adds `TENANT_<id>` and
  `TENANT_<id>_<ROLE>` per active membership, in both the OIDC and session paths.
- **Signup**: `POST /api/signup` creates a tenant + OWNER membership (idempotent,
  slug-validated, reserved-word guarded).
- **Switch / my tenants**: `GET /api/tenants/mine`, `POST /api/tenants/{slug}/switch`.
- **Platform admin**: `GET/POST /api/platform/tenants/**` (list / suspend /
  activate / impersonate): `ROLE_PLATFORM_ADMIN` only.
- **Resolution priority** (`TurTenantResolutionFilter`): JWT `tenant` claim →
  session attribute → subdomain → `X-Turing-Tenant` header. A suspended tenant
  or a missing active membership is rejected with 403 (platform admins bypass).
- **Server-to-server propagation (T334)**: an internal product (e.g. Dumont
  calling `http://turing:2700` on behalf of an end user) carries no end-user
  membership. Set `turing.tenancy.internal-token` and have the caller send that
  value in `X-Turing-Internal-Token` alongside `X-Turing-Tenant`; the filter
  then honours the stamped tenant **without** the membership check (suspension
  still blocks; comparison is constant-time). Empty token = feature off. Prefer
  forwarding the end user's bearer token: the JWT-claim + membership path above
  then applies unchanged and no internal token is needed. Share the token only
  over a trusted internal network.

## Quotas (plan-based)

`TurTenantQuotaService` maps `TurTenant.plan` → limits (`FREE` is bounded; paid/
custom plans are unlimited). Enforced at create (402) and LLM admission (429).

**Where the gate actually sits** (T963: until then it sat nowhere, and nothing
called it):

| Limit | Call site | Why there |
| ----- | --------- | --------- |
| `agents` | `TurAIAgentAPI.turAIAgentAdd` | The HTTP boundary *is* the exemption: the startup seeders and the import path create agents through the repository, and neither is a tenant spending a plan. |
| `sites` | `TurSNSiteAPI.turSNSiteAdd` | Same, and before the name-collision check: renaming does not get an operator past a plan limit. |
| `monthlyTokens` | `TurLlmModelFactory.createChatModel` / `createStreamingChatModel` | The single boundary every LLM call comes through. The alternative was a list of ~45 endpoints, which is a list that stops being complete; the two path-regex filters that already try it have both drifted. |
| `agents` + `sites`, in bulk | `TurImportAPI` and `TurMarketplaceAPI` (T981) | Import creates many at once, so both endpoints ask `inspectDemand` what the archive would **add** (entries colliding with an existing id are updates, not creations) and refuse the whole batch before a byte is written. A half-applied import is worse than a refused one. |

The first-boot seed (`TurExportImportOnStartup`) reaches the same exchange method
and stays **exempt**: it provisions a fresh install before any tenant exists.
That exemption is structural (the seeder never sees the quota service) and
`TurTenantQuotaBatchTest` fails if a later change gives it one.

**The counts are per tenant, and that is tested** (T982). Everything above rests
on `count()` being scoped by the `@TenantId` discriminator: were it not, one
tenant's agents would exhaust another's plan: a cross-tenant leak presenting as
a billing decision. `TurTenantQuotaIT` runs it against a real Hibernate session
with two FREE tenants and asserts the load-bearing half: that the second tenant
is admitted its first agent while the first is full.

`createEmbeddingModel` is deliberately **not** admitted: embeddings are what
indexing and retrieval run on, so refusing them would turn an exhausted chat
credit into a search outage. Single-tenant installs are unaffected:
`currentLimits()` returns `UNLIMITED` and returns before counting anything.

**The refusal body is asserted as a response** (T973). `TurQuotaRefusalResponseTest`
runs the advice and reads what the console receives: the status, the `type` URI,
and `resource` / `used` / `limit` / `resetsAt` / `upgradeUrl` under the names
`src/lib/quota-refusal.ts` reads them by. Both sides pin the type URI as a
literal, because the console returns null for any other `type`, so a drift
there would not break a build, it would silently stop the banner rendering.

**The refusal carries the exit** (T959). Both statuses answer with an RFC 7807
body typed `tag:viglet.com,2026:errors/quota-exhausted`, carrying `resource`,
`used`, `limit`, `resetsAt` (the monthly token window only: a resource count
does not come back by waiting) and, when the install shares a secret with the
managed service, a signed `upgradeUrl`. The console raises one banner from it at
the bento shell, offering the paid plan and bring-your-own-key at equal weight.

The upgrade target is signed with HMAC-SHA256 over
`v1|turing|{installId}|{tenantId}|{resource}|{exp}`, keyed by
`turing.viglet-ai.upgrade-signing-secret` when pinned and otherwise by the
credential T957 paired with. An install with neither ships the refusal **without**
a paid exit rather than with an unsigned link: unsigned, that link is an open
redirect with a forgeable tenant claim.

**The managed credential is bound to its install** (T960). Pairing mints an
Ed25519 keypair, registers the public half with the managed service and keeps the
private half encrypted in `GLOBAL_VIGLET_AI_INSTALL_PRIVATE_KEY`: no API exposes
it. Every call the `viglet` provider makes then carries `X-Viglet-Install` and
`X-Viglet-Install-Assertion` (`v1.{installId}.{exp}.{nonce}.{sig}` over
`v1|turing|{installId}|{exp}|{nonce}`), so a credential lifted out of the database
buys nothing: without the assertion the managed side refuses it, and revocation
lands on one install rather than an operator's whole account. The BYOK path sends
neither header and is never metered.

The assertion is short-lived, and a chat model is not (T964). The streaming
transport mints one **per request**. The blocking one cannot (the OpenAI Java
SDK bakes headers into its client at build time with no interceptor, and Spring
AI's per-prompt `httpHeaders` reach only its legacy RestClient transport) so
`TurRefreshingHeaderChatModel` rebuilds the model once its assertion is past
half-life instead. A hundred-call eval run over ten minutes rebuilds ten times,
not a hundred, and never sends an expired binding.

**Rotating the key after a leak** (T965): `POST /api/genai/viglet-ai/pairing/rotate-key`,
surfaced as *Rotate key* in Global Settings beside the URL-signing secret (hidden
on an install that never paired). The order is mint → register → swap: the
candidate is generated in memory, offered to the managed service **signed with
the key still in force**, and adopted only once that registration is accepted.
A refusal or an unreachable endpoint therefore leaves the old key working, and
is reported as "unchanged" rather than as an error: an install that adopts a
key nobody registered has revoked itself.

**The wire is asserted, not just described** (T968). `TurVigletWireContractTest`
binds a `MockRestServiceServer` to the pairing client and checks the requests
that actually leave: the three paths, every JSON key, the two header names, the
canonical signed payload and the four RFC 8628 error strings. Those assertions
are the fixtures the Viglet Cloud side (its Block T, C125–C131) implements
against: if one has to change, it changes in both repositories on the same day.

`TurVigletCallHeaderContractTest` does the same for the `/v1` call, on both
transports: a throwaway JDK `HttpServer` stands in for the endpoint, and the test
verifies the arriving assertion against the public key pairing registered, so a
header that silently stops being sent fails here rather than at Viglet's edge.

## Teardown runbook

`TurTenantTeardownService` (platform-admin only, audited, **idempotent**):

- **Suspend**: flips status to `SUSPENDED`; the resolution filter then blocks
  the tenant's members (403).
- **Delete**: `dryRun=true` returns the plan without mutating. The real delete
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
