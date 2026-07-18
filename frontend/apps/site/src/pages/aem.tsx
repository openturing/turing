import { ArrowLeft, ArrowRight, Check, Minus } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { CodeBlock } from "@/components/code-block";
import { WkndPlayground } from "@/components/sections/wknd-playground";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Container, LINKS, TableScroll } from "@/components/brand";
import {
  AEM_CODE,
  AEM_COLUMNS,
  AEM_DEPTH,
  AEM_FAQ,
  AEM_MATRIX,
  AEM_STEPS,
} from "@/lib/site-content";
import { cn } from "@/lib/utils";

/** Matrix cell — yes/no render as icons, anything else as short freeform text. */
function Cell({ value, highlight }: Readonly<{ value: string; highlight?: boolean }>) {
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
  return <span className="text-xs font-medium text-muted-foreground">{value}</span>;
}

/**
 * Structured data (@graph): a BreadcrumbList (Home › AEM Search), a
 * SoftwareApplication describing the solution, and the FAQPage so the objection
 * answers can win rich results. All prerendered to static HTML.
 */
function AemJsonLd() {
  const graph = {
    "@context": "https://schema.org",
    "@graph": [
      {
        "@type": "BreadcrumbList",
        itemListElement: [
          {
            "@type": "ListItem",
            position: 1,
            name: "Viglet Turing ES",
            item: "https://turing.viglet.org/",
          },
          {
            "@type": "ListItem",
            position: 2,
            name: "AEM Search",
            item: "https://turing.viglet.org/aem-search",
          },
        ],
      },
      {
        "@type": "SoftwareApplication",
        name: "Viglet Turing ES — AEM AI Search",
        applicationCategory: "BusinessApplication",
        operatingSystem: "Any (self-hosted, Docker)",
        url: "https://turing.viglet.org/aem-search",
        description:
          "AI search for Adobe Experience Manager: cited, streaming answers plus faceted keyword + semantic search over AEM 6.5 and AEM as a Cloud Service, indexed via the Dumont connector. Self-hosted, open source (Apache 2.0).",
        offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
        license: "https://www.apache.org/licenses/LICENSE-2.0",
      },
      {
        "@type": "FAQPage",
        mainEntity: AEM_FAQ.map((item) => ({
          "@type": "Question",
          name: item.q,
          acceptedAnswer: { "@type": "Answer", text: item.a },
        })),
      },
    ],
  };
  return (
    <script
      type="application/ld+json"
      // Prerendered to static HTML; this is our own content, not user input.
      dangerouslySetInnerHTML={{ __html: JSON.stringify(graph) }}
    />
  );
}

/**
 * AEM solution landing (/aem) — "AI search for Adobe Experience Manager".
 *
 * Reframes the AEM-native AI-search pitch (aem-ai-search.com): Turing gives AEM
 * the same cited, streaming AI answers, then goes deeper — cross-repository
 * search, agents/tools/skills/MCP, any LLM, any engine, self-hosted data
 * residency and Apache 2.0. Mirrors the Compare/Migrate shell: dark hero,
 * dependency-free routing via plain <a href> full loads, FAQPage JSON-LD.
 */
