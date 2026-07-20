# ADR 0004 — One entity for every model kind (unify `TurEmbeddingModel` into `TurLLMInstance`)

- **Status:** Accepted (2026-07-20)
- **Deciders:** Alexandre Oliveira
- **Task:** T752 (Block BB) — the gate that unblocks the implementable
  unification tasks T753–T758. Deliverable is this ADR only; no code.
- **Related:** IMPROVEMENTS §L.3 (design rationale), ROADMAP Block BB, CHANGELOG
  Block BA (multi-model instance, T749) + Block BB (model-kind taxonomy, T750;
  visual picker, T751). Builds on T626 (cloud embedding already `@ManyToOne
  TurLLMInstance`), T622/T623 (in-process ONNX local/HF factories), and the
  T627 embedding-dimension mismatch guard.

## Context

`TurEmbeddingModel` (`embedding_model` table) duplicates most of
`TurLLMInstance`'s identity — `id`, `tenantId`, `modelName`, `description`,
`icon`, `enabled` — and for its **cloud** case already holds a
`@ManyToOne TurLLMInstance` (T626): the embedding row just names a model id
(`modelReference`) on the *same* vendor connection. `TurRagContextBuilder
.resolveEmbeddingModel(...)` proves it — for a cloud embedding it clones a
detached `TurLLMInstance` from the embedding's `turLLMInstance` and overrides
`modelName` with `modelReference`. An embedding model is therefore **not a
different kind of provider; it is a different kind of model on the same
provider.**

Only the two **in-process ONNX** modes have no vendor connection at all:
`TurLocalEmbeddingModelFactory.PROVIDER_TYPE = "TRANSFORMERS_LOCAL"` (hand-typed
`modelPath`/`tokenizerPath`) and `TurHuggingFaceEmbeddingModelFactory
.PROVIDER_TYPE = "HUGGINGFACE"` (a HF repo id in `modelReference`). Both run the
same in-process ONNX runtime and are selected by the string `providerType`
sentinel rather than a real vendor.

With the T750 model-kind taxonomy in place (an embedding model is just
`kind=EMBEDDING`), the duplication is now removable. The risk is **entirely on
the read path**: `TurRagContextBuilder`, the Lucene/pgvector/Chroma/Milvus vector
stores, batch re-embedding, SN-site GenAI, `TurDefaultRagInfraOnStartup`, and the
store API all resolve a `TurEmbeddingModel` **by id** today. Repointing them must
preserve (a) embedding **dimensions** — a dimension change silently invalidates a
built index (the T627 guard) — and (b) the GLOBAL-vs-tenant visibility rule (both
entities are `VigletTenantOwnedInfra` with the explicit `tenantId = :current OR
IS NULL` read, deliberately **not** `@TenantId`).

## Decision

Collapse `TurEmbeddingModel` into a unified **`TurLLMInstance`** that represents a
**model-provider instance** (a vendor connection + generation params) able to
serve models of any kind, with a **default model name per kind**.

### 1. Keep the entity name `TurLLMInstance` (do **not** rename to `TurModelInstance`)

Extend the existing `TurLLMInstance` / `llm_instance` table in place. A rename to
`TurModelInstance` would churn the exchange format, the REST API, the SDK,
`shared-types`, and dozens of references for **zero functional gain** — and Block
BA/BB already evolved `TurLLMInstance` into a multi-model provider instance
(`modelNames` superset T749, kind-aware pickers T750/T751). The class Javadoc will
state that "LLM instance" now means "model-provider instance". **Rejected:**
rename (pure churn, high blast radius, breaks external callers).

### 2. A default model name per kind (nullable columns on `TurLLMInstance`)

- **chat default** = existing `modelName` — **unchanged contract** (every current
  consumer keeps working; this is why the migration is safe by construction).
- **embedding default** = new `embeddingModelName` (the id sent to the vendor —
  maps from the old `modelReference`, or `modelName` for a pure embedding row).
- **rerank default** = new `rerankModelName` (what the Block N reranker reads when
  its mode is a provider reranker).

Any of the three may be null; which are populated implies which kinds the
instance serves. Serving **one** default per kind is deliberate — multiple
simultaneous embedding models on one instance is a non-goal (§L.3).

### 3. Fold the embedding-serving fields onto the instance (populated only for embedding-capable instances)

