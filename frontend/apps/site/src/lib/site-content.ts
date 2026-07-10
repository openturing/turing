import {
  Bot,
  Search,
  ServerCog,
  Sparkles,
  type LucideIcon,
} from "lucide-react";
import type { TuringPersonaOption } from "@viglet/turing-react-ui";

/**
 * T638 / §XXVII.4 — the personas the playground offers ("same question,
 * different eyes" + "validate content as persona X"). Ids match the stable ids
 * the demo backend seeds onto the default agent (T637,
 * `TurLLMInstanceOnStartup.seedDemoPersonas`); there is no public persona-list
 * endpoint, so the site pins them here. Keep the ids in sync with that seed.
 */
export const PLAYGROUND_PERSONAS: TuringPersonaOption[] = [
  {
    id: "persona-skeptical-developer",
    name: "Skeptical Developer",
    description: "Senior engineer — wants precise, verifiable answers and distrusts hype.",
  },
  {
    id: "persona-enterprise-buyer",
    name: "Enterprise Buyer",
    description: "Decision-maker scanning for business value, security and compliance.",
  },
  {
    id: "persona-first-time-user",
    name: "First-time User",
    description: "New to the product — needs plain, jargon-free explanations.",
  },
];

export interface Pillar {
  icon: LucideIcon;
  iconClass: string;
  step: string;
  title: string;
  description: string;
  tags: string[];
  /** Detail-page slug under /features/. */
  slug: string;
}

/** The platform in four outcomes — Search it → Ask it → Automate it → Run it. */
export const PILLARS: Pillar[] = [
  {
    icon: Search,
    iconClass: "from-[#d97706] to-[#f59e0b]",
    step: "Search it",
    title: "Production search over your content",
    description:
      "Faceted, multilingual, typo-tolerant search on Solr, Elasticsearch or Lucene — with field manifests as code, currency/number localization and coverage observability.",
    tags: ["Facets", "Hybrid RRF", "Autocomplete", "i18n"],
    slug: "search",
  },
  {
    icon: Sparkles,
    iconClass: "from-[#059669] to-[#10b981]",
    step: "Ask it",
    title: "RAG & chat with answers you can audit",
    description:
      "Retrieval-augmented generation with a pluggable reranker (LLM, cross-encoder or Cohere) and an embedded vector store. Answers stream token-by-token with inline citations you trace back to the source passage — plus an optional groundedness check and a deterministic “not in this site” refusal.",
    tags: ["RAG", "Reranker", "Citations", "Groundedness"],
    slug: "ask",
  },
  {
    icon: Bot,
    iconClass: "from-[#1a3a9e] to-[#4169E1]",
    step: "Automate it",
    title: "Agents with tools, skills & MCP",
    description:
      "Configurable AI agents that call your tools, run Anthropic-standard skills in a Docker sandbox and federate across MCP servers — plus each provider's own server-side tools (web search, code execution, computer use, image generation), opt-in per agent.",
    tags: ["Tool calling", "Skills", "MCP", "Native tools"],
    slug: "automate",
  },
  {
    icon: ServerCog,
    iconClass: "from-[#4f46e5] to-[#6366f1]",
    step: "Run it",
    title: "Self-hosted, any LLM, your data",
    description:
      "Open source under Apache 2.0. Bring any LLM (OpenAI, Anthropic, Gemini, Ollama, Azure), your own search engine and storage. Multi-tenant, observable, your infrastructure.",
    tags: ["Apache 2.0", "Self-host", "Multi-tenant", "Any LLM"],
    slug: "run",
  },
];

export interface NativeTool {
  name: string;
  description: string;
  /** Provider names that expose this tool server-side. */
  providers: string[];
}

/**
 * Server-side built-in tools each provider runs in its own infrastructure —
 * Turing wires them into the agent loop with per-agent opt-in (no glue code,
 * no extra crawler). Verify provider availability — as of 2026.3.
 */
