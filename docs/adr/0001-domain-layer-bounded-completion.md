# ADR 0001 — Domain layer (ports & adapters): bounded completion, not 100%

- **Status:** Accepted (2026-07-02)
- **Deciders:** Alexandre Oliveira
- **Task:** T541 (Block AF) · **Supersedes:** the implicit "read-only pilot, more
  slices later" stance that lived only in class Javadoc and "scaffolding only"
  commit messages.
- **Related:** T540 (benchmark), T542–T545 (execution), Block AC / T486–T488
  ("never cache a JPA entity"), the EAGER 200ms→5s search incident.

## Context

A hexagonal (ports & adapters) migration was started at 2026.2.6, authored solo.
Three coordinated pieces exist under `com.viglet.turing.domain.*` and
`com.viglet.turing.persistence.adapter.*`:

- **`*Domain` records** — immutable, free of JPA/Hibernate/Jackson annotations,
  referencing neighbouring aggregates **by ID** (not by object reference).
- **`*RepositoryPort` interfaces** — what application services depend on.
- **adapters + MapStruct mappers** — `@Component` adapters that delegate to the
  *existing* JPA repositories and map entity → record.

State on entry to Block AF: a **read-only pilot**. A minority of app/API files
consume ports; the large majority still use the JPA repositories directly; write
paths are untouched; several ports were committed "scaffolding only"; and there
was **no written decision** — every port's Javadoc promised "future slices will
introduce `save` / `delete`", advertising the layer as perpetually in progress.

## Decision

**Do not take the migration to 100%. Bound it to the paths where an immutable,
lazy-proxy-free, cacheable read model genuinely pays off, and make the boundary
self-enforcing.**

### The boundary (the rule)

Use a **`*Domain` record + `*RepositoryPort`** when the read is on a:

1. **hot read path** — search, chat/RAG, autocomplete — where per-request
   latency matters; **or**
2. **cached value** — anything stored in a service-level cache (records are the
   safe cacheable value; entities are not — Block AC / T486–T488); **or**
3. **cross-aggregate read** — a read that would otherwise walk `@ManyToOne` /
   `@OneToOne` associations across aggregates (the EAGER-cascade hazard); **or**
4. **LazyInit-prone read** — a value returned, cached, or passed **outside** the
   originating transaction/session, where a lazy proxy would otherwise escape.

Use the **JPA repository directly** (with `@Transactional` + `LEFT JOIN FETCH`
for the to-one graph the caller touches — per T488, not `@EntityGraph`, and not
a repository `@Cacheable`) for:

- **admin / CRUD** reads and listings that are not hot, not cached, and not
  LazyInit-prone; **and**
- **all writes** — `save` / `delete` / merge / cascade / optimistic locking /
  the `apiKey`·`credential` secret crypto (`TurSecretCryptoService`).

### Ports are read-only by design (permanent)

Ports will **not** grow `save` / `delete`. A write path gains nothing from an
immutable read record and everything from a managed, dirty-checked entity. This
reverses the "future slices" promise; T544 rewrites the Javadoc to say so.

### The one criterion for a *new* aggregate

> Is this aggregate read on a **hot / cached / cross-aggregate** path, or exposed
> where a **lazy proxy would escape its session**?

If **yes** → it earns a `*Domain` record (+ port, unless T546 collapses it to a
mapper-backed reader). If **no** → it stays on the JPA repository. There is no
"complete the set for consistency" obligation — partial coverage of the entity
space is the intended end state, not a debt.

## Rationale

1. **The hexagonal anchor benefit is a fantasy here.** "Swap JPA for another
   persistence" has near-zero probability for this product. Paying a permanent
   abstraction tax across all entities (scaffolding files + migrating hundreds
   of call sites) buys insurance we will never claim.
2. **The easy 20% was done; the hard 80% wasn't.** Read-only ports are trivial;
   writes (transactional consistency, cascade, optimistic locking, secret
   crypto) are where hexagonal actually hurts, and none of it was started.
3. **Half-migrated is the worst state** — *unless the boundary is written down
   and enforced.** A frozen mid-flight migration with no stop-criteria forces
   every contributor to guess which pattern applies where. This ADR + the T545
   convention test convert "half-migrated" into "deliberately bounded".
4. **The value is the read model, not the database swap.** A by-ID record
   structurally cannot trigger the EAGER cascade behind the 200ms→5s incident,
   is the safe cacheable value the "never cache an entity" rule demands, and is
   safe to return/cache/pass outside a session. That is the whole payoff, and it
   only lands on the paths above.
5. **The mapping cost is a non-issue (T540).** The `entity → MapStruct → record`
   hop measures **≈48–53 ns/op** (≈40 ns/op over direct getters, ≈0.00005 ms) —
   4–5 orders of magnitude below a millisecond-scale search request. Keeping
   ports on the hot path is free; a projection query is warranted only where the
   source entity is heavy to **load** (many columns / large LOBs), not to map.

## Consequences

- **T542** finishes the "keep" slice — the remaining hot read paths (search /
  chat / RAG) and cache read-models — so no hot path is left half on entities.
- **T543** prunes ports/adapters/mappers/records generated for pure admin-CRUD
  aggregates that touch no hot path, cache, or LazyInit risk. Dead scaffolding
  (a port with zero consumers) is removed opportunistically ahead of it.
- **T544** rewrites the "future slices will introduce save/delete" Javadoc to
  state the read-only-forever decision.
- **T545** (`TurDomainLayerConventionsTest`) makes the boundary self-policing:
  `*Domain` records carry no JPA/Hibernate/Jackson annotations; hot-path packages
  depend on ports not JPA repos; every surviving port has exactly one adapter +
  mapper. Without it the layer silently drifts back toward the 100% it was told
  not to reach.
- **T546 (💭)** may collapse a port to a mapper-backed reader where the interface
  is pure ceremony (single permanent adapter, no mocking need).

The rule is mirrored into `agents.md` ("Domain layer (ports & adapters)") so it
guides day-to-day work without a doc dive, and into agent memory.
