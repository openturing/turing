# RAG Hybrid Retrieval — Configuration & Operations Guide

T24 / T24b — vector + BM25 fusion for the `search_knowledge_base` tool.

This guide is the operator-facing companion to the code in
[`TurRagSearchToolService`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurRagSearchToolService.java),
[`TurRagRrf`](../turing-app/src/main/java/com/viglet/turing/genai/rag/TurRagRrf.java),
[`TurRagBm25CoreProvisioner`](../turing-app/src/main/java/com/viglet/turing/genai/rag/TurRagBm25CoreProvisioner.java),
and the SN site GenAI admin form.

## Table of contents

1. [Why hybrid retrieval](#why-hybrid-retrieval)
2. [The two modes — Embedded vs SE_INSTANCE](#the-two-modes--embedded-vs-se_instance)
3. [Request flow](#request-flow)
4. [Quick start — Embedded (default)](#quick-start--embedded-default)
5. [Production setup — SE_INSTANCE](#production-setup--se_instance)
   - [Solr](#solr)
   - [Elasticsearch](#elasticsearch)
6. [Admin UI walkthrough](#admin-ui-walkthrough)
7. [How locale resolves](#how-locale-resolves)
8. [Tuning](#tuning)
9. [Troubleshooting](#troubleshooting)
10. [Reference — endpoints, tables, key classes](#reference)

---

## Why hybrid retrieval

Pure vector search misses queries the embedding model wasn't trained
on — proper nouns, internal product codes, version strings (`v2026.2.7`),
acronyms, rarely-spelled names. Pure BM25 misses paraphrases and
semantically-equivalent rewordings.

Hybrid retrieval runs both in parallel and fuses the result lists via
**Reciprocal Rank Fusion** (RRF, `k=60`). A chunk that appears in both
lists ranks highest; BM25-only chunks keep a `_keyword_only_fallback`
tag so the prompt builder can ask the LLM to soft-warn the user
("based on a keyword match…").

The empirical result: documented in T24 / T24b — recall lifts on long-tail
queries without trading off precision on the head.

---

## The two modes — Embedded vs SE_INSTANCE

Hybrid retrieval has two backings for the BM25 half. The vector half is
always the configured embedding store.

| Mode          | BM25 lives in                              | Locale handling                          | Setup    | Recommended for                              |
| ------------- | ------------------------------------------ | ---------------------------------------- | -------- | -------------------------------------------- |
| `EMBEDDED`    | Single in-process Lucene index             | Single tokenization for every locale     | Zero-config | Single-language deployments, dev/test, PoCs  |
| `SE_INSTANCE` | Per-locale Solr / Elasticsearch cores      | Per-locale analyzer chain (PT/EN/ES/…)   | Provision cores | Production, multi-language, large corpora |

**`EMBEDDED` (default)** ships ready-to-use the moment you enable RAG on
an SN site. No SE config, no provisioning, no schema templates. Caveat:
every locale shares the same Lucene index with one analyzer chain — fine
when 95% of the corpus is one language, suboptimal once you cross over.

**`SE_INSTANCE`** dedicates a Solr/Elasticsearch core per SN site locale
(`rag_<storeShortId>_<locale>`, e.g. `rag_a3b2c1d4_pt-BR`). Each core
gets the right analyzer (`PortugueseAnalyzer` for `pt-*`,
`EnglishAnalyzer` for `en-*`, etc.) — stemming, stopword removal, and
tokenization match the language. The cost is one-time provisioning + a
reindex.

You can switch modes per SN site at any time. EMBEDDED is always the
safe fallback — when an SE_INSTANCE core is missing or unreachable the
runtime degrades to vector-only rather than failing.

---

## Request flow

```
SN site chat API  ─ user query + locale (BCP 47 tag) ──┐
                                                       │
                                                       ▼
        TurRagSearchToolService.searchKnowledgeBase    │
                                                       │
        resolveAgentRagFlags()  ◄── TurSNSiteGenAi     │
                bm25Enabled?                           │
                hybridEnabled?                         │
                source = EMBEDDED | SE_INSTANCE        │
                                                       │
        ┌──────────────────────────┬───────────────────┘
        ▼                          ▼
   EMBEDDED path             SE_INSTANCE path
   (TurLuceneVectorStore     ┌──────────────┐
    .hybridSearch)           │ vector pass  │  vectorStore.similaritySearch
                             │ BM25 pass    │  plugin.retrieveStandalone
                             │   for locale │      (rag_<store>_<locale>)
                             └──────┬───────┘
                                    │
                                    ▼
                            TurRagRrf.fuse(vec, bm25, topK)
                                    │
                                    ▼
                            Up to topK Documents,
                            BM25-exclusive tagged
                            keyword_only_fallback
```

The flag combo lives on `TurSNSiteGenAi` (not the agent) because RAG
only invokes through the SN site chat API, and the SN site is the
natural owner of locale (`TurSNSiteLocale`).

---

## Quick start — Embedded (default)

1. **Global Settings → RAG**: pick a default embedding model + embedding
   store. Required for both modes.
2. **SN Site → AI**: bind an AI agent that has `ragEnabled = true`.
3. Save. The "Hybrid Retrieval" card defaults to:
   - `BM25 fallback` = **enabled**
   - `Hybrid search` = **enabled**
   - `BM25 source` = **Embedded**

Index content via the existing **Reindex** button or the asset training
flow. Queries through `/api/sn/{site}/chat` will use vector + BM25 fused
via RRF the moment chunks land.

That's it for Embedded — no SE config, no separate provisioning.

---

## Production setup — SE_INSTANCE

You'll need a working Solr or Elasticsearch instance registered in
**SE Instances** (the same surface that backs SN site public search).
The RAG core lives alongside SN site cores but is named with a `rag_`
prefix and is filtered out of public search results.

### Steps (UI-driven)

1. Pick the SE engine (Solr or Elasticsearch) and register it as a SE
   Instance in **System → Search Engines**.
2. On the SN site's **AI** page, expand the **Hybrid Retrieval** card.
3. Switch **BM25 source** from `Embedded` to `Search Engine`.
4. Pick the registered SE instance in the dropdown.
5. **Save the form first** — the provisioner needs the SE binding
   persisted before it can read it back.
6. Click **Provision missing cores**. The provisioner creates one core
   per SN site locale. The status table updates with the result.
7. Trigger a full **RAG reindex** (existing button on the same form).
   This pushes every chunk from the vector store into the matching
   per-locale BM25 core.

Once every row in the status table shows `PROVISIONED` with a non-zero
`Docs` count, hybrid retrieval routes to the SE for the BM25 half.

### Solr

The provisioner uses Solr's standard
[CoreAdmin API](https://solr.apache.org/guide/coreadmin-api.html) (or
the [Collections API](https://solr.apache.org/guide/solrcloud.html)
under SolrCloud). The base configset is picked by locale language: `pt`,
`es`, `en` are shipped in `turing-app/src/main/resources/solr/configsets/`;
unknown languages fall back to `en`.

**Recommended `solrconfig.xml` overrides** (only needed if you're
using a custom configset):

```xml
<!-- BM25 with the canonical Cormack defaults. -->
<similarity class="solr.BM25SimilarityFactory">
  <float name="k1">1.2</float>
  <float name="b">0.75</float>
</similarity>
```

**Schema fields required** (Turing's standalone-index ops use these
field names verbatim; align your custom configset to match):

| Field         | Type                   | Stored | Indexed | Notes                                                      |
| ------------- | ---------------------- | ------ | ------- | ---------------------------------------------------------- |
| `id`          | `string`               | ✓      | ✓       | Chunk UUID — MUST equal the vector store doc id for RRF.   |
| `content`     | `text_<locale>`        | ✓      | ✓       | The chunk body. Analyzer chain is locale-specific.         |
| `assetId`     | `string`               | ✓      | ✓       | Source asset / file id. Drives `deIndexStandaloneByField`. |
| `chunkIndex`  | `pint`                 | ✓      |         | Ordinal within the asset (diagnostics).                    |
| `sourceFile`  | `string`               | ✓      |         | Display label.                                             |

**Locale-specific analyzers** (already in the shipped configsets):

```xml
<!-- pt -->
<fieldType name="text_pt" class="solr.TextField" positionIncrementGap="100">
  <analyzer>
    <tokenizer class="solr.StandardTokenizerFactory"/>
    <filter class="solr.LowerCaseFilterFactory"/>
    <filter class="solr.PortugueseLightStemFilterFactory"/>
    <filter class="solr.StopFilterFactory" words="lang/stopwords_pt.txt" ignoreCase="true"/>
  </analyzer>
</fieldType>
```

(Mirror for `text_en`, `text_es`. Brazilian-specific aggressive stemming
matches what `TurPersonaToneValidator` uses for tone analysis —
intentional consistency.)

### Elasticsearch

Provisioner uses the ES Java SDK directly. Index settings written at
creation:

```json
{
  "settings": {
    "index": {
      "similarity": {
        "default": {
          "type": "BM25",
          "k1": 1.2,
          "b": 0.75
        }
      }
    },
    "analysis": {
      "analyzer": {
        "rag_text": {
          "type": "<locale>",
          "stopwords": "_<locale>_"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id":          { "type": "keyword" },
      "content":     { "type": "text", "analyzer": "rag_text" },
      "assetId":     { "type": "keyword" },
      "chunkIndex":  { "type": "integer" },
      "sourceFile":  { "type": "keyword" }
    }
  }
}
```

Locale is mapped to the matching ES built-in analyzer: `pt` →
`brazilian` (mirrors the BrazilianAnalyzer choice on the Lucene side),
`en` → `english`, `es` → `spanish`. Unknown locales fall back to
`standard`.

ES auto-refreshes every second by default — `commitStandalone` is a
no-op for ES. If you've disabled `refresh_interval`, force a refresh
via `POST /<index>/_refresh` after large bulk loads or `docCount` in
the admin UI will lag.

---

## Admin UI walkthrough

The **Hybrid Retrieval** card on each SN site's **AI** page exposes
every knob the operator needs.

```
┌─ Hybrid Retrieval (RAG) ──────────────────────────────────────────┐
│                                                                   │
│  BM25 (keyword) fallback           [ Enabled ]                    │
│                                                                   │
│  Hybrid Search (Vector + BM25)     [ Enabled ]                    │
│                                                                   │
│  BM25 source                       [ Embedded ] [ Search Engine ] │
│                                                                   │
│  ─── shown only when source = Search Engine ───                   │
│  Search Engine instance            ┌───────────────────────────┐  │
│                                    │  prod-solr (Solr)         │  │
│                                    └───────────────────────────┘  │
│                                                                   │
│  Per-locale BM25 cores                                            │
│                                    [Refresh] [Provision missing]  │
│  ┌─────────┬──────────────────────┬───────────────┬──────┐        │
│  │ Locale  │ Core                 │ Status        │ Docs │        │
│  ├─────────┼──────────────────────┼───────────────┼──────┤        │
│  │ pt-BR   │ rag_a3b2c1d4_pt-BR   │ PROVISIONED   │ 1247 │        │
│  │ en-US   │ rag_a3b2c1d4_en-US   │ PROVISIONING  │    0 │        │
│  │ es-ES   │ rag_a3b2c1d4_es-ES   │ ERROR (hover) │    0 │        │
│  └─────────┴──────────────────────┴───────────────┴──────┘        │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘
```

**Status colors**:

- `PROVISIONED` — green; ready to serve.
- `PROVISIONING` / `DELETING` — blue; transient.
- `ERROR` — red; hover for the last-error message. Re-clicking
  **Provision missing** retries.
- `NOT_PROVISIONED` — gray; SE has no core yet.

**Gating rules**:

- The `BM25 source` radio is disabled when `BM25 fallback` or
  `Hybrid search` is off (the source has no effect there).
- **Provision missing** is disabled when the form is dirty (the
  SE-instance binding must be persisted first) or when no SE instance
  is selected.
- Switching back to `Embedded` clears `ragSeInstance` on the form so
  the binding doesn't drift.

---

## How locale resolves

```
SN site chat API ─POST /api/sn/{site}/chat──► TurAgentChatExecutor
                                              ToolContext.put(
                                                 "turing.locale",
                                                 "pt-BR"  (BCP 47 tag))
                                              │
                                              ▼
                                  TurRagSearchToolService.resolveLocale
                                              │
                                              ▼
                                  ragBm25CoreRepository.findByStoreId
                                      AndLocale(storeId, "pt-BR")
                                              │
                                              ▼
                                  plugin.retrieveStandalone(
                                      seInstance, "rag_a3b2c1d4_pt-BR",
                                      query, topK)
```

- Locale is published into the Spring AI `ToolContext` under
  `turing.locale` by the SN site chat API on every turn.
- Empty / missing → `Locale.ROOT` → the core lookup misses → tool
  degrades to vector-only with a `WARN` log.
- No automatic language detection on the query itself — by design.
  The SN site already knows which locale this conversation is in
  (from URL routing, user preference, browser `Accept-Language`).

---

## Tuning

### BM25 parameters

The shipped defaults (`k1=1.2`, `b=0.75`) are Lucene's canonical
defaults and match the original BM25 paper. Don't change unless you've
measured a problem:

- `k1` (term-frequency saturation): lower (`0.8`) for short
  document-like chunks where TF≥2 already saturates relevance; higher
  (`1.6`) for long chunks where repetition is informative.
- `b` (length normalization): lower (`0.5`) when your chunk lengths are
  fairly uniform — normalization adds noise. Higher (`0.9`) when chunk
  lengths vary by an order of magnitude.

Solr: edit `solrconfig.xml`. ES: reindex (cannot change similarity on
an existing index).

### RRF `k`

Hard-coded to `60` in [`TurRagRrf.RRF_K`](../turing-app/src/main/java/com/viglet/turing/genai/rag/TurRagRrf.java).
Smaller `k` sharpens the contribution of top-ranked docs (a doc at
rank 1 dwarfs a doc at rank 10); larger flattens the curve.

`60` is the value Solr's native hybrid query and ES's RRF retriever
ship with — staying compatible matters because admins comparing scores
across implementations should see consistent fusion behavior.

### When to switch from Embedded to SE_INSTANCE

Concrete signals:

- Your corpus crosses ~50k chunks in a non-English language and you
  observe BM25 hits ranking poorly on root-word queries (stemming gap).
- Two or more locales in active use on the same SN site with materially
  different corpora.
- You want Solr/ES native monitoring + replication for the BM25 index
  (operations parity with the rest of the search stack).

Concrete signals you should **not**:

- Single-language deployment with a small corpus (< 10k chunks).
- No SE instance already deployed and you'd be standing one up just for
  RAG — the embedded path will serve you fine until corpus growth
  justifies the operational cost.

### `topK`

Tool callers control this via the `maxResults` parameter (default 5,
capped at 20). RRF fuses both source lists at the same `topK`, then
truncates the fused list back to `topK`. Bumping it improves recall on
long-tail queries at the cost of prompt-window pressure.

---

## Troubleshooting

### Status table is empty

- Confirm **Global Settings → RAG** has a default embedding store set.
  The cores API returns an empty list when no store is configured.
- Confirm the SN site has at least one **locale** (Languages tab).

### `PROVISIONED` but `Docs = 0`

The core exists but hasn't been indexed yet. Run a full **RAG reindex**
on the SN site GenAI page. The indexer enumerates locales and pushes
each chunk to the matching core.

If the reindex completes and `Docs` still reads 0 for one locale, check
the chunk metadata — only chunks with `objectName` (asset id) in their
metadata get pushed to BM25 cores.

### `ERROR` status

Hover the status badge for the last-error message. Common causes:

| Message contains              | Cause                                         | Fix                                       |
| ----------------------------- | --------------------------------------------- | ----------------------------------------- |
| `Connection refused`          | SE instance not reachable                     | Check the endpoint URL + network          |
| `Index already exists`        | Provisioner re-ran after partial success      | Re-click **Provision missing** (idempotent) |
| `Unknown analyzer`            | SE missing a language-specific analyzer       | Add the analyzer to the SE config         |
| `403 Forbidden`               | SE auth missing                               | Update SE-instance credentials            |

After fixing the underlying issue, **Provision missing** is idempotent
— it skips already-PROVISIONED cores and retries ERROR rows.

### Tool returns "No relevant documents" even after reindex

1. Confirm the bound agent has `ragEnabled = true`.
2. Confirm the SN site chat is publishing the `turing.locale` tool
   context key — check the request handler in
   `TurSNSiteChatAPI` (or its successor).
3. Watch `turing-app` logs for `[RAG]` messages — the tool service
   logs the chosen mode (`SE hybrid search` / `Embedded hybrid search`)
   plus the result count.

### Switching SE_INSTANCE → EMBEDDED leaves orphan cores

By design — the SE cores still exist and still hold chunks. Use the
**Deprovision** API (`DELETE /api/sn/{site}/rag/cores`) to clean them
up. The form doesn't expose this button by default to avoid accidental
data loss; admins can call it directly when needed.

### A locale was added to the SN site after provisioning

Click **Provision missing cores** again. Existing PROVISIONED cores
are skipped; only the new locale is created. Then run a reindex.

---

## Reference

### REST endpoints

| Method | Path                                          | Purpose                                          |
| ------ | --------------------------------------------- | ------------------------------------------------ |
| GET    | `/api/sn/{siteId}/rag/cores`                  | Status table (one row per site locale)           |
| POST   | `/api/sn/{siteId}/rag/cores/provision`        | Create missing cores; returns updated table      |
| DELETE | `/api/sn/{siteId}/rag/cores`                  | Deprovision every core for this site             |
| POST   | `/api/sn/{siteId}/genai/reindex`              | Existing — pushes chunks to vector + BM25 cores  |

All scoped under `ROLE_ADMIN` or `SN_EDIT` / `SN_VIEW`.

### Database

- **`rag_bm25_core`** (Liquibase `v2026.2.7.12`): one row per (store,
  locale). Unique constraint on `(store_instance_id, locale)`. Status
  enum: `NOT_PROVISIONED` / `PROVISIONING` / `PROVISIONED` / `ERROR` /
  `DELETING`.
- **`sn_site_genai.ragBm25Source`** + **`rag_se_instance_id`**
  (Liquibase `v2026.2.7.13`): per-binding selector + SE FK.

Core name format: `rag_<storeShortId>_<localeTag>`, e.g.
`rag_a3b2c1d4_pt-BR`. Generated by
[`TurRagBm25CoreProvisioner.coreNameFor`](../turing-app/src/main/java/com/viglet/turing/genai/rag/TurRagBm25CoreProvisioner.java)
— pure function over `(storeId, locale)`.

### Key classes

| Class                                                                                                                    | Role                                                              |
| ------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------- |
| [`TurRagSearchToolService`](../turing-app/src/main/java/com/viglet/turing/genai/tool/TurRagSearchToolService.java)       | Tool-callable entry point; resolves flags + routes to mode        |
| [`TurRagRrf`](../turing-app/src/main/java/com/viglet/turing/genai/rag/TurRagRrf.java)                                    | Pure RRF fusion (shared by both modes)                            |
| [`TurRagBm25CoreProvisioner`](../turing-app/src/main/java/com/viglet/turing/genai/rag/TurRagBm25CoreProvisioner.java)    | Idempotent core lifecycle (create/delete)                         |
| [`TurRagBm25Indexer`](../turing-app/src/main/java/com/viglet/turing/genai/rag/TurRagBm25Indexer.java)                    | Pushes vector chunks into BM25 cores                              |
| [`TurSearchEnginePlugin#retrieveStandalone`](../turing-app/src/main/java/com/viglet/turing/plugins/se/TurSearchEnginePlugin.java) | Per-engine BM25 query (Solr, ES; Lucene-embedded throws)          |
| [`TurRagBm25CoreAPI`](../turing-app/src/main/java/com/viglet/turing/api/rag/TurRagBm25CoreAPI.java)                      | Admin REST surface (list / provision / deprovision)               |
| [`TurLuceneVectorStore`](../turing-app/src/main/java/com/viglet/turing/genai/provider/store/lucene/TurLuceneVectorStore.java) | Embedded path (T24 base) — single-index hybrid                    |

### Tool context keys

- `turing.agentId` — agent invoking the tool (drives flag lookup).
- `turing.locale` — query locale (BCP 47 tag); drives core routing in
  SE_INSTANCE mode.
- `turing.conversationId`, `turing.agentPythonRequirements` — unrelated
  to RAG, listed for completeness.

### Test coverage

| Suite                                | Tests | Covers                                                       |
| ------------------------------------ | ----- | ------------------------------------------------------------ |
| `TurRagSearchToolServiceTest`        | 41    | Mode routing, locale resolution, degradation paths           |
| `TurRagRrfTest`                      | 9     | RRF math, tag stripping, top-K, edge cases                   |
| `TurRagBm25CoreProvisionerTest`      | 9     | Provision lifecycle, idempotency, error recovery             |
| `TurRagBm25IndexerTest`              | 8     | Chunk → SE doc mapping, batch indexing, delete-by-asset      |
| `TurRagBm25CoreAPITest`              | 8     | REST endpoints, 404s, partial failure swallowing             |
| `TurSNSiteAPITest.testSiteUpdatePersistsRagFlags` | 1 | Round-trip of all four RAG flags                  |

Run: `mvn test -pl turing-app -Dskip.npm=true -Dtest="TurRag*Test,TurSNSiteAPITest"`