export const NATIVE_TOOLS: NativeTool[] = [
  {
    name: "Web search & grounding",
    description:
      "The model searches the live web (and Google grounding on Gemini) and grounds its answer — citations flow into the same source-chip UI as your own RAG.",
    providers: ["OpenAI", "Anthropic", "Gemini"],
  },
  {
    name: "Code execution",
    description:
      "A managed Python sandbox runs alongside your own code interpreter — charts, data wrangling and computed answers, rendered inline.",
    providers: ["OpenAI", "Anthropic", "Gemini"],
  },
  {
    name: "Computer use",
    description:
      "A multi-turn screenshot/action loop behind a pluggable driver seam — browse and operate a UI to complete a task, the same contract across all three vendors.",
    providers: ["OpenAI", "Anthropic", "Gemini"],
  },
  {
    name: "URL / web fetch",
    description:
      "The model pulls a URL or PDF named in the prompt directly in the provider's infrastructure — no Turing crawler in the path.",
    providers: ["Anthropic", "Gemini"],
  },
  {
    name: "Image generation",
    description:
      "Generate images inline in the answer as self-contained data URIs — no separate artifact endpoint to host.",
    providers: ["OpenAI", "Gemini"],
  },
  {
    name: "File search",
    description:
      "Retrieval over a provider-hosted vector store, available as a built-in tool right next to Turing's own search.",
    providers: ["OpenAI"],
  },
];

export interface ComparisonRow {
  feature: string;
  /** "yes" | "partial" | "no" | freeform short text */
  turing: string;
  saas: string;
  diy: string;
}

/** Honest, high-level comparison. Verify current vendor capabilities — as of 2026. */
export const COMPARISON_COLUMNS = {
  turing: "Viglet Turing ES",
  saas: "Hosted search SaaS",
  diy: "DIY (LangChain + vector DB)",
};

export const COMPARISON: ComparisonRow[] = [
  { feature: "Open source (Apache 2.0)", turing: "yes", saas: "no", diy: "partial" },
  { feature: "Self-host — data stays in your infra", turing: "yes", saas: "no", diy: "yes" },
  { feature: "Faceted enterprise search", turing: "yes", saas: "yes", diy: "build it" },
  { feature: "RAG + pluggable reranker", turing: "yes", saas: "partial", diy: "build it" },
  { feature: "Cited answers you can audit", turing: "yes", saas: "partial", diy: "build it" },
  { feature: "AI agents, tools & skills built in", turing: "yes", saas: "partial", diy: "build it" },
  { feature: "Native provider tools (web search, code, computer use)", turing: "yes", saas: "no", diy: "build it" },
  { feature: "Any LLM provider", turing: "yes", saas: "partial", diy: "yes" },
  { feature: "Bring your own search engine", turing: "yes", saas: "no", diy: "partial" },
  { feature: "Time to first result", turing: "minutes", saas: "minutes", diy: "weeks" },
];

export interface SearchDoc {
  title: string;
  type: string;
  snippet: string;
}

/**
 * Sample corpus for the in-page interactive demo (client-side filtering).
 * Goes truly live once pointed at a public Turing instance (see ROADMAP T477/T478).
 */
export const SEARCH_DOCS: SearchDoc[] = [
  { title: "Reset your account password", type: "Help", snippet: "Step-by-step guide to recover access via the self-service portal and SSO." },
  { title: "Enterprise plan — SSO & audit logs", type: "Pricing", snippet: "SAML/OIDC single sign-on, SCIM provisioning and exportable audit trails." },
  { title: "REST API: search endpoint", type: "Docs", snippet: "POST /api/sn/{site}/search with facets, pagination and locale support." },
  { title: "Connecting Adobe AEM content", type: "Guide", snippet: "Crawl and index AEM pages and assets into a semantic navigation site." },
  { title: "Data residency & compliance", type: "Security", snippet: "Self-hosted deployment keeps all content and embeddings inside your network." },
  { title: "Build a chat agent over your docs", type: "Tutorial", snippet: "Wire useTuringChat to an agent with search tools and stream cited answers." },
  { title: "Hybrid ranking (RRF) explained", type: "Docs", snippet: "Combine BM25 and vector scores with reciprocal rank fusion for relevance." },
  { title: "Importing a product catalog", type: "Guide", snippet: "Provision fields from a manifest and bulk-index a structured catalog." },
  { title: "Webhooks for human handoff", type: "Docs", snippet: "Forward a conversation to a human channel when a slot is captured." },
  { title: "Self-host with Docker Compose", type: "Ops", snippet: "Run the full Turing stack locally with turing dev in one command." },
];

export interface Step {
  title: string;
  description: string;
}

export const STEPS: Step[] = [
  {
    title: "Ingest & index",
    description:
      "Connectors crawl AEM, WordPress, databases and files into Solr, Elasticsearch or Lucene with vector embeddings.",
  },
  {
    title: "Retrieve",
    description:
      "Hybrid RRF retrieval + reranking pull the most relevant passages for the user's intent across locales.",
  },
  {
    title: "Reason & act",
    description:
      "The agent calls tools, runs skills in a sandbox, queries MCP servers, and orchestrates a chat flow.",
  },
  {
    title: "Respond",
    description:
      "A grounded answer streams back with citations, rich content and structured slots your UI can render.",
  },
];