`embeddingModelReference` folds into `embeddingModelName`; the in-process ONNX
fields move over verbatim as nullable columns used only by the local/HF vendors:
`embeddingModelPath`, `embeddingTokenizerPath`, `embeddingBatchSize`, and the
detected `embeddingDimensions` (carried verbatim so the **T627 dimension guard
sees identical dimensions** after the move). These stay null on a chat-only
instance, so an existing chat instance's row is materially unchanged.

### 4. `TRANSFORMERS_LOCAL` + `HUGGINGFACE` become first-class `TurLLMVendor`s (T754)

Register the two in-process modes as `TurLLMVendor` rows whose plugin runs the
existing `TurLocalEmbeddingModelFactory` / `TurHuggingFaceEmbeddingModelFactory`
in-process. A "local embedding" is then simply an instance whose **vendor**
happens to run in-process and whose embedding serving fields carry the ONNX paths
(or HF repo id). `factory.supports(providerType)` becomes a vendor-plugin lookup,
matching the `TurGenAiLlmProviderFactory` convention. This retires the
`providerType` string sentinel.

### 5. Migration preserves the row id — 1:1, non-destructive (T755)

Each `embedding_model` row migrates to a unified `TurLLMInstance` **with the same
`id`**. Reference stability is the priority: any consumer that stored an
`embeddingModelId` (RAG-infra config, store bindings, agent embedding refs)
keeps resolving against an instance carrying that id — **no foreign-key rewrite,
no re-index.** Per row:

- **cloud** (`turLLMInstance != null`): the new instance copies vendor / url /
  encrypted key / tenantId from its parent `llm_instance`, sets
  `embeddingModelName = modelReference`, leaves chat `modelName` null.
- **local/HF** (`TRANSFORMERS_LOCAL` / `HUGGINGFACE`): the new instance takes the
  matching in-process vendor (decision 4) and the ONNX serving fields.

`tenantId` is carried verbatim, so GLOBAL (`null`) embeddings stay GLOBAL and the
`tenantId = :current OR IS NULL` visibility is preserved. The `embedding_model`
table is **left intact as a read-only compatibility shadow** for one release
(rollback + old-export import), and the app stops writing it after migration;
a later block drops it. **Rejected:** destructive drop in the same block (no
rollback, and it would strand any missed reference); folding cloud embeddings
into their parent instance (changes the id → breaks stored references).

Trade-off accepted: a migrated cloud embedding becomes a standalone
embedding-kind instance that duplicates its parent's connection, rather than
folding into it. De-duplication (one instance serving chat **and** embedding) is
available going forward via the per-kind pickers (T757) for **new** instances;
existing rows favour reference stability over tidiness.

### 6. Exchange + API/SDK compatibility (T758)

Old embedding exports stay importable (the importer maps a legacy
`TurEmbeddingModel` export onto a unified instance via the decision-5 rules); the
existing embedding endpoints are aliased onto the unified entity so external
callers and the Cloud GLOBAL seed ZIP (T373) still import; `shared-types` / SDK
gain the per-kind default fields additively.

## Consequences

- **T753** adds the unified schema — the three per-kind default columns + the
  folded embedding-serving columns on `llm_instance` via a new idempotent
  Liquibase file — and keeps `embedding_model` until T755.
- **T754** models the two ONNX modes as vendors.
- **T755** runs the id-preserving, non-destructive data migration (+ an
  integration test proving a pre-existing RAG index still resolves its embedding
  model, dimensions intact, after the move — the highest-risk step).
- **T756** repoints every read-path consumer off `TurEmbeddingModel` onto the
  unified model reference (RAG context builder, vector stores, batch
  re-embedding, SN-site GenAI, default-RAG-infra startup, store API) — the second
  step that needs the resolve-after-migration integration test.
- **T757** ships the unified admin UI (kind tabs/filter, an Embedding-default
  picker filtered to `kind=EMBEDDING` reusing the T751 pickers) and retires
  `/bento/embedding` with a redirect.
- **T758** lands the exchange/API/SDK compatibility layer.
- This decision is mirrored into IMPROVEMENTS §L.3 so it guides day-to-day work
  without a doc dive. **Non-goal:** no change to how embeddings are *computed* —
  this is a persistence/UX consolidation, not a new embedding runtime.
