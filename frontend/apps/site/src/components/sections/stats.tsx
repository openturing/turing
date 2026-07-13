import { Container, GitHubIcon, LINKS } from "@/components/brand";

/**
 * Proof strip — real, verifiable signals instead of vanity spec-counts.
 * No star count: the numbers that matter here are capability facts, not
 * a GitHub popularity metric.
 */
const FACTS: { value: string; label: string }[] = [
  { value: "Any LLM", label: "OpenAI · Anthropic · Gemini · Ollama" },
  { value: "3 engines", label: "Solr · Elasticsearch · Lucene" },
  { value: "Agents + RAG", label: "Tools · Skills · MCP" },
];

export function Stats() {
  return (
    <section className="bg-muted/40 py-12">
      <Container>
        <div className="grid grid-cols-2 gap-6 text-center sm:grid-cols-4">
          <a
            href={LINKS.github}
            target="_blank"
            rel="noreferrer"
            className="group"
            title="View Turing on GitHub"
          >
            <div className="bg-gradient-to-r from-[#4169E1] to-[#818cf8] bg-clip-text text-3xl font-extrabold text-transparent">
              Apache 2.0
            </div>
            <div className="mt-1 inline-flex items-center gap-1 text-sm font-semibold text-muted-foreground group-hover:text-foreground">
              <GitHubIcon className="size-3.5" />
              Open source on GitHub
            </div>
          </a>
          {FACTS.map((s) => (
            <div key={s.label}>
              <div className="bg-gradient-to-r from-[#4169E1] to-[#818cf8] bg-clip-text text-3xl font-extrabold text-transparent">
                {s.value}
              </div>
              <div className="mt-1 text-sm font-semibold text-muted-foreground">
                {s.label}
              </div>
            </div>
          ))}
        </div>
      </Container>
    </section>
  );
}
