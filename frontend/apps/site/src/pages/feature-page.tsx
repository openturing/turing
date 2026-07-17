import { ArrowLeft, ArrowRight, Check } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { NativeTools } from "@/components/sections/native-tools";
import { CodeBlock } from "@/components/code-block";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Container } from "@/components/brand";
import {
  FEATURE_DETAILS,
  FEATURE_SLUGS,
  type FeatureDetail,
} from "@/lib/site-content";

export function FeaturePage({ detail }: Readonly<{ detail: FeatureDetail }>) {
  const others = FEATURE_SLUGS.filter((s) => s !== detail.slug).map(
    (s) => FEATURE_DETAILS[s]
  );

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
              {detail.eyebrow}
            </p>
            <h1 className="mt-3 max-w-3xl text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-4xl lg:text-5xl">
              {detail.title}
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-relaxed text-[#aeb7d4]">
              {detail.lede}
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Button asChild size="lg">
                <a href={detail.docs.href} target="_blank" rel="noreferrer">
                  {detail.docs.label} <ArrowRight className="size-4" />
                </a>
              </Button>
              {detail.demo && (
                <Button
                  asChild
                  size="lg"
                  variant="outline"
                  className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white"
                >
                  <a href={detail.demo.href}>{detail.demo.label}</a>
                </Button>
              )}
            </div>
          </Container>
        </section>

        {/* capability sections */}
        <section className="py-16 lg:py-20">
          <Container className="grid grid-cols-1 gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:items-start">
            <ul className="min-w-0 space-y-6">
              {detail.sections.map((s) => (
                <li key={s.title} className="flex gap-3.5">
                  <span className="mt-1 flex size-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                    <Check className="size-4" />
                  </span>
                  <div>
                    <h2 className="text-base font-bold">{s.title}</h2>
                    <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                      {s.body}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
            {detail.code && (
              <div className="min-w-0 lg:sticky lg:top-24">
                <CodeBlock caption={detail.code.caption} code={detail.code.code} />
              </div>
            )}
          </Container>
        </section>

        {/* native tools (Automate it only) */}
        {detail.showNativeTools && <NativeTools />}

        {/* explore the rest of the platform */}
        <section className="border-t border-border py-16">
          <Container>
            <h2 className="mb-8 text-center text-2xl font-extrabold tracking-tight">
              Explore the rest of the platform
            </h2>
            <div className="grid gap-5 sm:grid-cols-3">
              {others.map((o) => (
                <a key={o.slug} href={`/features/${o.slug}`} className="group">
                  <Card className="h-full gap-0 p-6 transition-all duration-200 hover:-translate-y-1 hover:border-primary/50 hover:shadow-lg hover:shadow-primary/10">
                    <span className="text-xs font-extrabold tracking-[0.1em] text-muted-foreground uppercase">
                      {o.eyebrow}
                    </span>
                    <h3 className="mt-2 text-base font-bold">{o.title}</h3>
                    <span className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-primary">
                      Learn more
                      <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
                    </span>
                  </Card>
                </a>
              ))}
            </div>
          </Container>
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}
