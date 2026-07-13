import { ArrowLeft, ArrowRight, Check, ShieldCheck } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Container, LINKS } from "@/components/brand";
import { SECURITY_POINTS } from "@/lib/site-content";
import { cn } from "@/lib/utils";

/**
 * Security & data residency (/security).
 *
 * Turns the platform's strongest, most under-sold argument — the data never
 * leaves your infrastructure — into a dedicated sales asset. Answers the
 * enterprise buyer's SOC 2 / GDPR reflex with the honest self-hosted counter:
 * you don't have to trust our compliance because the data never leaves to begin
 * with. Mirrors the Compare/Migrate/AEM shell (dark hero, dependency-free
 * routing via plain <a href> full loads).
 */
export function SecurityPage() {
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
              Security &amp; data residency
            </p>
            <h1 className="mt-3 max-w-3xl text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-4xl lg:text-5xl">
              The safest place for your content is your own network
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-relaxed text-[#aeb7d4]">
              A search SaaS asks you to trust its compliance badges with your
              content and your users' queries. Viglet Turing ES is self-hosted:
              the data never leaves your infrastructure to begin with — so
              security inherits the controls you already run. Open source, any
              LLM, your region, your rules.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Button asChild size="lg">
                <a href={`${LINKS.docs}category/deploy-operate`} target="_blank" rel="noreferrer">
                  Deployment &amp; security docs <ArrowRight className="size-4" />
                </a>
              </Button>
              <Button
                asChild
                size="lg"
                variant="outline"
                className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white"
              >
                <a href="/#self-host">Self-host in one command</a>
              </Button>
            </div>
          </Container>
        </section>

        {/* the core argument — SaaS trust vs self-hosted */}
        <section className="py-16 lg:py-20">
          <Container>
            <Card className="mx-auto max-w-3xl gap-0 border-primary/20 bg-primary/5 p-8 text-center sm:p-10">
              <span className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
                <ShieldCheck className="size-6" />
              </span>
              <h2 className="mt-5 text-2xl font-extrabold tracking-tight">
                Don't trust a vendor's boundary — remove it
              </h2>
              <p className="mx-auto mt-3 max-w-xl text-base leading-relaxed text-muted-foreground">
                SOC 2 and GDPR paperwork exist because a hosted vendor holds your
                data. When you self-host, that boundary disappears: your content,
                embeddings and query logs stay inside the account, region and
                controls you already operate — nothing to transfer, nothing to
                audit on someone else's word.
              </p>
            </Card>
          </Container>
        </section>

        {/* security points grid */}
        <section className="border-t border-border bg-muted/40 py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                How Turing keeps you in control
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                Concrete, defensible guarantees — not compliance theater.
              </p>
            </div>
            <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
              {SECURITY_POINTS.map((p) => (
                <Card key={p.title} className="h-full gap-0 p-6">
                  <span
                    className={cn(
                      "flex size-11 items-center justify-center rounded-2xl bg-gradient-to-br text-white shadow-md",
                      p.iconClass
                    )}
                  >
                    <p.icon className="size-5" />
                  </span>
                  <h3 className="mt-5 text-base font-bold">{p.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {p.body}
                  </p>
                </Card>
              ))}
            </div>
          </Container>
        </section>

        {/* honest scope note + CTA */}
        <section className="py-16 lg:py-20">
          <Container>
            <div className="mx-auto max-w-3xl">
              <h2 className="text-2xl font-extrabold tracking-tight">
                What self-hosting means for your compliance
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                Turing is software you run, not a service we operate — so it has
                no SOC 2 report of its own to hand you, and it doesn't need one.
                Deployed inside your environment, it runs under the certifications,
                network controls, logging and data-residency guarantees your
                infrastructure already carries. You keep the audit trail; we keep
                the source open so your team can verify every claim on this page.
              </p>
              <ul className="mt-6 grid gap-2.5 sm:grid-cols-2">
                {[
                  "Self-hosted under Apache 2.0",
                  "Content, embeddings & query logs stay on-prem",
                  "Bring your own LLM — or run fully local",
                  "No telemetry, no phone-home",
                ].map((item) => (
                  <li key={item} className="flex items-start gap-2 text-sm">
                    <Check className="mt-0.5 size-4 shrink-0 text-emerald-500" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
              <div className="mt-8 flex flex-wrap gap-3">
                <Button asChild size="lg">
                  <a href="/aem-search">
                    See it for AEM <ArrowRight className="size-4" />
                  </a>
                </Button>
                <Button asChild size="lg" variant="outline">
                  <a href="/compare">Compare the alternatives</a>
                </Button>
              </div>
            </div>
          </Container>
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}
