import { renderToString } from "react-dom/server";
import App from "./App.tsx";
import { ThemeProvider } from "@/components/theme-provider";
import { FEATURE_DETAILS, FEATURE_SLUGS } from "@/lib/site-content";

export interface PrerenderRoute {
  /** URL path (also the output directory under dist/). */
  path: string;
  title: string;
  description: string;
}

/** Every route the build prerenders to its own static HTML file. */
export const routes: PrerenderRoute[] = [
  {
    path: "/",
    title: "Viglet Turing ES — Agentic Enterprise Search",
    description:
      "Open-source Enterprise Search Intelligence Platform. Bring AI agents, tools, skills, MCP and RAG to your enterprise content — semantic navigation, generative AI and advanced search in one platform.",
  },
  ...FEATURE_SLUGS.map((slug) => {
    const d = FEATURE_DETAILS[slug];
    return {
      path: `/features/${slug}`,
      title: d.metaTitle,
      description: d.metaDescription,
    };
  }),
  {
    path: "/migrate",
    title: "Switching from Algolia or Elasticsearch? | Viglet Turing ES",
    description:
      "Migrate from Algolia or Elasticsearch to Viglet Turing ES: a concept-mapping table, a three-step import path (schema as code, bulk index, swap the SDK), and self-hosted search where your data and embeddings stay in your own infrastructure.",
  },
  {
    path: "/compare",
    title: "Turing vs Algolia, Elasticsearch & DIY RAG | Viglet Turing ES",
    description:
      "An honest comparison of Viglet Turing ES against Algolia, Elasticsearch and a DIY LangChain RAG stack: a deep capability matrix, where each alternative wins, and an objection-answering FAQ. Self-hosted, open source, any LLM.",
  },
  {
    path: "/aem-search",
    title: "AEM Search with AI — Adobe Experience Manager | Viglet Turing ES",
    description:
      "AEM search, reimagined with AI. Add cited, streaming answers to Adobe Experience Manager (AEM 6.5 + Cloud Service) — semantic + keyword search, agents, MCP, any LLM, self-hosted. Deeper than an AEM-native add-on, with a live demo on Adobe's WKND site.",
  },
  {
    path: "/persona",
    title: "AI Personas — Give Your AI a Brand Voice | Viglet Turing ES",
    description:
      "Give your AI agents a governed brand voice. A Persona turns a generic LLM into your most on-brand representative — required and forbidden vocabulary enforced in two layers, live brand facts via MCP, audience content-fit scoring, Persona Match and synthetic user research. Define once, attach to any agent; swap the LLM, the voice stays. Self-hosted, open source.",
  },
  {
    path: "/security",
    title: "Security & Data Residency — Self-Hosted AI Search | Viglet Turing ES",
    description:
      "Your content and users' queries never leave your network. Viglet Turing ES is self-hosted and open source: data residency by construction, SSO, encrypted secrets, any LLM (including fully local), and an auditable Apache 2.0 codebase — instead of trusting a SaaS vendor's compliance badges.",
  },
];

/** Render a route's app shell to an HTML string for the prerender step. */
export function render(path: string): string {
  return renderToString(
    <ThemeProvider defaultTheme="system" storageKey="turing-site-theme">
      <App path={path} />
    </ThemeProvider>
  );
}
