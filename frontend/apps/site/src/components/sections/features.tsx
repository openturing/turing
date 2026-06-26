import { Card } from "@/components/ui/card";
import { Container, SectionHeader } from "@/components/brand";
import { FEATURES } from "@/lib/site-content";
import { cn } from "@/lib/utils";

export function Features() {
  return (
    <section id="features" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Capabilities"
          title="Everything to ship an AI search experience"
          description="From classic faceted search to autonomous agents that reason over your content, call tools and run skills — all in one platform."
        />
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((f) => {
            const Icon = f.icon;
            return (
              <Card
                key={f.title}
                className="group gap-0 p-6 transition-all duration-200 hover:-translate-y-1.5 hover:border-primary/50 hover:shadow-xl hover:shadow-primary/10"
              >
                <div
                  className={cn(
                    "mb-4 flex size-11 items-center justify-center rounded-xl bg-gradient-to-br text-white",
                    f.iconClass
                  )}
                >
                  <Icon className="size-6" />
                </div>
                <h3 className="text-lg font-bold">{f.title}</h3>
                <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                  {f.description}
                </p>
                <div className="mt-4 flex flex-wrap gap-2">
                  {f.tags.map((tag) => (
                    <span
                      key={tag}
                      className="rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              </Card>
            );
          })}
        </div>
      </Container>
    </section>
  );
}
