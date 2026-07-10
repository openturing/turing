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
];

/** Render a route's app shell to an HTML string for the prerender step. */
export function render(path: string): string {
  return renderToString(
    <ThemeProvider defaultTheme="system" storageKey="turing-site-theme">
      <App path={path} />
    </ThemeProvider>
  );
}
