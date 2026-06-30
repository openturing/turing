import { ArrowRight } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Container, SectionHeader } from "@/components/brand";
import { PILLARS } from "@/lib/site-content";
import { cn } from "@/lib/utils";

export function Pillars() {
  return (
    <section id="features" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="One platform, four jobs"
          title="Search it. Ask it. Automate it. Run it."
          description="Most teams stitch a search engine, a RAG pipeline and an agent framework together. Turing is all three — open source and self-hosted."
        />
        <div className="grid gap-5 md:grid-cols-2">
          {PILLARS.map((p) => {
            const Icon = p.icon;
            return (
              <Card
                key={p.step}
                className="group gap-0 p-6 transition-all duration-200 hover:-translate-y-1.5 hover:border-primary/50 hover:shadow-xl hover:shadow-primary/10"
              >
                <div className="flex items-center gap-3">
                  <div
                    className={cn(
                      "flex size-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br text-white",
                      p.iconClass
                    )}
                  >
                    <Icon className="size-6" />
                  </div>
                  <span className="text-xs font-extrabold tracking-[0.1em] text-muted-foreground uppercase">
                    {p.step}
                  </span>
                </div>
                <h3 className="mt-4 text-lg font-bold">{p.title}</h3>
                <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                  {p.description}
                </p>
                <div className="mt-4 flex flex-wrap gap-2">
                  {p.tags.map((tag) => (
                    <span
                      key={tag}
                      className="rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
                <a
                  href={`/features/${p.slug}`}
                  className="mt-5 inline-flex items-center gap-1 text-sm font-semibold text-primary hover:underline"
                >
                  Learn more
                  <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
                </a>
              </Card>
            );
          })}
        </div>
      </Container>
    </section>
  );
}