export interface Provider {
  name: string;
  color: string;
}

export const PROVIDERS: Provider[] = [
  { name: "OpenAI", color: "#10a37f" },
  { name: "Anthropic Claude", color: "#d9774f" },
  { name: "Google Gemini", color: "#4285f4" },
  { name: "Azure OpenAI", color: "#0ea5e9" },
  { name: "Ollama", color: "#64748b" },
  { name: "Apache Solr", color: "#d9411e" },
  { name: "Elasticsearch", color: "#f59e0b" },
  { name: "Lucene + Vectors", color: "#8b5cf6" },
  { name: "MCP Server", color: "#4169E1" },
];

export interface LinkCard {
  title: string;
  description: string;
  href: string;
  tag: string;
}

export const MARKETPLACE: LinkCard[] = [
  {
    title: "Mythical Creatures",
    description:
      "A fantasy bestiary search site with dragons, griffins and phoenixes. Faceted search plus a React SPA template.",
    href: "/marketplace/mythical-creatures/",
    tag: "Faceted · React",
  },
  {
    title: "Space Missions",
    description:
      "Explore historic and fictional space missions with semantic search — mission data, crews and launch details.",
    href: "/marketplace/space-missions/",
    tag: "Semantic · Data",
  },
  {
    title: "Vinyl Records",
    description:
      "A music catalog for vinyl records — browse albums by artist, genre, year and label with a retro template.",
    href: "/marketplace/vinyl-records/",
    tag: "Catalog · Retro",
  },
];

export const RESOURCES: LinkCard[] = [
  {
    title: "React SDK Storybook",
    description:
      "Interactive component docs — headless hooks and UI components with live examples for enterprise search.",
    href: "/react-sdk/",
    tag: "Storybook",
  },
  {
    title: "@viglet/turing-react-sdk",
    description:
      "React 19 hooks & UI components. Headless architecture with full TypeScript support.",
    href: "https://www.npmjs.com/package/@viglet/turing-react-sdk",
    tag: "npm",
  },
  {
    title: "@viglet/turing-cli",
    description:
      "Zero-dependency developer CLI: scaffold a project, run a local stack, deploy agents/flows/tools/skills, and run YAML eval suites.",
    href: "https://www.npmjs.com/package/@viglet/turing-cli",
    tag: "CLI",
  },
  {
    title: "Documentation",
    description:
      "Architecture guides, configuration reference, connector setup, API docs and deployment instructions.",
    href: "https://docs.viglet.org/turing/",
    tag: "Docs",
  },
  {
    title: "Source Code",
    description:
      "Apache 2.0. Spring Boot 4 backend, React 19 frontend and pluggable AI providers.",
    href: "https://github.com/openviglet/turing-ce",
    tag: "GitHub",
  },
  {
    title: "llms.txt",
    description:
      "Machine-readable summary of the platform for LLMs and AI coding agents.",
    href: "/llms.txt",
    tag: "AI-ready",
  },
  {
    title: "Marketplace API",
    description:
      "JSON catalog of example sites and templates you can import and adapt.",
    href: "/marketplace/marketplace.json",
    tag: "JSON",
  },
];

/* -------------------------------------------------------------------------- */
/* Feature detail pages (/features/{slug})                                    */
/* -------------------------------------------------------------------------- */

export interface FeatureSection {
  title: string;
  body: string;
}

export interface FeatureCode {
  caption: string;
  lang: string;
  code: string;
}

export interface FeatureLink {
  label: string;
  href: string;
}

export interface FeatureDetail {
  slug: string;
  /** Reuses the pillar's step label as the eyebrow ("Search it", …). */
  eyebrow: string;
  /** Page H1. */
  title: string;
  /** Hero paragraph. */
  lede: string;
  sections: FeatureSection[];
  code?: FeatureCode;
  docs: FeatureLink;
  demo?: FeatureLink;
  /** When true, the page renders the full native-provider-tools grid. */
  showNativeTools?: boolean;
  /** SEO. */
  metaTitle: string;
  metaDescription: string;
}

