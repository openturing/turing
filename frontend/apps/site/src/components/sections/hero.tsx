import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, LINKS } from "@/components/brand";
import { CopyCommand } from "@/components/copy-command";
import { SearchDemo } from "@/components/sections/search-demo";

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
            Open source · Apache 2.0 · Self-hosted
          </span>
          <h1 className="text-4xl font-extrabold leading-[1.08] tracking-tight sm:text-5xl lg:text-[3.4rem]">
            The open-source, self-hosted{" "}
            <span className="text-gradient">alternative to Algolia</span> — with AI
            agents built in.
          </h1>
          <p className="mt-5 max-w-xl text-lg leading-relaxed text-[#aeb7d4]">
            Add AI search, RAG and agents to your own content. Any LLM, your
            infrastructure, your data — never locked in someone else's cloud.
          </p>

          <div className="mt-7">
            <CopyCommand command="npx @viglet/turing-cli init my-search" />
          </div>

          <div className="mt-6 flex flex-wrap gap-3">
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
              <a href="#why">Why Turing</a>
            </Button>
          </div>

          <div className="mt-7 flex flex-wrap gap-x-6 gap-y-2 text-sm text-[#8b94b3]">
            {["Your data stays in your infra", "Any LLM provider", "MCP-native"].map(
              (item) => (
                <span key={item} className="inline-flex items-center gap-1.5">
                  <Check className="size-4 text-emerald-400" />
                  {item}
                </span>
              )
            )}
          </div>
        </div>

        <div>
          <SearchDemo />
          <p className="mt-3 text-center text-xs text-[#8b94b3]">
            Live demo with sample data — try typing above.
          </p>
        </div>
      </Container>
    </section>
  );
}
