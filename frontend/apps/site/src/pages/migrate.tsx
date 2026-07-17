import { ArrowLeft, ArrowRight, Check, ShieldCheck } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { CodeBlock } from "@/components/code-block";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Container, LINKS, TableScroll } from "@/components/brand";
import {
  MIGRATION_CLI_CODE,
  MIGRATION_CLI_FEATURES,
  MIGRATION_COLUMNS,
  MIGRATION_MAP,
  MIGRATION_OUTCOMES,
} from "@/lib/site-content";

/**
 * "Switching from Algolia / Elasticsearch?" migration landing (§XXVII.3, T479).
 *
 * A sales page, not a runbook: the how-to lives in the docs. It leads with the
 * one thing that removes the objection ("switching is one command"), shows the
 * team keeps everything it has (concept map) and gains more (outcomes), and
 * closes on the differentiator — the data never leaves their infrastructure.
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
              Switching from Algolia or Elasticsearch is one command
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-relaxed text-[#aeb7d4]">
              Keep the faceted, typo-tolerant search your team relies on — add
              RAG, cited chat and agents — and bring it all home to your own
              infrastructure. No rewrite, no lock-in, no per-record bill.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Button asChild size="lg">
                <a href={`${LINKS.docs}migration`} target="_blank" rel="noreferrer">
                  Read the migration guide <ArrowRight className="size-4" />
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

        {/* the one command + trust signals */}
        <section className="py-16 lg:py-20">
          <Container className="grid gap-10 lg:grid-cols-2 lg:items-center">
            <div>
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                Point it at your live index
              </h2>
              <p className="mt-4 text-base leading-relaxed text-muted-foreground">
                <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-sm">
                  turing migrate
                </code>{" "}
                reads your Elasticsearch mappings or Algolia settings, turns them
                into a Turing schema, and streams the records across — in a single
                step. And it's cautious by design:
              </p>
              <ul className="mt-6 space-y-4">
                {MIGRATION_CLI_FEATURES.map((f) => (
                  <li key={f.title} className="flex gap-3">
                    <Check className="mt-0.5 size-5 shrink-0 text-emerald-500" />
                    <div>
                      <p className="text-sm font-bold">{f.title}</p>
                      <p className="mt-0.5 text-sm leading-relaxed text-muted-foreground">
                        {f.body}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
            <CodeBlock
              caption={MIGRATION_CLI_CODE.caption}
              code={MIGRATION_CLI_CODE.code}
            />
          </Container>
        </section>

        {/* concept mapping — you keep everything */}
        <section className="border-t border-border bg-muted/40 py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                Everything you rely on carries over
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                The concepts map almost one-to-one. Where a hosted feature is
                paid, beta or vendor-locked, Turing ships the equivalent in the
                box — on the search engine you already operate.
              </p>
            </div>
            <TableScroll fadeClassName="from-muted">
              <table className="w-full min-w-[720px] border-collapse text-sm">
                <thead>
                  <tr>
                    <th className="sticky left-0 z-10 w-[140px] bg-background p-3 text-left font-semibold text-muted-foreground sm:w-1/5">
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
                      <td className="sticky left-0 z-10 bg-background p-3 font-semibold">
                        {row.concept}
                      </td>
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
            </TableScroll>
            <p className="mt-4 text-center text-xs text-muted-foreground">
              High-level mapping as of 2026 — verify current vendor capabilities.
            </p>
          </Container>
        </section>

        {/* what you gain */}
        <section className="py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                And you come out ahead
              </h2>
            </div>
            <div className="grid gap-6 md:grid-cols-3">
              {MIGRATION_OUTCOMES.map((o) => (
                <div
                  key={o.title}
                  className="rounded-2xl border border-border bg-card p-6"
                >
                  <h3 className="text-base font-bold">{o.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {o.body}
                  </p>
                </div>
              ))}
            </div>
          </Container>
        </section>

        {/* your data stays in your infra */}
        <section className="border-t border-border py-16 lg:py-20">
          <Container>
            <Card className="mx-auto max-w-3xl gap-0 border-primary/20 bg-primary/5 p-8 text-center sm:p-10">
              <span className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                <ShieldCheck className="size-6" />
              </span>
              <h2 className="mt-5 text-2xl font-extrabold tracking-tight">
                And your data never leaves your network
              </h2>
              <p className="mx-auto mt-3 max-w-xl text-base leading-relaxed text-muted-foreground">
                A hosted SaaS keeps your index — and increasingly your users'
                queries — in someone else's cloud. Turing runs on your own
                hardware: documents, embeddings and query logs stay put.
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
                  <a href={`${LINKS.docs}migration`} target="_blank" rel="noreferrer">
                    Read the migration guide <ArrowRight className="size-4" />
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
