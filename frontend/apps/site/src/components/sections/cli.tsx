import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, Eyebrow } from "@/components/brand";

const NPM = "https://www.npmjs.com/package/@viglet/turing-cli";

const POINTS = [
  "Scaffold an agent project and run a full local stack with Docker Compose",
  "Deploy agents, flows, tools and skills to any environment",
  "Tail live chat events and run YAML eval suites in CI",
];

function CliTerminal() {
  const C = "text-[#6b7394]"; // comment
  const P = "text-emerald-400"; // prompt $
  const F = "text-[#82aaff]"; // flag/arg
  return (
    <div className="overflow-hidden rounded-xl border border-white/10 bg-[#0d101e]/90 shadow-2xl shadow-black/50">
      <div className="flex items-center gap-1.5 border-b border-white/8 bg-white/[0.02] px-3.5 py-2.5">
        <span className="size-2.5 rounded-full bg-[#ff5f57]" />
        <span className="size-2.5 rounded-full bg-[#febc2e]" />
        <span className="size-2.5 rounded-full bg-[#28c840]" />
        <span className="ml-2 font-mono text-xs text-[#8b94b3]">zsh — turing</span>
      </div>
      <pre className="overflow-x-auto p-5 font-mono text-[0.8rem] leading-relaxed text-[#d7def5]">
        <code>
          <span className={P}>$</span> npm i -g <span className={F}>@viglet/turing-cli</span>
          {"\n\n"}
          <span className={P}>$</span> turing init <span className={F}>my-copilot</span>{"   "}
          <span className={C}># scaffold an agent project</span>
          {"\n"}
          <span className={P}>$</span> turing dev{"             "}
          <span className={C}># local stack via Docker</span>
          {"\n"}
          <span className={P}>$</span> turing deploy{" "}
          <span className={F}>--env=prod</span>{" "}
          <span className={C}># push agent + flows + tools + skills</span>
          {"\n"}
          <span className={P}>$</span> turing eval{"            "}
          <span className={C}># run YAML eval suites</span>
          {"\n"}
          <span className={P}>$</span> turing logs{" "}
          <span className={F}>--conversation abc</span>{" "}
          <span className={C}># tail chat events</span>
        </code>
      </pre>
    </div>
  );
}

export function Cli() {
  return (
    <section id="cli" className="py-20">
      <Container className="grid items-center gap-12 lg:grid-cols-2">
        <div>
          <Eyebrow>turing CLI</Eyebrow>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
            Ship agents from your terminal
          </h2>
          <p className="mt-3 text-base leading-relaxed text-muted-foreground sm:text-lg">
            <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-[0.85em]">
              @viglet/turing-cli
            </code>{" "}
            is a zero-dependency developer CLI — scaffold a project, run a full
            local stack, deploy to any environment, and evaluate your agents in CI.
          </p>
          <ul className="mt-6 space-y-3">
            {POINTS.map((p) => (
              <li key={p} className="flex items-start gap-2.5 text-sm">
                <Check className="mt-0.5 size-4 shrink-0 text-primary" />
                <span className="text-muted-foreground">{p}</span>
              </li>
            ))}
          </ul>
          <div className="mt-8 flex flex-wrap gap-3">
            <Button asChild>
              <a href={NPM} target="_blank" rel="noreferrer">
                Get the CLI <ArrowRight className="size-4" />
              </a>
            </Button>
          </div>
        </div>

        <CliTerminal />
      </Container>
    </section>
  );
}
