import {
  Bot,
  Boxes,
  Clock,
  EyeOff,
  FlaskConical,
  Globe,
  KeyRound,
  LayoutGrid,
  LineChart,
  Lock,
  MessagesSquare,
  MessageSquare,
  Mic,
  Search,
  Server,
  ServerCog,
  ShieldCheck,
  Sparkles,
  TrendingDown,
  Users,
  Workflow,
  type LucideIcon,
} from "lucide-react";
import type { TuringPersonaOption } from "@viglet/turing-react-ui";

/**
 * T638 / §XXVII.4: the personas the playground offers ("same question,
 * different eyes" + "validate content as persona X"). Ids match the stable ids
 * the demo backend seeds onto the default agent (T637,
 * `TurLLMInstanceOnStartup.seedDemoPersonas`); there is no public persona-list
 * endpoint, so the site pins them here. Keep the ids in sync with that seed.
 */
export const PLAYGROUND_PERSONAS: TuringPersonaOption[] = [
  {
    id: "persona-skeptical-developer",
    name: "Skeptical Developer",
    description: "Senior engineer. Wants precise, verifiable answers and distrusts hype.",
  },
  {
    id: "persona-enterprise-buyer",
    name: "Enterprise Buyer",
    description: "Decision-maker scanning for business value, security and compliance.",
  },
  {
    id: "persona-first-time-user",
    name: "First-time User",
    description: "New to the product. Needs plain, jargon-free explanations.",
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

/** The platform in four outcomes: Search it → Ask it → Automate it → Run it. */
export const PILLARS: Pillar[] = [
  {
    icon: Search,
    iconClass: "from-[#d97706] to-[#f59e0b]",
    step: "Search it",
    title: "Production search over your content",
    description:
      "Faceted, multilingual, typo-tolerant search on Solr, Elasticsearch or Lucene, with field manifests as code, currency/number localization and coverage observability.",
    tags: ["Facets", "Hybrid RRF", "Autocomplete", "i18n"],
    slug: "search",
  },
  {
    icon: Sparkles,
    iconClass: "from-[#059669] to-[#10b981]",
    step: "Ask it",
    title: "RAG & chat with answers you can audit",
    description:
      "Retrieval-augmented generation with a pluggable reranker (LLM, cross-encoder or Cohere) and an embedded vector store. Answers stream token-by-token with inline citations that trace back to the source passage, plus an optional groundedness check and a deterministic “not in this site” refusal.",
    tags: ["RAG", "Reranker", "Citations", "Groundedness"],
    slug: "ask",
  },
  {
    icon: Bot,
    iconClass: "from-[#1a3a9e] to-[#4169E1]",
    step: "Automate it",
    title: "Agents with tools, skills & MCP",
    description:
      "Configurable AI agents that call your tools, run Anthropic-standard skills in a Docker sandbox and federate across MCP servers. Each provider's own server-side tools (web search, code execution, computer use, image generation) are opt-in per agent.",
    tags: ["Tool calling", "Skills", "MCP", "Native tools"],
    slug: "automate",
  },
  {
    icon: ServerCog,
    iconClass: "from-[#4f46e5] to-[#6366f1]",
    step: "Run it",
    title: "Self-hosted, any LLM, your data",
    description:
      "Open source under Apache 2.0. Bring any LLM (OpenAI, Anthropic, Gemini, Ollama, Azure), your own search engine and storage. Multi-tenant and observable, on your infrastructure.",
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
 * Server-side built-in tools each provider runs in its own infrastructure.
 * Turing wires them into the agent loop with per-agent opt-in (no glue code,
 * no extra crawler). Verify provider availability (as of 2026.3).
 */
export const NATIVE_TOOLS: NativeTool[] = [
  {
    name: "Web search & grounding",
    description:
      "The model searches the live web (and Google grounding on Gemini) and grounds its answer. Citations flow into the same source-chip UI as your own RAG.",
    providers: ["OpenAI", "Anthropic", "Gemini"],
  },
  {
    name: "Code execution",
    description:
      "A managed Python sandbox runs alongside your own code interpreter for charts, data wrangling and computed answers, rendered inline.",
    providers: ["OpenAI", "Anthropic", "Gemini"],
  },
  {
    name: "Computer use",
    description:
      "A multi-turn screenshot/action loop behind a pluggable driver seam. The model browses and operates a UI to complete a task, with the same contract across all three vendors.",
    providers: ["OpenAI", "Anthropic", "Gemini"],
  },
  {
    name: "URL / web fetch",
    description:
      "The model pulls a URL or PDF named in the prompt directly in the provider's infrastructure, with no Turing crawler in the path.",
    providers: ["Anthropic", "Gemini"],
  },
  {
    name: "Image generation",
    description:
      "Images are generated inline in the answer as self-contained data URIs, with no separate artifact endpoint to host.",
    providers: ["OpenAI", "Gemini"],
  },
  {
    name: "File search",
    description:
      "Retrieval over a provider-hosted vector store, available as a built-in tool next to Turing's own search.",
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

/** Honest, high-level comparison. Verify current vendor capabilities (as of 2026). */
export const COMPARISON_COLUMNS = {
  turing: "Viglet Turing",
  saas: "Hosted search SaaS",
  diy: "DIY (LangChain + vector DB)",
};

export const COMPARISON: ComparisonRow[] = [
  { feature: "Open source (Apache 2.0)", turing: "yes", saas: "no", diy: "partial" },
  { feature: "Self-host: data stays in your infra", turing: "yes", saas: "no", diy: "yes" },
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
 * Goes live once pointed at a public Turing instance (see ROADMAP T477/T478).
 */
export const SEARCH_DOCS: SearchDoc[] = [
  { title: "Reset your account password", type: "Help", snippet: "Step-by-step guide to recover access via the self-service portal and SSO." },
  { title: "Enterprise plan: SSO & audit logs", type: "Pricing", snippet: "SAML/OIDC single sign-on, SCIM provisioning and exportable audit trails." },
  { title: "REST API: search endpoint", type: "Docs", snippet: "POST /api/sn/{site}/search with facets, pagination and locale support." },
  { title: "Connecting Adobe AEM content", type: "Guide", snippet: "Crawl and index AEM pages and assets into a semantic navigation site." },
  { title: "Data residency & compliance", type: "Security", snippet: "Self-hosted deployment keeps all content and embeddings inside your network." },
  { title: "Build a chat agent over your docs", type: "Tutorial", snippet: "Wire useTuringChat to an agent with search tools and stream cited answers." },
  { title: "Hybrid ranking (RRF) explained", type: "Docs", snippet: "Combine BM25 and vector scores with reciprocal rank fusion for relevance." },
  { title: "Importing a product catalog", type: "Guide", snippet: "Provision fields from a manifest and bulk-index a structured catalog." },
  { title: "Webhooks for human handoff", type: "Docs", snippet: "Forward a conversation to a human channel when a slot is captured." },
  { title: "Self-host with Docker Compose", type: "Ops", snippet: "Run the full Turing stack locally with turing dev in one command." },
];

/**
 * The SN site the AEM page's live demo pins on the public demo instance. Must
 * match the seeded site name in `viglet/cloud → docker/demo-seed-src/wknd/`
 * (`wknd-publish_content.json`). Until that seed is deployed the demo falls
 * back to WKND_SEARCH_DOCS below.
 */
export const WKND_DEMO_SITE = "wknd-publish";

/**
 * Offline fallback corpus for the AEM page's WKND demo: a slice of the Adobe
 * WKND reference site (indexed live via the Dumont AEM connector). Mirrors the
 * kind of content the live `wknd-publish` site returns; URLs resolve on the
 * public WKND publish site, wknd.site.
 */
export const WKND_SEARCH_DOCS: SearchDoc[] = [
  { title: "Bali Surf Camp", type: "Adventure", snippet: "Home to the best right-handers in Bali: big-wave surfing at Nusa Dua for advanced surfers." },
  { title: "Beervana in Portland", type: "Adventure", snippet: "A craft-beer tour through Oregon's Willamette Valley, from taprooms to hop farms." },
  { title: "Yosemite Backpacking", type: "Adventure", snippet: "A guided multi-day trek across granite domes, alpine meadows and giant sequoias." },
  { title: "Ski Touring Mont Blanc", type: "Adventure", snippet: "Backcountry ski touring across the Chamonix valley beneath Western Europe's highest peak." },
  { title: "Napa Wine Tasting", type: "Adventure", snippet: "A weekend of cellar-door tastings and vineyard tours through California wine country." },
  { title: "Arctic Surfing", type: "Magazine", snippet: "Chasing cold-water swells inside the Arctic Circle: wetsuits, aurora and empty line-ups." },
  { title: "Western Australia Road Trip", type: "Magazine", snippet: "Coast-to-outback across WA: quokkas, karri forests and the Coral Coast." },
  { title: "Guide to LA Skateparks", type: "Magazine", snippet: "The bowls, ledges and DIY spots that shaped Southern California skate culture." },
  { title: "About WKND", type: "Page", snippet: "A collective of outdoors, music, crafts, adventure-sports and travel enthusiasts sharing experiences." },
  { title: "WKND Adventures & Travel", type: "Page", snippet: "Browse curated surf, ski, cycling and camping adventures around the world." },
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
      "Hybrid RRF retrieval and reranking pull the passages most relevant to the user's question, across locales.",
  },
  {
    title: "Reason & act",
    description:
      "The agent calls tools, runs skills in a sandbox, queries MCP servers and runs a chat flow.",
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
      "Historic and fictional space missions with semantic search over mission data, crews and launch details.",
    href: "/marketplace/space-missions/",
    tag: "Semantic · Data",
  },
  {
    title: "Vinyl Records",
    description:
      "A vinyl records catalog: browse albums by artist, genre, year and label with a retro template.",
    href: "/marketplace/vinyl-records/",
    tag: "Catalog · Retro",
  },
];

export const RESOURCES: LinkCard[] = [
  {
    title: "React SDK Storybook",
    description:
      "Interactive component docs: headless hooks and UI components with live examples for enterprise search.",
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
    title: "@viglet/turing-sdk",
    description:
      "Vanilla JS, zero-dependency SDK: a framework-agnostic client and observable controllers for search, chat and autocomplete. Works in Adobe EDS blocks, a plain <script>, or any bundler.",
    href: "https://www.npmjs.com/package/@viglet/turing-sdk",
    tag: "Vanilla JS",
  },
  {
    title: "@viglet/turing-cli",
    description:
      "Zero-dependency developer CLI: scaffold a project, run a local stack, deploy agents/flows/tools/skills, and run YAML eval suites.",
    href: "https://www.npmjs.com/package/@viglet/turing-cli",
    tag: "CLI",
  },
  {
    title: "@viglet/turing-flow-dsl",
    description:
      "TypeScript DSL for chat flows. Write flows as typed source and transpile them to the editor's JSON wire format; enum typos (tone, guardrail, trigger) fail at compile time.",
    href: "https://www.npmjs.com/package/@viglet/turing-flow-dsl",
    tag: "DSL",
  },
  {
    title: "Documentation",
    description:
      "Architecture guides, configuration reference, connector setup, API docs and deployment instructions.",
    href: "https://docs.viglet.org/turing/",
    tag: "Docs",
  },
];

/* -------------------------------------------------------------------------- */
/* Outcomes (sell results, not features): the buyer-facing benefit layer       */
/* -------------------------------------------------------------------------- */

export interface Outcome {
  icon: LucideIcon;
  iconClass: string;
  /** The outcome (a business result), not the feature. */
  title: string;
  /** One line on how the platform delivers it. */
  body: string;
}

export const OUTCOMES: Outcome[] = [
  {
    icon: MessageSquare,
    iconClass: "from-[#059669] to-[#10b981]",
    title: "Users get answers, not a list of links",
    body: "Grounded, cited answers over your own content cut the time from question to answer. Every claim traces back to the exact source passage.",
  },
  {
    icon: TrendingDown,
    iconClass: "from-[#2563eb] to-[#4f46e5]",
    title: "Fewer repetitive support tickets",
    body: "A self-service assistant over your docs and knowledge base answers the questions your team answers again and again.",
  },
  {
    icon: Clock,
    iconClass: "from-[#d97706] to-[#f59e0b]",
    title: "Ship in days, not quarters",
    body: "Connectors, hybrid search, reranking, agents and SDKs come wired and tested. You configure a product instead of building a RAG stack from scratch.",
  },
  {
    icon: ShieldCheck,
    iconClass: "from-[#4f46e5] to-[#6366f1]",
    title: "Answers you can defend",
    body: "Inline citations, a relevance gate and an optional groundedness check keep a governed brand from shipping confident, wrong answers.",
  },
];

/* -------------------------------------------------------------------------- */
/* Audience split: one path for developers, one for enterprise buyers         */
/* -------------------------------------------------------------------------- */

export interface Audience {
  eyebrow: string;
  title: string;
  body: string;
  points: string[];
  primary: { label: string; href: string };
  secondary: { label: string; href: string };
}

export const AUDIENCES: Audience[] = [
  {
    eyebrow: "For developers",
    title: "Own your search stack, from npx to prod",
    body: "Open source and self-hosted, with SDKs that drop into your app.",
    points: [
      "Local stack in one command: npx @viglet/turing-cli init",
      "React SDK + zero-dependency vanilla-JS SDK (headless)",
      "Bring your own LLM and search engine",
      "Vendor-neutral funnel analytics to your own GA4, GTM, Segment or Matomo, zero-config",
      "Self-host with one docker run. No account, no telemetry",
    ],
    primary: { label: "Get started", href: "https://docs.viglet.org/turing/" },
    secondary: { label: "Try it live", href: "/#playground" },
  },
  {
    eyebrow: "For enterprises",
    title: "Production AI search on your own infrastructure",
    body: "For regulated, content-heavy organizations that need control over their data.",
    points: [
      "Your content and users' queries never leave your network",
      "Connect Adobe AEM, WordPress, databases and files",
      "SSO, encrypted secrets, any LLM (including fully local)",
      "Cited, auditable answers you can trace to the source",
    ],
    primary: { label: "AEM AI Search", href: "/aem-search/" },
    secondary: { label: "Security & data residency", href: "/security/" },
  },
];

/* -------------------------------------------------------------------------- */
/* Security & data residency page (/security)                                 */
/* -------------------------------------------------------------------------- */

export interface SecurityPoint {
  icon: LucideIcon;
  iconClass: string;
  title: string;
  body: string;
}

export const SECURITY_POINTS: SecurityPoint[] = [
  {
    icon: Server,
    iconClass: "from-[#1a3a9e] to-[#4169E1]",
    title: "Your data never leaves your network",
    body: "Turing is self-hosted. Indexed content, embeddings and query logs live on your own infrastructure. No SaaS tier collects them and no telemetry phones home.",
  },
  {
    icon: Globe,
    iconClass: "from-[#059669] to-[#10b981]",
    title: "Data residency by construction",
    body: "You run it, so your data stays in the region and account you already control. There is no data-processing addendum to negotiate and no cross-border transfer to reason about.",
  },
  {
    icon: KeyRound,
    iconClass: "from-[#4f46e5] to-[#6366f1]",
    title: "SSO & encrypted secrets",
    body: "Put the admin console behind your identity provider over OIDC single sign-on (Keycloak-based). Provider API keys and credentials are stored encrypted at rest.",
  },
  {
    icon: EyeOff,
    iconClass: "from-[#d97706] to-[#f59e0b]",
    title: "Keep even the LLM in-house",
    body: "Run fully local models (Ollama) or an OpenAI-compatible endpoint inside your network, so prompts and content never reach a third-party model provider.",
  },
  {
    icon: ShieldCheck,
    iconClass: "from-[#0ea5e9] to-[#4169E1]",
    title: "Auditable, open source",
    body: "Every line is Apache 2.0. Your security team can read what it does, pin a version and build it themselves.",
  },
  {
    icon: Lock,
    iconClass: "from-[#8b5cf6] to-[#6366f1]",
    title: "Small attack surface",
    body: "CSRF protection, a read-only public search/chat surface with every write and admin endpoint behind authentication, and sandboxed (Docker) code execution and skills.",
  },
];

export interface SecurityFaqItem {
  q: string;
  a: string;
}

/**
 * Data-residency questions, phrased the way a buyer (or an assistant on their
 * behalf) asks them. Emitted as FAQPage JSON-LD too. Claims only what the
 * software does.
 */
export const SECURITY_FAQ: SecurityFaqItem[] = [
  {
    q: "Is there an AI agent platform that preserves data residency?",
    a: "Yes. Viglet Turing is self-hosted, so its search index, embeddings, agent conversations and query logs live in the infrastructure and region you deploy it to. Agents, tools, skills and MCP servers run inside that deployment, and an agent only reaches an outside service you configure it to call.",
  },
  {
    q: "Which LLM should a regulated industry use when data residency is required?",
    a: "Turing is not an inference provider; it lets you pick one per agent. Keep prompts in-house with a local model through Ollama or any OpenAI-compatible endpoint you host, or use a cloud provider you can pin to a region, such as AWS Bedrock or Google Vertex AI. Switching providers does not change where your content and index live.",
  },
  {
    q: "Can I run RAG and AI search without sending content to a vendor's cloud?",
    a: "Yes. Connectors index your content into Solr, Elasticsearch or an embedded Lucene index on your own servers, and retrieval, ranking and citations happen there. With a local model, nothing in the answer path leaves your network.",
  },
  {
    q: "Does Turing send telemetry or usage data back to Viglet?",
    a: "No. There is no telemetry and no phone-home. The code is Apache 2.0, so your security team can read it, pin a version and build it themselves.",
  },
  {
    q: "Does Turing have SOC 2 or GDPR certification?",
    a: "Turing is software you run, not a service we operate, so there is no hosted environment to certify. Deployed in your environment, it runs under the certifications, network controls and data-processing terms your infrastructure already carries.",
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

/** Keyed by slug: the single source of truth for the four deep-dive pages. */
export const FEATURE_DETAILS: Record<string, FeatureDetail> = {
  search: {
    slug: "search",
    eyebrow: "Search it",
    title: "Production enterprise search over your own content",
    lede: "Faceted, multilingual, typo-tolerant search on the engine you already run (Apache Solr, Elasticsearch or an embedded Lucene index) behind one query API. No data leaves your infrastructure.",
    sections: [
      {
        title: "One query API, three engines",
        body: "Point each search site at Solr, Elasticsearch or the built-in Lucene index and query them all through the same REST API. Swap engines per site without touching your front-end; the search-engine plugin layer abstracts the difference.",
      },
      {
        title: "Faceted, multilingual & typo-tolerant",
        body: "Facets, locale-aware analyzers (English, Portuguese, Spanish, Catalan), autocomplete, spell-check and currency/number localization ship in the box.",
      },
      {
        title: "Schema as code",
        body: "Describe a site's fields in a versioned manifest and POST it. Turing converges the live schema to match, with a diff planner that flags breaking changes before they run. No schema yet? Derive a draft from sample documents.",
      },
      {
        title: "Hybrid ranking that fuses keyword + vector",
        body: "Opt into hybrid ranking and Turing reorders results with Reciprocal Rank Fusion over BM25 and per-site vector scores, so semantic matches rank well and exact matches still count.",
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
    metaTitle: "Enterprise Search on Solr, Elasticsearch & Lucene | Viglet Turing",
    metaDescription:
      "Faceted, multilingual, typo-tolerant enterprise search on Solr, Elasticsearch or embedded Lucene: one query API, schema as code, hybrid keyword + vector ranking, all self-hosted.",
  },

  ask: {
    slug: "ask",
    eyebrow: "Ask it",
    title: "RAG & chat with answers you can audit",
    lede: "Retrieval-augmented generation grounded in your content. Every answer streams back with inline citations to the exact source passage. Relevance gates, an optional groundedness check and a deterministic refusal cover the case where the answer isn't in your data.",
    sections: [
      {
        title: "Grounded answers with real citations",
        body: "Retrieval metadata flows end-to-end: every cited chunk carries its source, title, URL and score out to the UI as source chips, so a reader can click through to the passage the model used. It is provider-agnostic, because the citations come from your retrieval and not from a vendor's citation API.",
      },
      {
        title: "Pluggable reranker",
        body: "Pick the reranker that fits your budget: an LLM reranker, a local cross-encoder or a managed service like Cohere. All of them sit behind a fail-open interface, so a reranker outage does not break search.",
      },
      {
        title: "Relevance gate & honest refusal",
        body: "A configurable similarity floor drops weak matches. When nothing clears the bar, the chat short-circuits to a localized “this isn't covered here” answer without calling the LLM.",
      },
      {
        title: "Optional groundedness check",
        body: "Turn on a post-generation faithfulness audit, and any answer the check can't support gets a low-confidence caveat appended.",
      },
      {
        title: "Embedded vector store, streamed responses",
        body: "Use the built-in Lucene KNN vector store or your engine's own vectors, fused with keyword search via RRF. Answers stream token-by-token over SSE.",
      },
    ],
    code: {
      caption: "Stream a grounded, cited answer with the React SDK",
      lang: "tsx",
      code: `import { useTuringChat } from "@viglet/turing-react-sdk";

function Assistant() {
  const { messages, send } = useTuringChat({ agent: "support" });
  // each assistant message carries .sources[] (the cited passages)
  return <ChatThread messages={messages} onSend={send} />;
}`,
    },
    docs: { label: "RAG & chat docs", href: "https://docs.viglet.org/turing/category/rag-chat" },
    metaTitle: "RAG & Cited AI Chat over Your Content | Viglet Turing",
    metaDescription:
      "Retrieval-augmented generation with inline citations, a pluggable reranker (LLM, cross-encoder or Cohere), relevance gating, groundedness checks and SSE streaming. Self-hosted RAG you can audit.",
  },

  automate: {
    slug: "automate",
    eyebrow: "Automate it",
    title: "Agents with tools, skills, MCP & native provider tools",
    lede: "Configurable AI agents that call your tools, run Anthropic-standard skills in a Docker sandbox and federate across MCP servers. Each provider's own server-side tools can be switched on per agent.",
    sections: [
      {
        title: "Your tools, your logic",
        body: "Define custom tools in Groovy with helpers for HTTP calls, in-process Turing search and conversation slots. Tools hot-reload and can be tested from the admin without a restart.",
      },
      {
        title: "Anthropic-standard skills in a sandbox",
        body: "Drop in a skill folder (SKILL.md + scripts) and Turing runs it in an isolated Docker sandbox with progressive disclosure. It is the same skill format Claude uses, executed against a per-conversation workspace.",
      },
      {
        title: "MCP in both directions",
        body: "Connect agents to Model Context Protocol servers (with cross-vendor federation), and expose Turing's search as an MCP server so any MCP-aware client can query your content.",
      },
      {
        title: "Built-in code interpreter & chat flows",
        body: "Run Python in a hardened sandbox (native or Docker), and orchestrate multi-step conversations with a declarative flow DSL that includes slots, form capture, A/B testing and human handoff.",
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
    metaTitle: "AI Agents with Tools, Skills, MCP & Native Tools | Viglet Turing",
    metaDescription:
      "Build AI agents that call custom tools, run Anthropic-standard skills in a Docker sandbox, federate over MCP, and use OpenAI/Anthropic/Gemini native server-side tools, opt-in per agent. Self-hosted.",
  },

  run: {
    slug: "run",
    eyebrow: "Run it",
    title: "Self-hosted, any LLM, your data",
    lede: "Open source under Apache 2.0. Bring any LLM provider, your own search engine and your own storage. Multi-tenant and observable, running on your own infrastructure.",
    sections: [
      {
        title: "Bring any LLM",
        body: "OpenAI, Anthropic Claude, Google Gemini (native SDK or OpenAI-compatible), Azure OpenAI and local Ollama models, behind one pluggable provider interface. Switch providers per agent; API keys are stored encrypted.",
      },
      {
        title: "Bring your search engine & storage",
        body: "Run on Apache Solr, Elasticsearch or embedded Lucene, and back assets with MinIO, the filesystem or nothing at all. Turing runs on the infrastructure you already operate.",
      },
      {
        title: "Multi-tenant by design",
        body: "Isolate tenants behind a single identity realm with tenant-owned infrastructure, gated behind a feature flag so a single-tenant deployment stays simple.",
      },
      {
        title: "Observable & operable",
        body: "Turing exposes Micrometer metrics, chat & conversion analytics, cache-hit gauges and per-tool latency percentiles.",
      },
      {
        title: "Measure the funnel in the analytics stack you already run",
        body: "Server metrics tell you the platform is healthy; the SDK tells you what your users do. It emits a vendor-neutral event for every funnel step: query, no-results, result click, chat start, conversion and abandonment. A GA4 / Google Tag Manager sink detects the gtag / dataLayer your page already loads (Adobe Edge Delivery Services included), so it works with no extra config, and a generic escape hatch covers Segment, Matomo or Plausible. Cross-surface session stitching ties a chat lead back to the search that started it. It is your own tag on your own page, so you decide what is collected.",
      },
      {
        title: "Developer tooling",
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
    metaTitle: "Self-Hosted, Any LLM, Open Source (Apache 2.0) | Viglet Turing",
    metaDescription:
      "Self-host enterprise AI search on your own infrastructure: any LLM provider, any search engine, pluggable storage, multi-tenant, observable, with a CLI and SDKs. Open source under Apache 2.0.",
  },
};

/** Ordered slugs for navigation / prerender. */
export const FEATURE_SLUGS = ["search", "ask", "automate", "run"] as const;

/* -------------------------------------------------------------------------- */
/* Migration landing (/migrate): "Switching from Algolia / Elasticsearch?"    */
/* §XXVII.3 / T479. Meilisearch/Supabase pattern: a concept-mapping table,     */
/* a concrete import path, and the "your data stays in your infra" angle.     */
/* -------------------------------------------------------------------------- */

export interface MigrationRow {
  /** The capability, phrased engine-neutrally. */
  concept: string;
  /** Algolia's name / mechanism for it. */
  algolia: string;
  /** Elasticsearch's name / mechanism for it. */
  elastic: string;
  /** The Viglet Turing equivalent. */
  turing: string;
}

/**
 * Honest concept mapping from the two engines teams most often switch from.
 * Verify current vendor capabilities (as of 2026). Kept defensible: where a
 * vendor's feature is paid/beta/hosted we say so rather than implying parity.
 */
export const MIGRATION_MAP: MigrationRow[] = [
  {
    concept: "Where documents live",
    algolia: "Index",
    elastic: "Index",
    turing: "Semantic Navigation (SN) site",
  },
  {
    concept: "A single record",
    algolia: "Record (JSON object)",
    elastic: "Document (_source)",
    turing: "Document, indexed via REST or a connector",
  },
  {
    concept: "Schema & field config",
    algolia: "Index settings (searchableAttributes…)",
    elastic: "Mappings",
    turing: "Field manifest as code (POST /api/sn/manifest)",
  },
  {
    concept: "Faceting",
    algolia: "attributesForFaceting",
    elastic: "Aggregations",
    turing: "Native facet fields, no extra query DSL",
  },
  {
    concept: "Typo tolerance",
    algolia: "typoTolerance",
    elastic: "Fuzzy queries",
    turing: "Typo-tolerant + spell-check in the box",
  },
  {
    concept: "Synonyms",
    algolia: "Synonyms API",
    elastic: "Synonym token filter",
    turing: "Engine synonyms (Solr / Elasticsearch)",
  },
  {
    concept: "Semantic / vector search",
    algolia: "NeuralSearch (paid add-on)",
    elastic: "kNN over dense_vector",
    turing: "Embedded Lucene KNN or your engine's vectors, fused via hybrid RRF",
  },
  {
    concept: "Relevance tuning",
    algolia: "Custom ranking + tie-breaking",
    elastic: "BM25 + function_score",
    turing: "BM25 + opt-in hybrid RRF (keyword + vector)",
  },
  {
    concept: "AI answers over your content",
    algolia: "Ask AI (hosted beta)",
    elastic: "ELSER + your own LLM glue",
    turing: "RAG, cited chat & agents built in",
  },
  {
    concept: "Front-end UI",
    algolia: "InstantSearch.js / React",
    elastic: "Build it (elasticsearch-js)",
    turing: "React SDK + zero-dep vanilla-JS SDK (headless)",
  },
  {
    concept: "Query API",
    algolia: "Hosted search API",
    elastic: "_search DSL",
    turing: "POST /api/sn/{site}/search on your host",
  },
  {
    concept: "Hosting & data residency",
    algolia: "Fully hosted SaaS",
    elastic: "Elastic Cloud or self-managed",
    turing: "Self-hosted: content & embeddings never leave your infra",
  },
];

export const MIGRATION_COLUMNS = {
  concept: "Concept",
  algolia: "Algolia",
  elastic: "Elasticsearch",
  turing: "Viglet Turing",
} as const;

export interface MigrationOutcome {
  title: string;
  body: string;
}

/**
 * The payoff: what a team walks away with after the switch. Framed as
 * outcomes, not steps; the how-to lives in the docs.
 */
export const MIGRATION_OUTCOMES: MigrationOutcome[] = [
  {
    title: "The same search, plus hybrid ranking",
    body: "Faceting, typo tolerance and synonyms carry over, with hybrid keyword + vector ranking on top.",
  },
  {
    title: "AI answers, no glue code",
    body: "RAG, cited chat and agents over your own content ship in the box, with nothing to assemble or host separately.",
  },
  {
    title: "Your front-end barely changes",
    body: "Swap InstantSearch or elasticsearch-js for the React or zero-dependency vanilla-JS SDK. The same UI, on your infra.",
  },
];

/**
 * The one moment of code the page shows: `turing migrate` points at your live
 * index and moves schema + records in a single command. Full flag reference and
 * the manual REST path live in the docs; the page only needs the "wow".
 * Provided by the @viglet/turing-cli package (T479 / E.7).
 */
export const MIGRATION_CLI_CODE = {
  caption: "Schema and records move together",
  lang: "bash",
  code: `# Elasticsearch: mappings → schema, docs imported
turing migrate elasticsearch \\
  --index products --site Products

# Algolia: --use-llm infers types for schemaless data
turing migrate algolia \\
  --index catalog --site Catalog --use-llm`,
} as const;

export interface MigrationCliFeature {
  /** Short reassurance headline. */
  title: string;
  /** One-line explanation. */
  body: string;
}

/** Trust signals for the cautious buyer: the switch is previewable and reversible. */
export const MIGRATION_CLI_FEATURES: MigrationCliFeature[] = [
  {
    title: "Preview before you commit",
    body: "--dry-run shows the schema Turing derived. Nothing is written until you're happy with it.",
  },
  {
    title: "Prove parity, then cut over",
    body: "compare replays your real queries against both engines and reports the relevance difference.",
  },
  {
    title: "Reshape fields on the way in",
    body: "--overrides-file renames, retypes or drops any field, and your source index stays unchanged.",
  },
];

/* -------------------------------------------------------------------------- */
/* Comparison / FAQ page (/compare): §XXVII.3 / T482.                         */
/* Deep, honest comparison vs Algolia, Coveo, Elasticsearch and a DIY RAG     */
/* stack                                                                      */
/* (LangChain + vector DB), plus an objection-answering FAQ. The Deep Agents  */
/* / AG-UI pattern: name the objection before the reader does, answer it      */
/* fairly, and say where each alternative genuinely wins.                     */
/* -------------------------------------------------------------------------- */

export interface CompareRow {
  /** The capability, phrased engine-neutrally. */
  capability: string;
  /** "yes" | "partial" | "no" | freeform short text (per Cell in comparison.tsx). */
  turing: string;
  algolia: string;
  coveo: string;
  elastic: string;
  /** DIY = LangChain / LlamaIndex + a vector database, assembled yourself. */
  diy: string;
}

export const COMPARE_COLUMNS = {
  turing: "Viglet Turing",
  algolia: "Algolia",
  coveo: "Coveo",
  elastic: "Elasticsearch",
  diy: "DIY (LangChain + vector DB)",
} as const;

/**
 * Deep capability matrix. Honest and defensible: where an alternative ships
 * something equivalent we say "yes"; where it's paid/beta/self-built we say so.
 * Verify current vendor capabilities (high-level, as of 2026).
 */
export const COMPARE_MATRIX: CompareRow[] = [
  { capability: "Open source (Apache 2.0)", turing: "yes", algolia: "no", coveo: "no", elastic: "partial", diy: "partial" },
  { capability: "Self-host: data stays in your infra", turing: "yes", algolia: "no", coveo: "no", elastic: "yes", diy: "yes" },
  { capability: "Faceted, typo-tolerant search", turing: "yes", algolia: "yes", coveo: "yes", elastic: "yes", diy: "build it" },
  { capability: "Multilingual analyzers in the box", turing: "yes", algolia: "yes", coveo: "yes", elastic: "yes", diy: "build it" },
  { capability: "Vector / semantic search", turing: "yes", algolia: "paid add-on", coveo: "yes", elastic: "yes", diy: "yes" },
  { capability: "Hybrid keyword + vector (RRF)", turing: "yes", algolia: "partial", coveo: "yes", elastic: "yes", diy: "build it" },
  { capability: "RAG with cited answers", turing: "yes", algolia: "hosted beta", coveo: "yes", elastic: "build it", diy: "build it" },
  { capability: "Pluggable reranker (LLM / cross-encoder / Cohere)", turing: "yes", algolia: "no", coveo: "built-in ML", elastic: "partial", diy: "build it" },
  { capability: "AI agents, tools & skills built in", turing: "yes", algolia: "no", coveo: "partial", elastic: "no", diy: "you wire it" },
  { capability: "MCP server & client", turing: "yes", algolia: "no", coveo: "partial", elastic: "no", diy: "build it" },
  { capability: "Native provider tools (web search, code, computer use)", turing: "yes", algolia: "no", coveo: "no", elastic: "no", diy: "build it" },
  { capability: "Bring your own LLM", turing: "yes", algolia: "no", coveo: "partial", elastic: "partial", diy: "yes" },
  { capability: "Bring your own search engine", turing: "yes", algolia: "no", coveo: "no", elastic: "n/a", diy: "partial" },
  { capability: "Pre-built enterprise connectors", turing: "yes", algolia: "partial", coveo: "yes", elastic: "yes", diy: "build it" },
  { capability: "Pricing model", turing: "free / self-host", algolia: "per-record + per-search", coveo: "enterprise subscription", elastic: "infra / Elastic Cloud", diy: "infra + build time" },
  { capability: "Time to first result", turing: "minutes", algolia: "minutes", coveo: "days", elastic: "days", diy: "weeks" },
];

export interface CompareVendor {
  name: string;
  /** One-line honest framing of what they are. */
  tagline: string;
  /** Where this alternative genuinely wins, stated plainly. */
  shines: string;
  /** Where Turing fits instead (the switching reason). */
  turingFit: string;
}

/**
 * Per-competitor honest cards. Lead with where the alternative wins so the
 * comparison reads as fair, then say where Turing fits.
 */
export const COMPARE_VENDORS: CompareVendor[] = [
  {
    name: "vs Algolia",
    tagline: "A fully hosted search SaaS with a great DX and instant relevance.",
    shines:
      "Algolia is hard to beat for purely hosted, low-ops keyword search with best-in-class InstantSearch widgets, if sending your index to a vendor's cloud and per-record/per-search pricing are acceptable.",
    turingFit:
      "Switch when data residency, per-record cost at scale, or wanting RAG, cited chat and agents in the same platform matter. Turing runs faceted, typo-tolerant search plus AI on your own infrastructure, under Apache 2.0.",
  },
  {
    name: "vs Coveo",
    tagline: "An enterprise AI-search SaaS built for commerce, service and workplace.",
    shines:
      "Coveo is strong where a large organisation wants managed relevance: machine-learning ranking tuned on its own traffic, generative answers with citations, and a long list of ready connectors into Salesforce, ServiceNow, SharePoint and the big commerce platforms. It is sold and supported as an enterprise subscription.",
    turingFit:
      "Switch when the content must stay in your own infrastructure, when you want to pick the LLM and the search engine yourself, or when an enterprise contract is out of proportion to the project. Turing gives you cited RAG, agents and faceted search, self-hosted under Apache 2.0.",
  },
  {
    name: "vs Elasticsearch",
    tagline: "The de facto self-hosted search engine: powerful and low-level.",
    shines:
      "Elasticsearch (and OpenSearch) is a mature engine with deep aggregation, kNN and cluster tooling. If you have the team to operate it and build the application layer, it will take you far.",
    turingFit:
      "Turing runs on Elasticsearch, one of its three supported engines, and adds the application layer you would otherwise build: schema-as-code, hybrid RRF, RAG, cited chat, agents, connectors and an SDK.",
  },
  {
    name: "vs DIY RAG (LangChain + vector DB)",
    tagline: "Assemble the stack yourself from framework primitives.",
    shines:
      "A hand-built LangChain/LlamaIndex stack gives you total control and no product opinions. It suits a bespoke research pipeline, or a team that wants to own every layer.",
    turingFit:
      "Turing is the batteries-included alternative: retrieval, reranking, citations, agents, tools, skills, MCP, streaming, analytics and admin UI are already wired and tested. You keep the pluggability (any LLM, any engine) without maintaining the plumbing.",
  },
];

export interface FaqItem {
  q: string;
  a: string;
}

/**
 * Objection-answering FAQ: name the concern before the reader does. Rendered
 * on the page and emitted as FAQPage JSON-LD for rich results.
 */
export const FAQ: FaqItem[] = [
  {
    q: "Is Viglet Turing really free and open source?",
    a: "Yes. The platform is open source under the Apache 2.0 license: the backend, the frontend and the SDKs. There is no per-record or per-search billing. You run it on your own infrastructure and pay only for that infrastructure and any LLM provider you choose.",
  },
  {
    q: "Does my content or my users' queries leave my network?",
    a: "No. Turing is self-hosted: your documents, embeddings and query logs stay in your own infrastructure. The only data that leaves is what you explicitly send to an external LLM provider, and you can run fully local models (Ollama) to keep even that in-house.",
  },
  {
    q: "Do I have to replace my search engine?",
    a: "No. Turing runs on Apache Solr, Elasticsearch or an embedded Lucene index behind one query API. If you already operate Elasticsearch or Solr, Turing sits on top of it and adds the AI and application layer.",
  },
  {
    q: "Am I locked into one LLM vendor?",
    a: "No. Any LLM provider (OpenAI, Anthropic, Google Gemini, Azure OpenAI, local Ollama and OpenAI-compatible endpoints) plugs in behind one interface, switchable per agent. API keys are stored encrypted, and reranking and embeddings are pluggable too.",
  },
  {
    q: "Isn't this just LangChain with a UI?",
    a: "No. Turing is a deployable product, not a framework you assemble. Retrieval, hybrid ranking, reranking, citations, groundedness checks, agents, tools, skills, MCP, streaming, connectors, analytics and an admin UI are already built, tested and wired together. You get the pluggability of a DIY stack without its maintenance.",
  },
  {
    q: "How long until I get a real result?",
    a: "Minutes for a first search. The CLI scaffolds a project and runs a local stack in one command, you provision fields from a manifest (or derive one from sample documents), bulk-index your records, and query through the REST API or the SDK.",
  },
  {
    q: "Can I trust the AI answers?",
    a: "Answers are grounded in your content and stream back with inline citations you can trace to the exact source passage. A configurable relevance gate drops weak matches, an optional groundedness check flags unsupported answers, and when nothing clears the bar the chat gives a deterministic “not covered here” reply instead of hallucinating.",
  },
  {
    q: "Is it production-ready and observable?",
    a: "Yes. Micrometer metrics, chat and conversion analytics, cache-hit gauges and per-tool latency percentiles are built in, multi-tenancy is available behind a feature flag, and YAML eval suites let you gate agent and prompt changes in CI.",
  },
];

/* -------------------------------------------------------------------------- */
/* AEM solution page (/aem): "AI search for Adobe Experience Manager".         */
/* Reframes the AEM-native AI-search pitch: Turing gives AEM the same cited    */
/* AI answers, then goes deeper: cross-repository search, agents, any LLM,     */
/* any engine, self-hosted data residency, open source. Honest & defensible:   */
/* where the AEM-native add-on genuinely matches, we say so.                   */
/* -------------------------------------------------------------------------- */

export interface AemMatrixRow {
  /** The capability, phrased vendor-neutrally. */
  capability: string;
  /** "yes" | "partial" | "no" | freeform short text (per Cell). */
  turing: string;
  /** A Cloud-Service-native AEM AI-search add-on (e.g. aem-ai-search.com). */
  aemAi: string;
  /** Adobe's own built-in search (Query Builder / Omnisearch). */
  adobe: string;
}

export const AEM_COLUMNS = {
  turing: "Viglet Turing",
  aemAi: "AEM-native AI add-on",
  adobe: "Adobe native search",
} as const;

/**
 * Capability matrix vs a Cloud-Service-native AEM AI-search add-on and Adobe's
 * own search. Honest: the add-on matches Turing on the core AI-answer story;
 * the depth gap opens on everything around it. Verify current vendor
 * capabilities (high-level, as of 2026).
 */
export const AEM_MATRIX: AemMatrixRow[] = [
  { capability: "AI-generated answers with citations", turing: "yes", aemAi: "yes", adobe: "no" },
  { capability: "Vector / semantic search", turing: "yes", aemAi: "yes", adobe: "no" },
  { capability: "Real-time SSE streaming", turing: "yes", aemAi: "yes", adobe: "no" },
  { capability: "Faceted, typo-tolerant search", turing: "yes", aemAi: "partial", adobe: "partial" },
  { capability: "Indexes AEM 6.5 + AEM as a Cloud Service", turing: "connector", aemAi: "native", adobe: "yes" },
  { capability: "PDF & DAM asset text extraction", turing: "yes", aemAi: "yes", adobe: "partial" },
  { capability: "Searches beyond AEM (CMS, DBs, files)", turing: "yes", aemAi: "no", adobe: "no" },
  { capability: "AI agents, tools & skills", turing: "yes", aemAi: "no", adobe: "no" },
  { capability: "MCP server & client", turing: "yes", aemAi: "no", adobe: "no" },
  { capability: "Native provider tools (web search, code, computer use)", turing: "yes", aemAi: "no", adobe: "no" },
  { capability: "LLM providers", turing: "OpenAI · Anthropic · Gemini · Azure · Ollama", aemAi: "OpenAI · Anthropic", adobe: "none" },
  { capability: "Bring your own search engine", turing: "Solr · Elasticsearch · Lucene", aemAi: "no (Oak)", adobe: "no (Oak)" },
  { capability: "Pluggable reranker (LLM / cross-encoder / Cohere)", turing: "yes", aemAi: "no", adobe: "no" },
  { capability: "Groundedness check & honest refusal", turing: "yes", aemAi: "partial", adobe: "no" },
  { capability: "Open source (Apache 2.0)", turing: "yes", aemAi: "no", adobe: "no" },
  { capability: "Where content & queries live", turing: "your infra", aemAi: "Adobe cloud", adobe: "Adobe cloud" },
  { capability: "Pricing model", turing: "free / self-host", aemAi: "license + consulting", adobe: "bundled w/ AEM" },
  { capability: "Time to first result", turing: "minutes", aemAi: "2–4 week engagement", adobe: "bundled" },
];

export interface AemStep {
  step: string;
  title: string;
  body: string;
}

/** How Turing plugs into an AEM stack: connect → index → answer → automate. */
export const AEM_STEPS: AemStep[] = [
  {
    step: "1",
    title: "Connect your AEM tier",
    body: "Point the Turing AEM connector at your author or publish instance. It crawls pages, components and DAM assets into a Semantic Navigation site, extracting text from PDFs and Office files. Works with AEM 6.5+ and AEM as a Cloud Service; no code changes to your templates.",
  },
  {
    step: "2",
    title: "Index into the engine you choose",
    body: "Content lands in Apache Solr, Elasticsearch or an embedded Lucene index with vector embeddings, outside AEM's Oak repository. Fields are described as code in a versioned manifest, and hybrid RRF fuses keyword and semantic relevance for every query.",
  },
  {
    step: "3",
    title: "Drop the UI anywhere",
    body: "Mount cited AI search and chat into an AEM page component, an Adobe Edge Delivery Services (EDS) block or your own SPA, with the zero-dependency vanilla-JS SDK or the React SDK. Answers stream token-by-token over SSE with inline citations back to the source page.",
  },
  {
    step: "4",
    title: "Turn search into an agent",
    body: "Go past answers: configure an agent that calls your tools, captures form slots, runs Anthropic-standard skills in a Docker sandbox, federates over MCP and uses each LLM's native tools.",
  },
];

export interface AemDepth {
  icon: LucideIcon;
  iconClass: string;
  title: string;
  body: string;
}

/**
 * "We have more depth": the capabilities that live beyond an AEM-native
 * add-on's ceiling. Each names what the AEM-only pitch can't do and what
 * Turing does instead.
 */
export const AEM_DEPTH: AemDepth[] = [
  {
    icon: Boxes,
    iconClass: "from-[#d97706] to-[#f59e0b]",
    title: "One index across every repository",
    body: "AEM is rarely your only source. Turing federates AEM pages and DAM assets with WordPress, databases, file shares and any REST source into a single index, so one answer can cite a policy PDF, a product record and an AEM page together. An AEM-native tool stops at the AEM boundary.",
  },
  {
    icon: Bot,
    iconClass: "from-[#1a3a9e] to-[#4169E1]",
    title: "Agents, not just answers",
    body: "Beyond RAG, configure agents that call your tools, capture form slots, run skills in a sandbox and hand off to a human. An AEM search box becomes an assistant that can book, look up or escalate, not only summarize a page.",
  },
  {
    icon: ServerCog,
    iconClass: "from-[#4f46e5] to-[#6366f1]",
    title: "Any LLM, any engine",
    body: "OpenAI, Anthropic, Gemini, Azure OpenAI and local Ollama, running on Solr, Elasticsearch or Lucene. Switch per agent, store keys encrypted, and tie nothing to a single vendor's cloud.",
  },
  {
    icon: ShieldCheck,
    iconClass: "from-[#059669] to-[#10b981]",
    title: "Your content stays in your infrastructure",
    body: "A Cloud-Service-native add-on runs, and reasons over your content, inside Adobe's cloud. Turing is self-hosted under Apache 2.0: pages, embeddings and query logs never leave your network, and you can run fully local models to keep even the LLM in-house.",
  },
  {
    icon: Workflow,
    iconClass: "from-[#0ea5e9] to-[#4169E1]",
    title: "MCP + native provider tools",
    body: "Expose AEM search as an MCP server for any AI client, federate out to other MCP servers, and switch on each provider's server-side web search, code execution and computer use. Each is opt-in per agent and wired into the same cited-answer UI.",
  },
  {
    icon: Sparkles,
    iconClass: "from-[#8b5cf6] to-[#6366f1]",
    title: "Answers a governed brand can trust",
    body: "A pluggable reranker, a configurable relevance gate, an optional groundedness check and a deterministic “not in this site” refusal keep a regulated AEM property from shipping confident hallucinations.",
  },
  {
    icon: LineChart,
    iconClass: "from-[#0891b2] to-[#06b6d4]",
    title: "Measure it in GA4 / GTM, zero config on EDS",
    body: "The search and cited-chat UI emit a vendor-neutral analytics event for every funnel step: query, result click, chat, conversion, abandonment. The GA4 / Google Tag Manager sink detects the gtag / dataLayer your AEM pages or Edge Delivery Services blocks already load, so the funnel shows up in the analytics your marketing team already uses. Segment, Matomo and Plausible are covered by an escape hatch, and nothing is sent to a vendor.",
  },
];

export const AEM_CODE = {
  caption: "Drop cited AI search into an AEM page or EDS block",
  lang: "html",
  code: `<!-- Vanilla, zero-dependency: AEM or EDS block -->
<div id="turing-search"></div>
<script type="module">
  import {
    createTuringClient,
    createSearchController,
    createChatController,
  } from "https://esm.sh/@viglet/turing-sdk";

  // Live demo: Adobe's WKND site, indexed from AEM.
  // Swap baseURL for your own instance; data stays put.
  const client = createTuringClient({
    baseURL: "https://turing-demo.viglet.org/api",
  });

  // Faceted, typo-tolerant search over WKND content…
  const search = createSearchController(client, {
    site: "wknd-publish",
    locale: "en_US",
  });
  // …and a grounded, cited chat answer streamed over SSE.
  const chat = createChatController(client, {
    site: "wknd-publish",
  });

  search.subscribe((s) => render("#turing-search", s));
  search.query("surf camps in Bali");
</script>`,
} as const;

export interface AemFaqItem {
  q: string;
  a: string;
}

/** AEM-specific objection-answering FAQ (emitted as FAQPage JSON-LD too). */
export const AEM_FAQ: AemFaqItem[] = [
  {
    q: "What is AEM search?",
    a: "AEM search is how visitors and employees find content managed in Adobe Experience Manager: pages, components and DAM assets. Adobe's built-in Query Builder / Omnisearch handles basic keyword lookups. For typo-tolerant, faceted and AI-powered search (semantic results and cited answers) you add a dedicated search layer. Viglet Turing indexes your AEM content and serves that layer, without moving off AEM.",
  },
  {
    q: "How do I enable AI search in AEM?",
    a: "Point the Turing AEM connector at your author or publish instance. It crawls pages and DAM assets (including PDFs) into a search index on Solr, Elasticsearch or embedded Lucene. Then drop the search + cited-chat UI into an AEM component, an Edge Delivery Services (EDS) block or your own SPA with the zero-dependency JavaScript SDK. It works with AEM 6.5 and AEM as a Cloud Service, needs no template changes, and first results come back in minutes.",
  },
  {
    q: "Is there an AEM search component I can drop into a page?",
    a: "Yes. The search box, facets, autocomplete and cited AI chat mount into an AEM page component or an Edge Delivery Services block with the zero-dependency JavaScript SDK, or into a React SPA with the React SDK. The component calls Turing as its search engine for AEM, so results, ranking and answers come from your own index rather than Adobe's Query Builder.",
  },
  {
    q: "Do I have to move off AEM?",
    a: "No. Turing sits alongside AEM. Its connector indexes AEM 6.5+ and AEM as a Cloud Service (pages, components and DAM assets) without touching your templates or authoring workflow. AEM stays your content source of truth; Turing adds the AI search and agent layer on top.",
  },
  {
    q: "Isn't it limited to AEM content, like the AEM-native tools?",
    a: "No. An AEM-native add-on stops at the AEM repository. Turing federates AEM with WordPress, databases, file shares and any REST source into one index, so a single cited answer can span your whole content estate, not just what lives in Oak.",
  },
  {
    q: "Where do my content and my users' queries go?",
    a: "Into your own infrastructure. Turing is self-hosted under Apache 2.0: indexed AEM content, embeddings and query logs stay on your hardware. A Cloud-Service-native add-on reasons over your content inside Adobe's cloud; Turing doesn't. Run local Ollama models and even the LLM stays in-house.",
  },
  {
    q: "Which LLMs can it use? Only OpenAI and Anthropic?",
    a: "More than that. OpenAI, Anthropic Claude, Google Gemini, Azure OpenAI, local Ollama and any OpenAI-compatible endpoint plug in behind one interface, switchable per agent, with encrypted keys. Reranking and embeddings are pluggable too.",
  },
  {
    q: "Do I need a multi-week consulting engagement to install it?",
    a: "No. It's open source with a zero-dependency CLI: scaffold a project, run a local stack and point the connector at AEM in one session. First searches come back in minutes, not after a 2–4 week paid implementation.",
  },
  {
    q: "Can I drop it into AEM pages or Edge Delivery Services?",
    a: "Yes. The vanilla-JS SDK is zero-dependency and framework-agnostic. It runs in an AEM component, a plain <script> tag or an Adobe EDS block (example blocks ship in the repo). There's also a React SDK with headless hooks and components for search, facets, autocomplete and cited chat.",
  },
  {
    q: "Does it handle PDFs and DAM assets?",
    a: "Yes. The connector extracts text from PDFs and Office documents in the DAM and indexes them alongside pages, so answers can cite an asset the same way they cite a page.",
  },
];

/* -------------------------------------------------------------------------- */
/* Persona solution page (/persona): "Give your AI a voice that sounds like    */
/* your company". Reframes prompt-engineering-per-agent as a reusable,         */
/* governable brand voice: define once, attach to any agent, enforce           */
/* vocabulary in two layers, and measure whether content fits its audience.    */
/* Honest: where a plain system prompt genuinely gets you part-way, "partial". */
/* -------------------------------------------------------------------------- */

export interface PersonaMatrixRow {
  /** The capability, phrased neutrally. */
  capability: string;
  /** "yes" | "partial" | "no" | freeform short text (per Cell). */
  turing: string;
  /** Hand-rolled prompt engineering, per agent. */
  prompt: string;
  /** An off-the-shelf chatbot / a single system prompt. */
  chatbot: string;
}

export const PERSONA_COLUMNS = {
  turing: "Turing Persona",
  prompt: "Prompt engineering",
  chatbot: "Off-the-shelf chatbot",
} as const;

/**
 * What a governed Persona gives you that a hand-written prompt or a generic
 * chatbot can't. "partial" means a plain system prompt can approximate it but
 * without reuse, enforcement or measurement. High-level, as of 2026.
 */
export const PERSONA_MATRIX: PersonaMatrixRow[] = [
  { capability: "One brand voice, reused across every agent", turing: "yes", prompt: "copy-paste", chatbot: "partial" },
  { capability: "Mandatory & forbidden vocabulary, enforced", turing: "two layers", prompt: "no", chatbot: "no" },
  { capability: "Post-response tone masking (PT · EN · ES)", turing: "yes", prompt: "no", chatbot: "no" },
  { capability: "Teach the voice by example (few-shot store)", turing: "yes", prompt: "manual", chatbot: "no" },
  { capability: "Live brand facts via MCP, no redeploy", turing: "yes", prompt: "no", chatbot: "no" },
  { capability: "Ground answers in your indexed content", turing: "yes", prompt: "manual", chatbot: "no" },
  { capability: "Big Five (OCEAN) personality control", turing: "yes", prompt: "no", chatbot: "no" },
  { capability: "Swap the LLM, keep the voice", turing: "yes", prompt: "rewrite", chatbot: "no" },
  { capability: "Audience personas + content-fit scoring", turing: "yes", prompt: "no", chatbot: "no" },
  { capability: "Persona Match: N×N content × audience", turing: "yes", prompt: "no", chatbot: "no" },
  { capability: "Synthetic user research at scale", turing: "yes", prompt: "no", chatbot: "no" },
  { capability: "Draft a persona from a voice recording", turing: "yes", prompt: "no", chatbot: "no" },
  { capability: "Configured by non-engineers, no code", turing: "yes", prompt: "no", chatbot: "partial" },
];

export interface PersonaStep {
  step: string;
  title: string;
  body: string;
}

/** How a Persona reaches production: define → attach → govern → measure. */
export const PERSONA_STEPS: PersonaStep[] = [
  {
    step: "1",
    title: "Define the voice once",
    body: "In Administration → Personas, describe who the persona is (a free-text system instruction), set tone, verbosity and language style, and pin the vocabulary you require and the vocabulary you forbid. An AI-authoring assistant can draft every field from a plain-language brief, and nothing is saved until you approve it.",
  },
  {
    step: "2",
    title: "Attach it to any agent",
    body: "Give an AI Agent a catalog of personas and star one as default. The voice is decoupled from the brain (the LLM) and the hands (the tools): switch either, and the persona keeps every agent it's attached to sounding like the same on-brand representative. A flow can even switch persona mid-conversation.",
  },
  {
    step: "3",
    title: "Govern in two layers",
    body: "Forbidden terms are enforced twice: in the prompt, so the model never intends to say them, and again after the response, where any slip is masked as [***] before it reaches the user. It understands Portuguese, English and Spanish, and matches word variants and whole phrases. Compliance auditors review one list, not every reply.",
  },
  {
    step: "4",
    title: "Measure the audience",
    body: "A persona can also be a reader profile. Score whether a document fits the people meant to read it: readability plus a text-grounded AI review. Persona Match runs that across many contents × many audiences on a schedule; Synthetic User Research interviews a cohort before you spend a real participant's time.",
  },
];

export interface PersonaSurface {
  icon: LucideIcon;
  iconClass: string;
  title: string;
  body: string;
}

/**
 * The persona surfaces: a persona is more than a system prompt. Each card
 * names one thing you operate directly, grounded in the product's real feature
 * set (voice, audience/content-fit, match, dialogue, research, from-audio).
 */
export const PERSONA_SURFACES: PersonaSurface[] = [
  {
    icon: MessageSquare,
    iconClass: "from-[#1a3a9e] to-[#4169E1]",
    title: "A voice for every agent",
    body: "Attach a persona and the agent speaks in your tone, uses the words you want to be remembered by, and avoids the ones that get you in trouble. Talk to a persona directly on a shareable URL to approve the voice before it goes live, with no agent required.",
  },
  {
    icon: Users,
    iconClass: "from-[#059669] to-[#10b981]",
    title: "Audiences & content-fit",
    body: "Model a target reader (reading level, domain expertise, vocabulary ceiling) and measure whether a page fits them. You get a readability score (no AI needed) plus a text-grounded AI review that flags the exact spans that miss, each with a rewrite suggestion.",
  },
  {
    icon: LayoutGrid,
    iconClass: "from-[#d97706] to-[#f59e0b]",
    title: "Persona Match (N×N)",
    body: "A reusable project that scores many contents against many audiences at once, filling a color-coded heatmap live. It re-runs on a daily or weekly schedule and re-reads only what changed. Exports to PDF.",
  },
  {
    icon: MessagesSquare,
    iconClass: "from-[#8b5cf6] to-[#6366f1]",
    title: "Persona Dialogue",
    body: "Put two or more brand voices in a room and let them debate a topic, turn by turn, live. You hear where your voices diverge (a \"diff of voices\") before you decide which to attach to which agent. Saved as reusable projects.",
  },
  {
    icon: FlaskConical,
    iconClass: "from-[#0ea5e9] to-[#4169E1]",
    title: "Synthetic user research",
    body: "Interview a cohort of personas against a research script and summarize the findings by theme, with verbatim quotes and a \"good enough\" saturation signal that tells you when more interviews stop adding anything. A discovery aid, never a substitute for real users.",
  },
  {
    icon: Mic,
    iconClass: "from-[#4f46e5] to-[#6366f1]",
    title: "Draft from a recording",
    body: "Hand Turing five minutes of \"here's how our ideal rep sounds\" and it transcribes, classifies the style fields and writes a system instruction. You get a persona draft to review and save. Derive, never auto-apply.",
  },
];

export interface PersonaVoice {
  /** The style label shown on the card. */
  style: string;
  /** tone · verbosity · language-style, as a short caption. */
  config: string;
  /** The answer that configuration produces. */
  answer: string;
  /** Where you'd use it. */
  use: string;
}

/**
 * "Same question, three voices" (book ch. 7). One question ("Is the annual
 * plan worth it?") answered by three persona configurations over the *same*
 * model. Shows that the persona, not the model, decides how the company sounds.
 */
export const PERSONA_VOICES: PersonaVoice[] = [
  {
    style: "The closer",
    config: "EXECUTIVE · verbosity 2 · PERSUASIVE",
    answer: "\"Worth it: you lock this year's price and get two months free. Most teams that switch recover the cost in 90 days. Want me to run the numbers for your volume?\"",
    use: "Sales chat on a corporate site",
  },
  {
    style: "The advisor",
    config: "TECHNICAL · verbosity 4 · INSTRUCTIONAL",
    answer: "A short paragraph walking through the month-by-month savings math, with each plan's limits laid out in bullets so the reader can verify it themselves.",
    use: "Developer-relations or pre-sales",
  },
  {
    style: "The friend",
    config: "CASUAL · verbosity 2 · DIRECT",
    answer: "\"Way cheaper yearly 😊 it's 16% off. Wanna go for it?\"",
    use: "In-app chat on a mobile product",
  },
];

export interface PersonaFaqItem {
  q: string;
  a: string;
}

/** Objection-answering FAQ for the persona page (emitted as FAQPage JSON-LD). */
export const PERSONA_FAQ: PersonaFaqItem[] = [
  {
    q: "What is an AI persona?",
    a: "A persona is the voice profile of your AI: a small, reusable bundle of decisions (system instruction, tone, verbosity, required and forbidden vocabulary, optional personality and knowledge grounding) that replaces an LLM's generic default voice with your company's. You configure it once in Viglet Turing and attach it to any AI Agent, and the same persona keeps every agent sounding like the same on-brand representative.",
  },
  {
    q: "How is a persona different from a system prompt?",
    a: "A system prompt is text you hand-write into one agent. A persona is a governed, reusable object: it's enforced in two layers (forbidden terms are stripped both in the prompt and after the response), it teaches the voice by example from a few-shot store, it pulls live brand facts from an MCP server, and it's decoupled from the model, so you can swap the LLM and keep the voice. It's also editable by non-engineers, and the same persona reuses across every agent instead of being copy-pasted.",
  },
  {
    q: "Can a persona keep the AI from saying the wrong thing?",
    a: "Yes. That is what forbidden terms are for. They're applied twice: in the prompt so the model never intends to say them, and again after the response, where any match is masked as [***] before the user sees it. It works in Portuguese, English and Spanish and recognizes word variants and whole phrases, so 'promotion', 'promotions' and 'promoting' are all caught. Compliance reviews one list instead of every reply.",
  },
  {
    q: "Do I have to re-write prompts when I change the LLM?",
    a: "No. A persona is the voice; the LLM is the brain and the tools are the hands. Because the three are decoupled, you can switch from one provider to another (OpenAI, Anthropic, Gemini, Azure OpenAI, local Ollama) and the persona keeps every agent it's attached to speaking the same way.",
  },
  {
    q: "How do I keep prices and promotions up to date without engineering?",
    a: "Point the persona at a Brand Context MCP server you control. On every conversation Turing calls it and injects the current facts as a 'brand facts' block, so marketing updates a promotion in their own panel and the next message reflects it, with no prompt edit and no redeploy. The same MCP feeds every persona at once.",
  },
  {
    q: "Can a persona tell me whether my content fits its audience?",
    a: "Yes. Model a target reader as an AUDIENCE persona (reading level, domain expertise, vocabulary ceiling) and score any document against it. You get a fixed readability score plus a text-grounded AI review that lists what fits and what doesn't, with the exact span, the reason and a rewrite suggestion. Persona Match scales this to many contents × many audiences on a schedule.",
  },
  {
    q: "Is synthetic user research a replacement for talking to real users?",
    a: "No, and Turing is explicit about it. Synthetic research interviews a cohort of personas to sharpen your questions, surface likely themes and pressure-test an idea before you spend a real participant's time. A 'good enough' saturation signal tells you when more interviews stop adding anything, and every study reads as a discovery aid. Always validate with real users before you act.",
  },
  {
    q: "Do I need to write code to use personas?",
    a: "No. Personas are configured entirely in the admin UI, and an AI-authoring assistant can draft every field from a plain-language brief, so a marketer can create one. A REST API (/api/persona and friends) is there for teams that integrate by code, but day-to-day persona operation doesn't require it.",
  },
];
