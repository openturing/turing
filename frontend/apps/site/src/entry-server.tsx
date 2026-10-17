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
    title: "Viglet Turing · Agentic Enterprise Search",
    description:
      "Open-source Enterprise Search Intelligence Platform: AI agents, tools, skills, MCP and RAG over your enterprise content, with semantic navigation, generative AI and advanced search in one platform.",
  },
  ...FEATURE_SLUGS.map((slug) => {
    const d = FEATURE_DETAILS[slug];
    return {
      path: `/features/${slug}/`,
      title: d.metaTitle,
      description: d.metaDescription,
    };
  }),
  {
    path: "/migrate/",
    title: "Switching from Algolia or Elasticsearch? | Viglet Turing",
    description:
      "Migrate from Algolia or Elasticsearch to Viglet Turing:a concept-mapping table, a three-step import path (schema as code, bulk index, swap the SDK), and self-hosted search where your data and embeddings stay in your own infrastructure.",
  },
  {
    path: "/compare/",
    title: "Turing vs Algolia, Coveo, Elasticsearch & DIY RAG | Viglet Turing",
    description:
      "An honest comparison of Viglet Turing against Algolia, Coveo, Elasticsearch and a DIY LangChain RAG stack: a deep capability matrix, where each alternative wins, and an objection-answering FAQ. Self-hosted, open source, any LLM.",
  },
  {
    path: "/aem-search/",
    title: "AEM Search Component & AI Search Engine for AEM | Viglet Turing",
    description:
      "An AI search engine and search component for Adobe Experience Manager (AEM 6.5 + Cloud Service): cited, streaming answers, semantic + keyword search, agents, MCP, any LLM, self-hosted. Live demo on Adobe's WKND site.",
  },
  {
    path: "/persona/",
    title: "AI Personas · Give Your AI a Brand Voice | Viglet Turing",
    description:
      "Give your AI agents a governed brand voice: required and forbidden vocabulary enforced in two layers, live brand facts via MCP, audience content-fit scoring, Persona Match and synthetic user research. Define once, attach to any agent, and keep the voice when you swap the LLM. Self-hosted, open source.",
  },
  {
    path: "/security/",
    title: "Security & Data Residency · Self-Hosted AI Search | Viglet Turing",
    description:
      "Your content and users' queries never leave your network. Viglet Turing is self-hosted and open source: data residency by construction, SSO, encrypted secrets, any LLM (including fully local), and an auditable Apache 2.0 codebase.",
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
