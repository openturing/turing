import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, LINKS } from "@/components/brand";

export function CallToAction() {
  return (
    <section className="py-20">
      <Container>
        <div className="relative isolate overflow-hidden rounded-3xl border border-white/10 bg-[#0a0d1c] px-6 py-16 text-center text-white">
          <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
          <div className="pointer-events-none absolute -top-24 left-1/2 -z-10 h-80 w-80 -translate-x-1/2 rounded-full bg-[radial-gradient(circle,#4f46e5,transparent_70%)] opacity-50 blur-[70px]" />
          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl">
            Own your AI search stack
          </h2>
          <p className="mx-auto mt-3 max-w-lg text-base text-[#aeb7d4] sm:text-lg">
            Open source, self-hosted, and ready for any LLM. Stand up the full
            platform in one command and ship cited AI answers over your own content —
            without your data ever leaving your infrastructure.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Button asChild size="lg">
              <a href={LINKS.docs}>
                Read the docs <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href="/#playground">Try it live</a>
            </Button>
          </div>
        </div>
      </Container>
    </section>
  );
}
