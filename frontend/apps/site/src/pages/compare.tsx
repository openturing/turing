import { ArrowLeft, ArrowRight, Check, Minus } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Container, LINKS, TableScroll } from "@/components/brand";
import {
  COMPARE_COLUMNS,
  COMPARE_MATRIX,
  COMPARE_VENDORS,
  FAQ,
} from "@/lib/site-content";
import { cn } from "@/lib/utils";

/** Matrix cell — yes/no render as icons, anything else as short freeform text. */
function Cell({ value, highlight }: { value: string; highlight?: boolean }) {
  if (value === "yes") {
    return (
      <Check
        className={cn("mx-auto size-5", highlight ? "text-primary" : "text-emerald-500")}
      />
    );
  }
  if (value === "no") {
    return <Minus className="mx-auto size-5 text-muted-foreground/40" />;
  }
  return (
    <span className="text-xs font-medium text-muted-foreground">{value}</span>
  );
}

/** FAQPage structured data so the objection answers can win rich results. */
function FaqJsonLd() {
  const json = {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: FAQ.map((item) => ({
      "@type": "Question",
      name: item.q,
      acceptedAnswer: { "@type": "Answer", text: item.a },
    })),
  };
  return (
    <script
      type="application/ld+json"
      // Prerendered to static HTML; this is our own content, not user input.
      dangerouslySetInnerHTML={{ __html: JSON.stringify(json) }}
    />
  );
}

/**
 * Deep comparison / FAQ page (§XXVII.3, T482).
 *
 * Answers the objections a team evaluating Turing against Algolia,
 * Elasticsearch or a DIY LangChain stack will raise — a fair capability
 * matrix, honest "where they win / where Turing fits" cards, and an
 * objection-answering FAQ (with FAQPage JSON-LD). Mirrors the MigratePage
 * shell: dark hero, dependency-free routing via plain <a href> full loads.
 */
