import { Container } from "@/components/brand";
import { PROVIDERS } from "@/lib/site-content";

export function Providers() {
  return (
    <section className="border-b border-border py-10">
      <Container>
        <p className="text-center text-xs font-bold tracking-[0.08em] text-muted-foreground uppercase">
          Pluggable LLM providers & search engines
        </p>
        <div className="mt-5 flex flex-wrap justify-center gap-2.5">
          {PROVIDERS.map((p) => (
            <span
              key={p.name}
              className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-4 py-2 text-sm font-semibold"
            >
              <span
                className="size-2 rounded-full"
                style={{ backgroundColor: p.color }}
              />
              {p.name}
            </span>
          ))}
        </div>
      </Container>
    </section>
  );
}
