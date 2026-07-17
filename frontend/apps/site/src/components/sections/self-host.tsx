import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, Eyebrow, LINKS } from "@/components/brand";
import { CopyCommand } from "@/components/copy-command";

const POINTS = [
  "Prebuilt container image — nothing to clone, nothing to build",
  "Your content and embeddings never leave your network — no SaaS tier, no telemetry",
  "Point it at any LLM provider and your own Solr, Elasticsearch or embedded Lucene",
];

function ComposeTerminal() {
  const C = "text-[#6b7394]"; // comment
  const P = "text-emerald-400"; // prompt $
  const F = "text-[#82aaff]"; // arg
  const OK = "text-emerald-400";
  return (
    <div className="min-w-0 overflow-hidden rounded-xl border border-white/10 bg-[#0d101e]/90 shadow-2xl shadow-black/50">
      <div className="flex items-center gap-1.5 border-b border-white/8 bg-white/[0.02] px-3.5 py-2.5">
        <span className="size-2.5 rounded-full bg-[#ff5f57]" />
        <span className="size-2.5 rounded-full bg-[#febc2e]" />
        <span className="size-2.5 rounded-full bg-[#28c840]" />
        <span className="ml-2 font-mono text-xs text-[#8b94b3]">bash — self-host</span>
      </div>
      <pre className="overflow-x-auto p-5 font-mono text-[0.8rem] leading-relaxed text-[#d7def5]">
        <code>
          <span className={P}>$</span> docker run -p 2700:2700{" "}
          <span className={F}>ghcr.io/openviglet/turing-ce</span>
          {"\n\n"}
          <span className={C}># pulling ghcr.io/openviglet/turing-ce:latest ...</span>
          {"\n"}
          <span className={OK}>✓</span> image pulled{"   "}
          <span className={C}>no build step</span>
          {"\n"}
          <span className={OK}>✓</span> turing-app  <span className={C}>running</span>
          {"\n\n"}
          <span className={C}># console + API ready on http://localhost:2700</span>
        </code>
      </pre>
    </div>
  );
}

export function SelfHost() {
  return (
    <section id="self-host" className="py-20">
      <Container className="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
        <ComposeTerminal />
        <div className="min-w-0">
          <Eyebrow>Run it yourself</Eyebrow>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
            Self-host the whole platform in one command
          </h2>
          <p className="mt-3 text-base leading-relaxed text-muted-foreground sm:text-lg">
            No account, no sales call, no data leaving your infrastructure. There is
            nothing to clone or build — the container image is already published to a
            public registry. Pull it, run it, then open the console and index your
            first site. Want the full stack (Solr, MariaDB, monitoring)? A ready
            <code className="mx-1 rounded bg-muted px-1.5 py-0.5 text-[0.85em]">
              docker compose
            </code>
            file ships in the repo too.
          </p>
          <ul className="mt-6 space-y-3">
            {POINTS.map((p) => (
              <li key={p} className="flex items-start gap-2.5 text-sm">
                <Check className="mt-0.5 size-4 shrink-0 text-primary" />
                <span className="text-muted-foreground">{p}</span>
              </li>
            ))}
          </ul>
          <div className="mt-7">
            <CopyCommand
              command="docker run -p 2700:2700 ghcr.io/openviglet/turing-ce"
              className="border-border bg-muted text-foreground"
            />
          </div>
          <div className="mt-6 flex flex-wrap gap-3">
            <Button asChild>
              <a href={LINKS.docs}>
                Deployment guide <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button asChild variant="outline">
              <a href="#playground">Try the live demo</a>
            </Button>
          </div>
        </div>
      </Container>
    </section>
  );
}