export function ComparePage() {
  return (
    <div className="flex min-h-screen flex-col">
      <FaqJsonLd />
      <SiteNav />
      <main className="flex-1">
        {/* hero */}
        <section className="relative isolate overflow-hidden bg-[#080b18] py-16 text-[#f2f4fb] lg:py-20">
          <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
          <div className="animate-orb-a pointer-events-none absolute -top-40 -left-20 -z-10 h-[460px] w-[460px] rounded-full bg-[radial-gradient(circle,#4169E1,transparent_70%)] opacity-40 blur-[70px]" />
          <Container>
            <a
              href="/"
              className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#aeb7d4] transition-colors hover:text-white"
            >
              <ArrowLeft className="size-4" /> Back to overview
            </a>
            <p className="mt-6 text-xs font-extrabold tracking-[0.12em] text-[#7f8bbd] uppercase">
              How Turing compares
            </p>
            <h1 className="mt-3 max-w-3xl text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-4xl lg:text-5xl">
              Turing vs Algolia, Elasticsearch & DIY RAG
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-relaxed text-[#aeb7d4]">
              An honest look at where each alternative wins — and where a
              self-hosted, open-source platform that unifies search, RAG and
              agents fits instead. No straw men: Viglet Turing ES runs on the
              engine you already operate, keeps your data in your infrastructure,
              and is Apache 2.0.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Button asChild size="lg">
                <a href="/#playground">
                  Try it live <ArrowRight className="size-4" />
                </a>
              </Button>
              <Button
                asChild
                size="lg"
                variant="outline"
                className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white"
              >
                <a href="/migrate">Switching from Algolia or Elastic?</a>
              </Button>
            </div>
          </Container>
        </section>

        {/* deep capability matrix */}
        <section className="py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                One platform, measured against the alternatives
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                A hosted SaaS locks your data in someone else's cloud. A raw
                engine or a DIY stack is months of application glue. Turing gives
                you production search and auditable AI in one deployable product.
              </p>
            </div>
            <TableScroll>
              <table className="w-full min-w-[760px] border-collapse text-sm">
                <thead>
                  <tr>
                    <th className="sticky left-0 z-10 w-[150px] bg-background p-3 text-left font-semibold sm:w-2/5"></th>
                    <th className="rounded-t-xl bg-primary/10 p-3 text-center font-extrabold text-primary">
                      {COMPARE_COLUMNS.turing}
                    </th>
                    <th className="p-3 text-center font-semibold text-muted-foreground">
                      {COMPARE_COLUMNS.algolia}
                    </th>
                    <th className="p-3 text-center font-semibold text-muted-foreground">
                      {COMPARE_COLUMNS.elastic}
                    </th>
                    <th className="p-3 text-center font-semibold text-muted-foreground">
                      {COMPARE_COLUMNS.diy}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {COMPARE_MATRIX.map((row, i) => (
                    <tr key={row.capability} className="border-t border-border">
                      <td className="sticky left-0 z-10 bg-background p-3 font-medium">
                        {row.capability}
                      </td>
                      <td
                        className={cn(
                          "p-3 text-center bg-primary/5",
                          i === COMPARE_MATRIX.length - 1 && "rounded-b-xl"
                        )}
                      >
                        <Cell value={row.turing} highlight />
                      </td>
                      <td className="p-3 text-center">
                        <Cell value={row.algolia} />
                      </td>
                      <td className="p-3 text-center">
                        <Cell value={row.elastic} />
                      </td>
                      <td className="p-3 text-center">
                        <Cell value={row.diy} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableScroll>
            <p className="mt-4 text-center text-xs text-muted-foreground">
              High-level comparison as of 2026 — verify current vendor
              capabilities. “DIY” = assembling LangChain/LlamaIndex with a vector
              database yourself.
            </p>
          </Container>
        </section>

        {/* honest per-competitor cards */}
        <section className="border-t border-border bg-muted/40 py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                Where each alternative wins — and where Turing fits
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                The fair version. Each of these is a strong tool; the question is
                which trade-offs match your team.
              </p>
            </div>
            <div className="grid gap-5 md:grid-cols-3">
              {COMPARE_VENDORS.map((v) => (
                <Card key={v.name} className="h-full gap-0 p-6">
                  <h3 className="text-lg font-extrabold">{v.name}</h3>
                  <p className="mt-1 text-sm text-muted-foreground">{v.tagline}</p>
                  <p className="mt-5 text-xs font-bold tracking-[0.08em] text-muted-foreground uppercase">
                    Where it shines
                  </p>
                  <p className="mt-1.5 text-sm leading-relaxed">{v.shines}</p>
                  <p className="mt-5 text-xs font-bold tracking-[0.08em] text-primary uppercase">
                    Where Turing fits
                  </p>
                  <p className="mt-1.5 text-sm leading-relaxed">{v.turingFit}</p>
                </Card>
              ))}
            </div>
          </Container>
        </section>

        {/* objection-answering FAQ */}
        <section className="py-16 lg:py-20">
          <Container className="max-w-3xl">
            <div className="mb-10 text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                Frequently asked questions
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                The objections worth raising before you commit — answered
                straight.
              </p>
            </div>
            <dl className="divide-y divide-border">
              {FAQ.map((item) => (
                <div key={item.q} className="py-6">
                  <dt className="text-base font-bold">{item.q}</dt>
                  <dd className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {item.a}
                  </dd>
                </div>
              ))}
            </dl>
          </Container>
        </section>

        {/* CTA */}
        <section className="border-t border-border bg-muted/40 py-16">
          <Container>
            <Card className="mx-auto max-w-3xl gap-0 border-primary/20 bg-primary/5 p-8 text-center sm:p-10">
              <h2 className="text-2xl font-extrabold tracking-tight">
                See it on your own content
              </h2>
              <p className="mx-auto mt-3 max-w-xl text-base leading-relaxed text-muted-foreground">
                Self-host under Apache 2.0, bring your own LLM and search engine,
                and keep every document in your infrastructure.
              </p>
              <div className="mt-8 flex flex-wrap justify-center gap-3">
                <Button asChild size="lg">
                  <a href={LINKS.docs}>
                    Get started <ArrowRight className="size-4" />
                  </a>
                </Button>
                <Button asChild size="lg" variant="outline">
                  <a href="/#playground">Try it live</a>
                </Button>
              </div>
            </Card>
          </Container>
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}
