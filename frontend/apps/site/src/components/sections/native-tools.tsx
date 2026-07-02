import { ArrowRight } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Container, SectionHeader } from "@/components/brand";
import { NATIVE_TOOLS } from "@/lib/site-content";

/** Brand dots for the provider badges on each tool card. */
const PROVIDER_COLOR: Record<string, string> = {
  OpenAI: "#10a37f",
  Anthropic: "#d9774f",
  Gemini: "#4285f4",
};

/**
 * Native provider tools grid.
 * `teaser` (landing page) appends a CTA into the full "Automate it" page;
 * omitted (on that page itself) renders the grid without the CTA.
 */
export function NativeTools({ teaser = false }: Readonly<{ teaser?: boolean }>) {
  return (
    <section id="native-tools" className="bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="Native provider tools"
          title="Turn on the tools your model already has"
          description="OpenAI, Anthropic and Gemini ship powerful tools that run in their own infrastructure. Turing wires them straight into the agent loop — opt-in per agent, no glue code, no extra crawler — and routes their citations into the same source-chip UI as your own search."
        />
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {NATIVE_TOOLS.map((tool) => (
            <Card
              key={tool.name}
              className="gap-0 p-6 transition-all duration-200 hover:-translate-y-1 hover:border-primary/50 hover:shadow-lg hover:shadow-primary/10"
            >
              <h3 className="text-base font-bold">{tool.name}</h3>
              <p className="mt-2 flex-1 text-sm leading-relaxed text-muted-foreground">
                {tool.description}
              </p>
              <div className="mt-4 flex flex-wrap gap-2">
                {tool.providers.map((p) => (
                  <span
                    key={p}
                    className="inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-2.5 py-0.5 text-xs font-semibold"
                  >
                    <span
                      className="size-1.5 rounded-full"
                      style={{ backgroundColor: PROVIDER_COLOR[p] ?? "#64748b" }}
                    />
                    {p}
                  </span>
                ))}
              </div>
            </Card>
          ))}
        </div>
        <p className="mt-6 text-center text-xs text-muted-foreground">
          Strictly opt-in via a two-level capability gate — your existing agents
          stay byte-for-byte unchanged. Provider availability as of 2026.3.
        </p>
        {teaser && (
          <div className="mt-8 flex justify-center">
            <Button asChild size="lg" variant="outline">
              <a href="/features/automate">
                See how agents use these <ArrowRight className="size-4" />
              </a>
            </Button>
          </div>
        )}
      </Container>
    </section>
  );
}
