import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, LINKS } from "@/components/brand";

function HeroTerminal() {
  return (
    <div className="overflow-hidden rounded-xl border border-white/10 bg-[#0d101e]/85 shadow-2xl shadow-black/50 backdrop-blur">
      <div className="flex items-center gap-1.5 border-b border-white/8 bg-white/[0.02] px-3.5 py-2.5">
        <span className="size-2.5 rounded-full bg-[#ff5f57]" />
        <span className="size-2.5 rounded-full bg-[#febc2e]" />
        <span className="size-2.5 rounded-full bg-[#28c840]" />
        <span className="ml-2 font-mono text-xs text-[#8b94b3]">agent-chat.tsx</span>
      </div>
      <pre className="overflow-x-auto p-5 font-mono text-[0.8rem] leading-relaxed text-[#d7def5]">
        <code>
          <span className="text-[#6b7394]">{"// Conversational, agentic search over your content"}</span>
          {"\n"}
          <span className="text-[#c792ea]">import</span> {"{ useTuringChat } "}
          <span className="text-[#c792ea]">from</span>{" "}
          <span className="text-[#c3e88d]">"@viglet/turing-react-sdk"</span>;{"\n\n"}
          <span className="text-[#c792ea]">const</span> {"{ "}
          <span className="text-[#82aaff]">messages</span>,{" "}
          <span className="text-[#82aaff]">send</span> {"} = "}
          <span className="text-[#82aaff]">useTuringChat</span>({"{"}
          {"\n  "}
          <span className="text-[#82aaff]">agent</span>:{" "}
          <span className="text-[#c3e88d]">"support-copilot"</span>,{"\n  "}
          <span className="text-[#82aaff]">tools</span>: [
          <span className="text-[#c3e88d]">"search_site"</span>,{" "}
          <span className="text-[#c3e88d]">"get_site_fields"</span>],{"\n"}
          {"});"}
          {"\n\n"}
          <span className="text-[#82aaff]">send</span>(
          <span className="text-[#c3e88d]">"Which plans include SSO and audit logs?"</span>);
          {"\n"}
          <span className="text-[#6b7394]">{"// → streams tokens, calls tools, cites sources via RAG"}</span>
        </code>
      </pre>
    </div>
  );
}

export function Hero() {
  return (
    <section
      id="top"
      className="relative isolate overflow-hidden bg-[#080b18] text-[#f2f4fb]"
    >
      {/* grid + orbs */}
      <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
      <div className="animate-orb-a pointer-events-none absolute -top-44 -left-20 -z-10 h-[520px] w-[520px] rounded-full bg-[radial-gradient(circle,#4169E1,transparent_70%)] opacity-50 blur-[70px]" />
      <div className="animate-orb-b pointer-events-none absolute -top-28 -right-24 -z-10 h-[460px] w-[460px] rounded-full bg-[radial-gradient(circle,#6366f1,transparent_70%)] opacity-50 blur-[70px]" />

      <Container className="grid items-center gap-12 py-20 lg:grid-cols-[1.05fr_0.95fr] lg:py-28">
        <div>
          <span className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3.5 py-1.5 text-xs font-semibold text-[#c7d0ee]">
            <span className="size-1.5 rounded-full bg-emerald-400 shadow-[0_0_8px] shadow-emerald-400" />
            Open source · Apache 2.0 · Spring Boot 4 + React 19
          </span>
          <h1 className="text-4xl font-extrabold leading-[1.08] tracking-tight sm:text-5xl lg:text-6xl">
            Agentic <span className="text-gradient">Enterprise Search</span> for your content.
          </h1>
          <p className="mt-5 max-w-xl text-lg leading-relaxed text-[#aeb7d4]">
            Viglet Turing ES brings AI agents, tools, skills, MCP and RAG to your
            enterprise content — semantic navigation, generative AI and advanced
            search, in one open platform you fully control.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href={LINKS.docs}>
                Get started <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href={LINKS.storybook}>Explore the SDK</a>
            </Button>
          </div>
          <div className="mt-7 flex flex-wrap gap-x-6 gap-y-2 text-sm text-[#8b94b3]">
            {["Self-hosted & data-sovereign", "Any LLM provider", "MCP-native"].map(
              (item) => (
                <span key={item} className="inline-flex items-center gap-1.5">
                  <Check className="size-4 text-emerald-400" />
                  {item}
                </span>
              )
            )}
          </div>
        </div>

        <HeroTerminal />
      </Container>
    </section>
  );
}