/** Keyed by slug — the single source of truth for the four deep-dive pages. */
export const FEATURE_DETAILS: Record<string, FeatureDetail> = {
  search: {
    slug: "search",
    eyebrow: "Search it",
    title: "Production enterprise search over your own content",
    lede: "Faceted, multilingual, typo-tolerant search on the engine you already run — Apache Solr, Elasticsearch or an embedded Lucene index — behind one query API. No data leaves your infrastructure.",
    sections: [
      {
        title: "One query API, three engines",
        body: "Point each search site at Solr, Elasticsearch or the built-in Lucene index and query them all through the same REST API. Swap engines per site without touching your front-end — the search-engine plugin layer abstracts the difference.",
      },
      {
        title: "Faceted, multilingual & typo-tolerant",
        body: "Facets, locale-aware analyzers (English, Portuguese, Spanish, Catalan), autocomplete, spell-check and currency/number localization ship in the box — the search depth enterprise catalogs and knowledge bases need, in any language.",
      },
      {
        title: "Schema as code",
        body: "Describe a site's fields in a versioned manifest and POST it — Turing converges the live schema to match, with a diff planner that flags breaking changes before they run. Don't have a schema yet? Derive a draft from sample documents.",
      },
      {
        title: "Hybrid ranking that fuses keyword + vector",
        body: "Opt into hybrid ranking and Turing reorders results with Reciprocal Rank Fusion over BM25 and per-site vector scores — the relevance of semantic search without giving up exact-match precision.",
      },
      {
        title: "Coverage you can see",
        body: "A field-coverage view shows what percentage of your documents actually populate each field, so you catch a half-indexed catalog before your users do.",
      },
    ],
    code: {
      caption: "Search a site with facets, pagination and locale",
      lang: "bash",
      code: String.raw`curl -X POST https://your-host/api/sn/products/search \
  -H "Content-Type: application/json" \
  -d '{
    "q": "wireless headphones",
    "rows": 20,
    "fq": ["brand:Acme", "price:[100 TO 300]"],
    "_setlocale": "en_US"
  }'`,
    },
    docs: { label: "Search & connectors docs", href: "https://docs.viglet.org/turing/category/enterprise-search" },
    demo: { label: "Try a live faceted demo", href: "/marketplace/mythical-creatures/" },
    metaTitle: "Enterprise Search — Solr, Elasticsearch & Lucene | Viglet Turing ES",
    metaDescription:
      "Faceted, multilingual, typo-tolerant enterprise search on Solr, Elasticsearch or embedded Lucene — one query API, schema as code, hybrid keyword + vector ranking, all self-hosted.",
  },

  ask: {
    slug: "ask",
    eyebrow: "Ask it",
    title: "RAG & chat with answers you can audit",
    lede: "Retrieval-augmented generation grounded in your content. Every answer streams back with inline citations you can trace to the exact source passage — with relevance gates, an optional groundedness check and a deterministic refusal when the answer isn't in your data.",
    sections: [
      {
        title: "Grounded answers with real citations",
        body: "Retrieval metadata flows end-to-end — every cited chunk carries its source, title, URL and score out to the UI as source chips, so a reader can click straight through to the passage the model used. Provider-agnostic: it's your retrieval, not a vendor's citation API.",
      },
      {
        title: "Pluggable reranker",
        body: "Sharpen the candidate set with the reranker that fits your budget — an LLM reranker, a local cross-encoder or a managed service like Cohere — all behind a fail-open interface, so a reranker outage degrades gracefully instead of breaking search.",
      },
      {
        title: "Relevance gate & honest refusal",
        body: "A configurable similarity floor drops weak matches, and when nothing clears the bar the chat short-circuits to a localized “this isn't covered here” answer — no LLM call, no hallucinated filler.",
      },
      {
        title: "Optional groundedness check",
        body: "Turn on a post-generation faithfulness audit and any answer the check can't support gets a low-confidence caveat appended — a second line of defense against confident-but-wrong responses.",
      },
      {
        title: "Embedded vector store, streamed responses",
        body: "Use the built-in Lucene KNN vector store or your engine's own vectors, fused with keyword search via RRF. Answers stream token-by-token over SSE for a responsive chat UI.",
      },
    ],
    code: {
      caption: "Stream a grounded, cited answer with the React SDK",
      lang: "tsx",
      code: `import { useTuringChat } from "@viglet/turing-react-sdk";

function Assistant() {
  const { messages, send } = useTuringChat({ agent: "support" });
  // each assistant message carries .sources[] — the cited passages
  return <ChatThread messages={messages} onSend={send} />;
}`,
    },
    docs: { label: "RAG & chat docs", href: "https://docs.viglet.org/turing/category/rag-chat" },
    metaTitle: "RAG & Cited AI Chat over Your Content | Viglet Turing ES",
    metaDescription:
      "Retrieval-augmented generation with inline citations, a pluggable reranker (LLM, cross-encoder or Cohere), relevance gating, groundedness checks and SSE streaming — self-hosted RAG you can audit.",
  },

  automate: {
    slug: "automate",
    eyebrow: "Automate it",
    title: "Agents with tools, skills, MCP & native provider tools",
    lede: "Configurable AI agents that call your tools, run Anthropic-standard skills in a Docker sandbox, federate across MCP servers — and turn on each provider's own server-side tools, opt-in per agent.",
    sections: [
      {
        title: "Your tools, your logic",
        body: "Define custom tools in Groovy with first-class helpers for HTTP calls, in-process Turing search and conversation slots — hot-reloadable, testable from the admin without a restart.",
      },
      {
        title: "Anthropic-standard skills in a sandbox",
        body: "Drop a skill folder (SKILL.md + scripts) and Turing runs it in an isolated Docker sandbox with progressive disclosure — the same skill format Claude uses, executed safely against a per-conversation workspace.",
      },
      {
        title: "MCP — both directions",
        body: "Connect agents to Model Context Protocol servers (with cross-vendor federation), and expose Turing's own search itself as an MCP server so any MCP-aware client can query your content.",
      },
      {
        title: "Built-in code interpreter & chat flows",
        body: "Run Python in a hardened sandbox (native or Docker), and orchestrate multi-step conversations with a declarative flow DSL — slots, form capture, A/B testing and human handoff included.",
      },
    ],
    showNativeTools: true,
    code: {
      caption: "Scaffold, run and deploy an agent with the CLI",
      lang: "bash",
      code: `npx @viglet/turing-cli init my-search
cd my-search
npx @viglet/turing-cli dev      # local stack
npx @viglet/turing-cli deploy   # push agents, flows, tools & skills`,
    },
    docs: { label: "Agents, tools & skills docs", href: "https://docs.viglet.org/turing/category/agents" },
    metaTitle: "AI Agents with Tools, Skills, MCP & Native Tools | Viglet Turing ES",
    metaDescription:
      "Build AI agents that call custom tools, run Anthropic-standard skills in a Docker sandbox, federate over MCP, and use OpenAI/Anthropic/Gemini native server-side tools — opt-in per agent, self-hosted.",
  },

  run: {
    slug: "run",
    eyebrow: "Run it",
    title: "Self-hosted, any LLM, your data — never locked in",
    lede: "Open source under Apache 2.0. Bring any LLM provider, your own search engine and your own storage. Multi-tenant, observable, and running entirely on your infrastructure.",
    sections: [
      {
        title: "Bring any LLM",
        body: "OpenAI, Anthropic Claude, Google Gemini (native SDK or OpenAI-compatible), Azure OpenAI and local Ollama models — all behind one pluggable provider interface. Switch providers per agent; API keys are stored encrypted.",
      },
      {
        title: "Bring your search engine & storage",
        body: "Run on Apache Solr, Elasticsearch or embedded Lucene, and back assets with MinIO, the filesystem or nothing at all. Turing adapts to the infrastructure you already operate.",
      },
      {
        title: "Multi-tenant by design",
        body: "Isolate tenants behind a single identity realm with tenant-owned infrastructure, gated behind a feature flag so a single-tenant deployment stays simple.",
      },
      {
        title: "Observable & operable",
        body: "Micrometer metrics, chat & conversion analytics, cache-hit gauges and per-tool latency percentiles give you the operational visibility a production search platform needs.",
      },
      {
        title: "A real developer experience",
        body: "A zero-dependency CLI (init / dev / deploy / eval / logs), official React and vanilla-JS SDKs, and YAML eval suites to gate agent and prompt changes in CI.",
      },
    ],
    code: {
      caption: "Pluggable storage & provider config (application.yaml)",
      lang: "yaml",
      code: `turing:
  storage:
    type: filesystem        # none | minio | filesystem
    filesystem:
      path: ./store/assets
  tenancy:
    enabled: false          # flip on for multi-tenant`,
    },
    docs: { label: "Deployment & configuration docs", href: "https://docs.viglet.org/turing/category/deploy-operate" },
    metaTitle: "Self-Hosted, Any LLM, Open Source (Apache 2.0) | Viglet Turing ES",
    metaDescription:
      "Self-host enterprise AI search on your own infrastructure: any LLM provider, any search engine, pluggable storage, multi-tenant, observable, with a CLI and SDKs. Open source under Apache 2.0.",
  },
};

/** Ordered slugs for navigation / prerender. */
export const FEATURE_SLUGS = ["search", "ask", "automate", "run"] as const;
