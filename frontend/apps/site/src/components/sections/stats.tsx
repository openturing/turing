import { Container } from "@/components/brand";

/**
 * Proof strip — real, verifiable capability facts, not vanity metrics.
 */
const FACTS: { value: string; label: string }[] = [
  { value: "Apache 2.0", label: "Open source — free forever" },
  { value: "Any LLM", label: "OpenAI · Anthropic · Gemini · Ollama" },
  { value: "3 engines", label: "Solr · Elasticsearch · Lucene" },
  { value: "Agents + RAG", label: "Tools · Skills · MCP" },
];

export function Stats() {
  return (
    <section className="bg-muted/40 py-12">
      <Container>
        <div className="grid grid-cols-2 gap-6 text-center sm:grid-cols-4">
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