export function AemPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <AemJsonLd />
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
              Turing for Adobe Experience Manager
            </p>
            <h1 className="mt-3 max-w-3xl text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-4xl lg:text-5xl">
              AEM search, reimagined with AI — without the ceiling
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-relaxed text-[#aeb7d4]">
              The AEM-native pitch is “Cloud-Service-native.” That's also its
              limit. Viglet Turing ES gives AEM the same cited, streaming AI
              answers — then goes deeper: it searches beyond AEM, adds agents,
              tools, skills and MCP, runs on any LLM and any search engine, and
              keeps every page and query in your own infrastructure. Open source,
              Apache 2.0.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Button asChild size="lg">
                <a href="#demo">
                  Try it live <ArrowRight className="size-4" />
                </a>
              </Button>
              <Button
                asChild
                size="lg"
                variant="outline"
                className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white"
              >
                <a href={LINKS.aemConnector} target="_blank" rel="noreferrer">
                  AEM connector docs
                </a>
              </Button>
            </div>
          </Container>
        </section>

        {/* live WKND demo — the centerpiece proof */}
        <section id="demo" className="scroll-mt-24 py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <p className="text-xs font-extrabold tracking-[0.12em] text-primary uppercase">
                Live on real AEM content
              </p>
              <h2 className="mt-3 text-2xl font-extrabold tracking-tight sm:text-3xl">
                Search and ask Adobe's WKND site
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                This is the Adobe WKND reference site — its pages and assets
                indexed live via the Dumont AEM connector. Search it with facets
                and typo tolerance, or ask a question and get a grounded, cited
                answer. Every result and citation links back to the public WKND
                site. Same SDK, same corpus — two lenses.
              </p>
            </div>
            <WkndPlayground />
          </Container>
        </section>

        {/* how it plugs into AEM */}
        <section id="how" className="scroll-mt-24 border-t border-border py-16 lg:py-20">
          <Container className="grid gap-10 lg:grid-cols-2 lg:items-start">
            <div className="min-w-0">
              <p className="text-xs font-extrabold tracking-[0.12em] text-primary uppercase">
                How it plugs in
              </p>
              <h2 className="mt-3 text-2xl font-extrabold tracking-tight sm:text-3xl">
                Sits alongside AEM — no re-platforming
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                AEM stays your content source of truth. Turing connects, indexes
                into the engine you choose, and drops a cited-answer UI wherever
                your users are.
              </p>
              <ul className="mt-8 space-y-6">
                {AEM_STEPS.map((s) => (
                  <li key={s.step} className="flex gap-3.5">
                    <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-extrabold text-primary">
                      {s.step}
                    </span>
                    <div>
                      <h3 className="text-base font-bold">{s.title}</h3>
                      <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                        {s.body}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
            <div className="min-w-0 lg:sticky lg:top-24">
              <CodeBlock caption={AEM_CODE.caption} code={AEM_CODE.code} />
            </div>
          </Container>
        </section>

        {/* capability matrix */}
        <section id="matrix" className="scroll-mt-24 border-t border-border bg-muted/40 py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                Everything an AEM AI search does — and the parts it can't
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                Turing matches a Cloud-Service-native add-on on the core
                AI-answer story. The depth gap opens on everything around it.
              </p>
            </div>
            <TableScroll fadeClassName="from-muted">
              <table className="w-full min-w-[760px] border-collapse text-sm">
                <thead>
                  <tr>
                    <th className="sticky left-0 z-10 w-[150px] bg-background p-3 text-left font-semibold sm:w-2/5"></th>
                    <th className="rounded-t-xl bg-primary/10 p-3 text-center font-extrabold text-primary">
                      {AEM_COLUMNS.turing}
                    </th>
                    <th className="p-3 text-center font-semibold text-muted-foreground">
                      {AEM_COLUMNS.aemAi}
                    </th>
                    <th className="p-3 text-center font-semibold text-muted-foreground">
                      {AEM_COLUMNS.adobe}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {AEM_MATRIX.map((row, i) => (
                    <tr key={row.capability} className="border-t border-border">
                      <td className="sticky left-0 z-10 bg-background p-3 font-medium">
                        {row.capability}
                      </td>
                      <td
                        className={cn(
                          "p-3 text-center bg-primary/5",
                          i === AEM_MATRIX.length - 1 && "rounded-b-xl"
                        )}
                      >
                        <Cell value={row.turing} highlight />
                      </td>
                      <td className="p-3 text-center">
                        <Cell value={row.aemAi} />
                      </td>
                      <td className="p-3 text-center">
                        <Cell value={row.adobe} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableScroll>
            <p className="mt-4 text-center text-xs text-muted-foreground">
              High-level comparison as of 2026 — verify current vendor
              capabilities. “AEM-native AI add-on” = a Cloud-Service-native AI
              search product; “Adobe native search” = built-in Query
              Builder / Omnisearch.
            </p>
          </Container>
        </section>

        {/* depth cards */}
        <section id="depth" className="scroll-mt-24 py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                We have more depth
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                Where an AEM-only add-on reaches its ceiling, Turing keeps going.
              </p>
            </div>
            <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
              {AEM_DEPTH.map((d) => (
                <Card key={d.title} className="h-full gap-0 p-6">
                  <span
                    className={cn(
                      "flex size-11 items-center justify-center rounded-2xl bg-gradient-to-br text-white shadow-md",
                      d.iconClass
                    )}
                  >
                    <d.icon className="size-5" />
                  </span>
                  <h3 className="mt-5 text-base font-bold">{d.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {d.body}
                  </p>
                </Card>
              ))}
            </div>
          </Container>
        </section>

        {/* objection-answering FAQ */}
        <section id="faq" className="scroll-mt-24 border-t border-border bg-muted/40 py-16 lg:py-20">
          <Container className="max-w-3xl">
            <div className="mb-10 text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                AEM teams ask us
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                The objections worth raising before you add AI search to AEM —
                answered straight.
              </p>
            </div>
            <dl className="divide-y divide-border">
              {AEM_FAQ.map((item) => (
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
        <section id="cta" className="scroll-mt-24 py-16">
          <Container>
            <Card className="mx-auto max-w-3xl gap-0 border-primary/20 bg-primary/5 p-8 text-center sm:p-10">
              <h2 className="text-2xl font-extrabold tracking-tight">
                Add AI search to AEM — on your terms
              </h2>
              <p className="mx-auto mt-3 max-w-xl text-base leading-relaxed text-muted-foreground">
                Self-host under Apache 2.0, connect your AEM tier in a session,
                bring your own LLM and search engine, and keep every page and
                query in your infrastructure.
              </p>
              <div className="mt-8 flex flex-wrap justify-center gap-3">
                <Button asChild size="lg">
                  <a href={LINKS.docs}>
                    Get started <ArrowRight className="size-4" />
                  </a>
                </Button>
                <Button asChild size="lg" variant="outline">
                  <a href="/compare">Compare the alternatives</a>
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
