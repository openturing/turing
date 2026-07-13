import { ArrowRight, Check } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { AUDIENCES } from "@/lib/site-content";

/**
 * Audience split — the site talks to two very different readers (a developer
 * adopting bottom-up, and an enterprise buyer). This section gives each a clear
 * path instead of leaving them to guess which parts are for them.
 */
export function Audiences() {
  return (
    <section className="border-t border-border bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="Who it's for"
          title="One platform, two ways in"
          description="Adopt it bottom-up as a developer, or bring it in as a governed enterprise search layer — the same open-source core underneath."
        />
        <div className="grid gap-6 lg:grid-cols-2">
          {AUDIENCES.map((a) => (
            <Card key={a.eyebrow} className="flex h-full flex-col gap-0 p-8">
              <span className="text-xs font-extrabold uppercase tracking-[0.12em] text-primary">
                {a.eyebrow}
              </span>
              <h3 className="mt-2 text-xl font-extrabold tracking-tight">
                {a.title}
              </h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                {a.body}
              </p>
              <ul className="mt-5 space-y-2.5">
                {a.points.map((p) => (
                  <li key={p} className="flex items-start gap-2.5 text-sm">
                    <Check className="mt-0.5 size-4 shrink-0 text-emerald-500" />
                    <span>{p}</span>
                  </li>
                ))}
              </ul>
              <div className="mt-auto flex flex-wrap gap-3 pt-7">
                <Button asChild>
                  <a href={a.primary.href}>
                    {a.primary.label} <ArrowRight className="size-4" />
                  </a>
                </Button>
                <Button asChild variant="outline">
                  <a href={a.secondary.href}>{a.secondary.label}</a>
                </Button>
              </div>
            </Card>
          ))}
        </div>
      </Container>
    </section>
  );
}
