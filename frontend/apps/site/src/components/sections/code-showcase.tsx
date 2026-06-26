import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";
import { Container, SectionHeader } from "@/components/brand";

const kw = "text-[#8b5cf6] dark:text-[#c792ea]";
const str = "text-[#16a34a] dark:text-[#c3e88d]";
const fn = "text-[#2563eb] dark:text-[#82aaff]";
const cm = "text-muted-foreground";

function CodeCard({
  dot,
  label,
  badge,
  children,
}: {
  dot: string;
  label: string;
  badge: string;
  children: ReactNode;
}) {
  return (
    <Card className="gap-0 overflow-hidden p-0">
      <div className="flex items-center gap-2.5 border-b border-border px-4 py-3">
        <span className="size-2.5 rounded-full" style={{ backgroundColor: dot }} />
        <span className="font-mono text-xs text-muted-foreground">{label}</span>
        <span className="ml-auto rounded bg-primary/10 px-2 py-0.5 text-[0.65rem] font-bold text-primary">
          {badge}
        </span>
      </div>
      <pre className="overflow-x-auto bg-muted/50 p-5 font-mono text-[0.78rem] leading-relaxed">
        <code>{children}</code>
      </pre>
    </Card>
  );
}

export function CodeShowcase() {
  return (
    <section id="developers" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Built for developers"
          title="Integrate in minutes, not months"
          description="Pick the SDK that fits your stack, or call the API directly. Everything is open source and self-hostable."
        />
        <div className="grid gap-5 lg:grid-cols-2">
          <CodeCard dot="#4169E1" label="search.ts — REST API" badge="REST">
            <span className={cm}>{"// Semantic search with facets & pagination"}</span>
            {"\n"}
            <span className={kw}>const</span> res = <span className={kw}>await</span>{" "}
            <span className={fn}>fetch</span>(
            {"\n  "}
            <span className={str}>"/api/sn/products/search?q=wireless+headphones"</span>
            {"\n);\n"}
            <span className={kw}>const</span> {"{ "}
            <span className={fn}>results</span>, <span className={fn}>facets</span>
            {" } = "}
            <span className={kw}>await</span> res.<span className={fn}>json</span>();
          </CodeCard>

          <CodeCard dot="#6366f1" label="chat.ts — Chat API (SSE)" badge="Streaming">
            <span className={cm}>{"// LLM-powered conversational search"}</span>
            {"\n"}
            <span className={kw}>const</span> res = <span className={kw}>await</span>{" "}
            <span className={fn}>fetch</span>(
            {"\n  "}
            <span className={str}>"/api/v2/llm/default/semantic-chat"</span>,
            {"\n  { "}
            <span className={fn}>method</span>: <span className={str}>"POST"</span>,{" "}
            <span className={fn}>body</span> {"}"}
            {"\n);\n"}
            <span className={kw}>for</span> <span className={kw}>await</span> (
            <span className={kw}>const</span> token <span className={kw}>of</span>{" "}
            <span className={fn}>readSSE</span>(res)) <span className={fn}>render</span>(token);
          </CodeCard>
        </div>
      </Container>
    </section>
  );
}
