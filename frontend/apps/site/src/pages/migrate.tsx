import { ArrowLeft, ArrowRight, Check, ShieldCheck } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { CodeBlock } from "@/components/code-block";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Container, LINKS } from "@/components/brand";
import {
  MIGRATION_CODE,
  MIGRATION_COLUMNS,
  MIGRATION_MAP,
  MIGRATION_STEPS,
} from "@/lib/site-content";

/**
 * "Switching from Algolia / Elasticsearch?" migration landing (§XXVII.3, T479).
 *
 * Mirrors the FeaturePage shell (dark hero, dependency-free routing via plain
 * <a href> full loads) but leads with the differentiator that matters to a team
 * evaluating a switch: their data stays in their own infrastructure.
 */
export function MigratePage() {
  return (
    <div className="flex min-h-screen flex-col">
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
              Migrate to Turing
            </p>
            <h1 className="mt-3 max-w-3xl text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-4xl lg:text-5xl">
              Switching from Algolia or Elasticsearch?
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-relaxed text-[#aeb7d4]">
              Keep faceted, typo-tolerant search — and add RAG, cited chat and
              agents — without shipping your content to someone else's cloud.
              Viglet Turing ES is open source and self-hosted: your documents and
              embeddings stay in your own infrastructure.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Button asChild size="lg">
                <a href={`${LINKS.docs}migration`} target="_blank" rel="noreferrer">
                  Read the migration docs <ArrowRight className="size-4" />
                </a>
              </Button>
              <Button
                asChild
                size="lg"
                variant="outline"
                className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white"
              >
                <a href="/#playground">Try it live</a>
              </Button>
            </div>
          </Container>
        </section>

        {/* concept mapping table */}
        <section className="py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                What you know maps to what Turing does
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                The concepts carry over almost one-to-one. Where a hosted feature
                is paid, beta or vendor-locked, Turing ships the equivalent in the
                box — running on the search engine you already operate.
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] border-collapse text-sm">
                <thead>
                  <tr>
                    <th className="w-1/5 p-3 text-left font-semibold text-muted-foreground">
                      {MIGRATION_COLUMNS.concept}
                    </th>
                    <th className="p-3 text-left font-semibold text-muted-foreground">
                      {MIGRATION_COLUMNS.algolia}
                    </th>
                    <th className="p-3 text-left font-semibold text-muted-foreground">
                      {MIGRATION_COLUMNS.elastic}
                    </th>
                    <th className="rounded-t-xl bg-primary/10 p-3 text-left font-extrabold text-primary">
                      {MIGRATION_COLUMNS.turing}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {MIGRATION_MAP.map((row, i) => (
                    <tr key={row.concept} className="border-t border-border">
                      <td className="p-3 font-semibold">{row.concept}</td>
                      <td className="p-3 text-muted-foreground">{row.algolia}</td>
                      <td className="p-3 text-muted-foreground">{row.elastic}</td>
                      <td
                        className={
                          "bg-primary/5 p-3 font-medium" +
                          (i === MIGRATION_MAP.length - 1 ? " rounded-b-xl" : "")
                        }
                      >
                        {row.turing}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt-4 text-center text-xs text-muted-foreground">
              High-level mapping as of 2026 — verify current vendor capabilities.
            </p>
          </Container>
        </section>

        {/* import path */}
        <section className="border-t border-border bg-muted/40 py-16 lg:py-20">
          <Container className="grid gap-10 lg:grid-cols-[1fr_1fr] lg:items-start">
            <div>
              <p className="text-xs font-extrabold tracking-[0.12em] text-primary uppercase">
                The import path
              </p>
              <h2 className="mt-3 text-2xl font-extrabold tracking-tight sm:text-3xl">
                Three moves, not a rewrite
              </h2>
              <ul className="mt-8 space-y-6">
                {MIGRATION_STEPS.map((s) => (
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
            <div className="lg:sticky lg:top-24">
              <CodeBlock caption={MIGRATION_CODE.caption} code={MIGRATION_CODE.code} />
            </div>
          </Container>
        </section>

        {/* your data stays in your infra */}
        <section className="py-16 lg:py-20">
          <Container>
            <Card className="mx-auto max-w-3xl gap-0 border-primary/20 bg-primary/5 p-8 text-center sm:p-10">
              <span className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                <ShieldCheck className="size-6" />
              </span>
              <h2 className="mt-5 text-2xl font-extrabold tracking-tight">
                Your data stays in your infrastructure
              </h2>
              <p className="mx-auto mt-3 max-w-xl text-base leading-relaxed text-muted-foreground">
                A hosted SaaS keeps your index — and increasingly your users'
                queries — in someone else's cloud. Turing runs entirely on your
                own hardware: your documents, embeddings and query logs never
                leave your network. Apache 2.0, no per-record pricing, no lock-in.
              </p>
              <ul className="mx-auto mt-6 grid max-w-xl gap-2.5 text-left sm:grid-cols-2">
                {[
                  "Self-hosted under Apache 2.0",
                  "Content & embeddings stay on-prem",
                  "Bring your own LLM & search engine",
                  "No per-record or per-search billing",
                ].map((item) => (
                  <li key={item} className="flex items-start gap-2 text-sm">
                    <Check className="mt-0.5 size-4 shrink-0 text-emerald-500" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
              <div className="mt-8 flex flex-wrap justify-center gap-3">
                <Button asChild size="lg">
                  <a href="/features/search">
                    See enterprise search <ArrowRight className="size-4" />
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
