# Vectorless (Structured-Data) RAG

> **Talk to a structured catalog — grounded, cited, no vector database.**
> Turing answers questions over your product catalog, price list, spec sheet, or
> model registry using only a language model and your existing search index. No
> embeddings, no vector store, no chunking pipeline to stand up.

*Block BF · §LIV · since 2026.3.4*

---

## What it is

Most "RAG" (Retrieval-Augmented Generation) needs a vector database: your content
is chunked, each chunk is turned into an embedding vector, stored in a vector
store, and queries are matched by cosine similarity. That is the right tool for
**unstructured prose** (articles, manuals, tickets).

But when your knowledge is **structured** — rows with named fields (price,
context window, category, in-stock, rating) — you do not need any of that. Turing
parses the user's question into a search over your **declared field schema**,
runs it against the search engine (Solr / Elasticsearch / Lucene) as ordinary
field / facet / range queries, and the language model answers **strictly from the
matched rows**, citing each claim as `[1]`, `[2]`, …

This is **Vectorless (Structured-Data) RAG**. It needs only a **default LLM**.

```
question ──▶ NL→filter over your declared schema ──▶ structured search (no embeddings)
                                                        │
              cited answer  ◀── LLM (grounded, [n]) ◀──┘
```

## When to use which mode

Set the mode per SN site on **Semantic Navigation → (site) → GenAI → Knowledge
Base Mode**:

| Mode | Best for | Requires |
| ---- | -------- | -------- |
| **Vector** (default) | Prose / unstructured content — articles, docs, manuals, support tickets | Embedding model **+** vector store on the bound agent |
| **Vectorless (Structured)** | A structured catalog — products, prices, specs, a model registry | **Only a default LLM** |
| **Hybrid** | A site that has both prose *and* structured attributes | The full vector setup (superset of Vector) |

The mode you pick only changes **discoverability and readiness** — the vectorless
copilot endpoint has always existed. In **Vectorless** mode the AI-Mode readiness
check reports the site as ready when a default LLM is configured, instead of
asking you to wire up an embedding model and a vector store you do not need.

## How to turn it on

1. Index your structured records into an SN site via `POST /api/sn/import` (each
   record's attributes become searchable fields). See
   [T791 — the scheduled JSON-feed ingester](../ROADMAP.md) to *pull* a remote
   feed on a schedule instead of pushing.
2. **Declare the field schema** — mark the fields you want the assistant to reason
   over (`price`, `contextWindow`, `kind`, …) as enabled `TurSNSiteFieldExt` rows,
   with facetable/type set correctly. The NL→filter step may only reference
   declared fields; anything it cannot ground is dropped (it never invents a
   field).
3. Set **Knowledge Base Mode = Vectorless (Structured)** on the site's GenAI form.
4. Ensure a **default LLM** is configured in Global Settings.

## Asking questions

`POST /api/sn/{siteName}/copilot`

```json
{
  "messages": [
    { "role": "user", "content": "cheapest embedding model with at least 1M context?" }
  ],
  "locale": "en"
}
```

The response is a grounded answer plus a `citations[]` array — each citation is a
matched row (id, title, optional link, score) the answer is allowed to reference.
Multi-turn is supported: pass the running conversation and the latest user turn
drives retrieval.

`GET /api/sn/{siteName}/copilot/available` reports whether a default LLM is
configured (i.e. whether the copilot can answer).

## Guarantees

- **Grounded** — the answer asserts nothing that is not in the matched rows; the
  parser never invents a field.
- **Cited** — every claim carries the `[n]` of the row it came from.
- **Objective ranking** — results are ordered by relevance/objective signals only;
  no paid placement ever enters the pipeline.
- **Fail-open** — a parse failure degrades to a free-text query, a ranking failure
  degrades to lexical order, and an LLM failure still returns the matched
  citations so the client can render results without a synthesized answer.

## Live example

The public [`openviglet/model-catalog`](https://openviglet.github.io/model-catalog/)
is dogfooded as the first vectorless knowledge base on `turing-demo.viglet.org` —
ask it *"cheapest embedding model with ≥ 1M context?"* and it answers with linked,
cited model rows. See Block BF · T794.

## Related

- Design rationale: [`IMPROVEMENTS.md` §LIV](../IMPROVEMENTS.md)
- The copilot engine: `TurCatalogCopilotService` (T392 / §XX.12)
- NL→filter grounding on a declared schema: `TurNLFacetParser` (T385)
- Structured index + DSL: the `sn/dsl/*` search path
