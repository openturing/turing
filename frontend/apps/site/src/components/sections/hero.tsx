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

      <Container className="grid grid-cols-1 items-center gap-12 py-20 lg:grid-cols-[1.05fr_0.95fr] lg:py-28">
        <div className="min-w-0">
          <span className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3.5 py-1.5 text-xs font-semibold text-[#c7d0ee]">
            <span className="size-1.5 rounded-full bg-emerald-400 shadow-[0_0_8px] shadow-emerald-400" />
            Open source · Apache 2.0 · Self-hosted
          </span>
          <h1 className="text-4xl font-extrabold leading-[1.08] tracking-tight sm:text-5xl lg:text-[3.4rem]">
            Enterprise search,{" "}
            <span className="text-gradient">cited RAG and AI agents</span> — over
            your own content.
          </h1>
          <p className="mt-5 max-w-xl text-lg leading-relaxed text-[#aeb7d4]">
            Give your content AI search and grounded answers you can trace back to
            the source — on any LLM, in your own infrastructure. The open,
            self-hosted alternative to Algolia, with AI built in.
          </p>
          <p className="mt-3 max-w-xl text-sm leading-relaxed text-[#8b94b3]">
            For engineering teams who need production search and RAG without
            shipping their data to someone else's cloud.
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
              <a href="#playground">See it answer, live</a>
            </Button>
          </div>

          <div className="mt-7 flex flex-wrap gap-x-6 gap-y-2 text-sm text-[#8b94b3]">
            {[
              "Your data stays in your infra",
              "Any LLM provider",
              "Cited answers you can audit",
            ].map((item) => (
              <span key={item} className="inline-flex items-center gap-1.5">
                <Check className="size-4 text-emerald-400" />
                {item}
              </span>
            ))}
          </div>
        </div>

        <div className="min-w-0">
          <SearchDemo />
          <p className="mt-3 text-center text-xs text-[#8b94b3]">
            Try it — type to search.
          </p>
        </div>
      </Container>
    </section>
  );
}
