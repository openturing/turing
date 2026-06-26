import {
  Bot,
  Boxes,
  Code2,
  Database,
  Search,
  Wrench,
  type LucideIcon,
} from "lucide-react";

export interface Feature {
  icon: LucideIcon;
  iconClass: string;
  title: string;
  description: string;
  tags: string[];
}

export const FEATURES: Feature[] = [
  {
    icon: Bot,
    iconClass: "from-[#1a3a9e] to-[#4169E1]",
    title: "AI Agents",
    description:
      "Configurable agents with system prompts, tools, memory, slots and capability matrices. Agents reason, call tools and stream answers token-by-token.",
    tags: ["Tool calling", "Streaming", "Memory"],
  },
  {
    icon: Wrench,
    iconClass: "from-[#4f46e5] to-[#6366f1]",
    title: "Tools & MCP",
    description:
      "Built-in search tools, custom Groovy tools, and a full MCP Server with cross-vendor federation — plus native provider tools (web search, code execution, computer use).",
    tags: ["MCP Server", "Federation", "Custom tools"],
  },
  {
    icon: Boxes,
    iconClass: "from-[#7c3aed] to-[#a78bfa]",
    title: "Skills",
    description:
      "Anthropic-standard skill folders run in a hardened Docker sandbox with progressive disclosure. Drop in a SKILL.md, import a ZIP, and agents use it automatically.",
    tags: ["Sandboxed", "SKILL.md", "Importable"],
  },
  {
    icon: Database,
    iconClass: "from-[#059669] to-[#10b981]",
    title: "RAG & Reranking",
    description:
      "Retrieval-augmented generation with hybrid ranking (RRF), a pluggable reranker (LLM, cross-encoder or Cohere) and an embedded vector store on Lucene.",
    tags: ["Hybrid RRF", "Reranker", "Vectors"],
  },
  {
    icon: Search,
    iconClass: "from-[#d97706] to-[#f59e0b]",
    title: "Semantic Navigation",
    description:
      "Faceted, multilingual search with field manifests as code, currency & numeric localization, and field-coverage observability — production search, not a demo.",
    tags: ["Facets", "Manifest", "i18n"],
  },
  {
    icon: Code2,
    iconClass: "from-[#e11d48] to-[#fb7185]",
    title: "SDKs & APIs",
    description:
      "React SDK, a zero-dependency vanilla JS SDK (Adobe EDS-ready), headless UI components, a CLI, plus REST, SSE streaming and GraphQL APIs.",
    tags: ["React", "Vanilla JS", "CLI"],
  },
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
    href: "https://github.com/openviglet/turing",
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
