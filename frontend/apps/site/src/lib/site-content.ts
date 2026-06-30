import {
  Bot,
  Search,
  ServerCog,
  Sparkles,
  type LucideIcon,
} from "lucide-react";

export interface Pillar {
  icon: LucideIcon;
  iconClass: string;
  step: string;
  title: string;
  description: string;
  tags: string[];
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
  },
  {
    icon: Sparkles,
    iconClass: "from-[#059669] to-[#10b981]",
    step: "Ask it",
    title: "RAG & chat with answers you can audit",
    description:
      "Retrieval-augmented generation with a pluggable reranker (LLM, cross-encoder or Cohere) and an embedded vector store. Answers stream token-by-token with inline citations you trace back to the source passage — plus an optional groundedness check and a deterministic “not in this site” refusal.",
    tags: ["RAG", "Reranker", "Citations", "Groundedness"],
  },
  {
    icon: Bot,
    iconClass: "from-[#1a3a9e] to-[#4169E1]",
    step: "Automate it",
    title: "Agents with tools, skills & MCP",
    description:
      "Configurable AI agents that call your tools, run Anthropic-standard skills in a Docker sandbox and federate across MCP servers — plus each provider's own server-side tools (web search, code execution, computer use, image generation), opt-in per agent.",
    tags: ["Tool calling", "Skills", "MCP", "Native tools"],
  },
  {
    icon: ServerCog,
    iconClass: "from-[#4f46e5] to-[#6366f1]",
    step: "Run it",
    title: "Self-hosted, any LLM, your data",
    description:
      "Open source under Apache 2.0. Bring any LLM (OpenAI, Anthropic, Gemini, Ollama, Azure), your own search engine and storage. Multi-tenant, observable, your infrastructure.",
    tags: ["Apache 2.0", "Self-host", "Multi-tenant", "Any LLM"],
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

export interface Stat {
  value: string;
  label: string;
}

export const STATS: Stat[] = [
  { value: "6+", label: "LLM providers" },
  { value: "3", label: "Search engines" },
  { value: "4", label: "Official SDKs" },
  { value: "Apache 2.0", label: "Open source" },
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
