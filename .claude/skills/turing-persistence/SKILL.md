---
name: turing-persistence
description: Turing JPA + Liquibase persistence rules — column naming (no implicit snake_case, camelCase maps literally), Liquibase changelog conventions (new file per change, idempotent preconditions, portable lowercase types, longtext not clob), and the project-wide FetchType.LAZY-by-default rule for associations. Use whenever writing or modifying JPA entities, @Column/@Entity annotations, associations, or adding/editing Liquibase changelogs and database migrations.
---

# JPA & Liquibase persistence

## JPA & Database Column Naming

- **No implicit naming strategy** is configured — Hibernate maps Java field names **literally** to column names (no snake_case conversion)
- A Java field `sitePrompt` maps to column `sitePrompt`, **not** `site_prompt`
- When adding `@Column` annotations, use the exact camelCase field name (or omit `name` to let Hibernate use the default)
- **Never use snake_case** in Liquibase column names unless the existing table already uses it — always match the convention of the target table
- Existing tables like `sn_site_genai` use camelCase columns: `systemPrompt`, `sitePrompt`
- Existing tables like `sn_site` use lowercase: `description`, `name`, `facet`
- When in doubt, check the existing columns in the entity class or run a query against the H2 dev database

## Liquibase Conventions

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

- Liquibase changelogs live in `turing-app/src/main/resources/db/changelog/`.
- Liquibase updates on reused local H2 files can fail with `Duplicate column name`;
  make changeSets idempotent with `preConditions` (`columnExists` + `onFail: MARK_RAN`).

## JPA fetch strategy: LAZY by default

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
