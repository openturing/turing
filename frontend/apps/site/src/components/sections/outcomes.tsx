import { Container, SectionHeader } from "@/components/brand";
import { Card } from "@/components/ui/card";
import { OUTCOMES } from "@/lib/site-content";
import { cn } from "@/lib/utils";

/**
 * Outcome layer — sells the business result, not the feature. A capability
 * matrix convinces an engineer; a buyer wants to know what changes for their
 * team and their users. Sits right after the "what it is" pillars.
 */
export function Outcomes() {
  return (
    <section className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Why teams choose it"
          title="Outcomes, not just features"
          description="A capability list is easy to skim past. Here's what the platform actually changes for your team and your users."
        />
        <div className="grid gap-5 sm:grid-cols-2">
          {OUTCOMES.map((o) => (
            <Card key={o.title} className="h-full gap-0 p-6">
              <span
                className={cn(
                  "flex size-11 items-center justify-center rounded-2xl bg-gradient-to-br text-white shadow-md",
                  o.iconClass
                )}
              >
                <o.icon className="size-5" />
              </span>
              <h3 className="mt-5 text-lg font-bold">{o.title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                {o.body}
              </p>
            </Card>
          ))}
        </div>
      </Container>
    </section>
  );
}
