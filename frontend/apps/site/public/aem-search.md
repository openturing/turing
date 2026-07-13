# AEM Search with AI — Adobe Experience Manager

> AEM search, reimagined with AI. Viglet Turing ES adds cited, streaming AI
> answers plus faceted keyword + semantic search to Adobe Experience Manager
> (AEM 6.5 and AEM as a Cloud Service) — indexed via the Dumont AEM connector,
> self-hosted, open source (Apache 2.0). Deeper than an AEM-native add-on.

Page: https://turing.viglet.org/aem-search

## What is AEM search?

AEM search is how people find content managed in Adobe Experience Manager —
pages, components and DAM assets. Adobe's built-in Query Builder / Omnisearch
covers basic keyword lookups; for typo-tolerant, faceted and AI-powered search
(semantic results and cited answers) you add a dedicated search layer. Turing
provides that layer while AEM stays your content source of truth.

## How to add AI search to AEM (4 steps)

1. **Connect your AEM tier** — point the Turing AEM connector at your author or
   publish instance. It crawls pages, components and DAM assets (extracting text
   from PDFs and Office files) into a Semantic Navigation site. Works with AEM
   6.5+ and AEM as a Cloud Service; no template changes.
2. **Index into the engine you choose** — content lands in Apache Solr,
   Elasticsearch or an embedded Lucene index with vector embeddings — not locked
   to AEM's Oak repository. Fields are described as code in a versioned manifest;
   hybrid RRF fuses keyword and semantic relevance.
3. **Drop the UI anywhere** — mount cited AI search and chat into an AEM page
   component, an Adobe Edge Delivery Services (EDS) block, or your own SPA with
   the zero-dependency vanilla-JS SDK or the React SDK. Answers stream over SSE
   with inline citations back to the source page.
4. **Turn search into an agent** — configure an agent that calls your tools,
   captures form slots, runs Anthropic-standard skills in a Docker sandbox,
   federates over MCP and uses each LLM's native tools.

## Drop-in code (vanilla JS)

```html
<div id="turing-search"></div>
<script type="module">
  import {
    createTuringClient,
    createSearchController,
    createChatController,
  } from "https://esm.sh/@viglet/turing-sdk";

  // Live public demo — Adobe's WKND site, indexed from AEM.
  const client = createTuringClient({
    baseURL: "https://turing-demo.viglet.org/api",
  });
  const search = createSearchController(client, {
    site: "wknd-publish",
    locale: "en_US",
  });
  const chat = createChatController(client, { site: "wknd-publish" });

  search.subscribe((s) => render("#turing-search", s));
  search.query("surf camps in Bali");
</script>
```

## How Turing compares (AEM search options)

| Capability | Viglet Turing ES | AEM-native AI add-on | Adobe native search |
| --- | --- | --- | --- |
| AI-generated answers with citations | Yes | Yes | No |
| Vector / semantic search | Yes | Yes | No |
| Real-time SSE streaming | Yes | Yes | No |
| Faceted, typo-tolerant search | Yes | Partial | Partial |
| Indexes AEM 6.5 + AEM as a Cloud Service | Connector | Native | Yes |
| PDF & DAM asset text extraction | Yes | Yes | Partial |
| Searches beyond AEM (CMS, DBs, files) | Yes | No | No |
| AI agents, tools & skills | Yes | No | No |
| MCP server & client | Yes | No | No |
| Native provider tools (web search, code, computer use) | Yes | No | No |
| LLM providers | OpenAI · Anthropic · Gemini · Azure · Ollama | OpenAI · Anthropic | — |
| Bring your own search engine | Solr · ES · Lucene | No (Oak) | No (Oak) |
| Open source (Apache 2.0) | Yes | No | No |
| Where content & queries live | Your infra | Adobe cloud | Adobe cloud |
| Time to first result | Minutes | 2–4 week engagement | Bundled |

## Why it goes deeper than an AEM-only add-on

- **One index across every repository** — federate AEM pages and DAM assets with
  WordPress, databases, file shares and any REST source into a single index.
- **Agents, not just answers** — tools, form-slot capture, sandboxed skills and
  human handoff turn the search box into an AI concierge.
- **Any LLM, any engine** — OpenAI, Anthropic, Gemini, Azure OpenAI, local Ollama
  on Solr, Elasticsearch or Lucene; switch per agent.
- **Your content stays in your infrastructure** — self-hosted under Apache 2.0;
  pages, embeddings and query logs never leave your network.
- **MCP + native provider tools** — expose AEM search as an MCP server; use each
  provider's server-side web search, code execution and computer use.
- **Answers you can audit** — pluggable reranker, relevance gate, groundedness
  check and a deterministic "not in this site" refusal.

## Live demo

A live demo runs on Adobe's WKND reference site, indexed from AEM into the
`wknd-publish` site on https://turing-demo.viglet.org — result and citation links
resolve on the public WKND site (wknd.site).

## Links

- AEM Search page: https://turing.viglet.org/aem-search
- AEM Connector docs (Dumont): https://docs.viglet.org/dumont/connectors/aem
- Documentation: https://docs.viglet.org/turing/
