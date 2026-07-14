import { ArrowLeft, ArrowRight, Check, Minus } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { ContentFitDemo } from "@/components/sections/content-fit-demo";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Container, LINKS } from "@/components/brand";
import {
  PERSONA_COLUMNS,
  PERSONA_FAQ,
  PERSONA_MATRIX,
  PERSONA_STEPS,
  PERSONA_SURFACES,
  PERSONA_VOICES,
} from "@/lib/site-content";
import { cn } from "@/lib/utils";

const PERSONA_BOOK_URL = "https://docs.viglet.org/turing/persona-book";

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
 * Structured data (@graph): a BreadcrumbList (Home › Personas), a
 * SoftwareApplication describing the feature, and the FAQPage so the objection
 * answers can win rich results. All prerendered to static HTML.
 */
function PersonaJsonLd() {
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
            name: "AI Personas",
            item: "https://turing.viglet.org/persona",
          },
        ],
      },
      {
        "@type": "SoftwareApplication",
        name: "Viglet Turing ES — AI Personas (brand voice)",
        applicationCategory: "BusinessApplication",
        operatingSystem: "Any (self-hosted, Docker)",
        url: "https://turing.viglet.org/persona",
        description:
          "Give your AI agents a governed brand voice. Personas turn a generic LLM into your most on-brand, most consistent representative — required and forbidden vocabulary enforced in two layers, live brand facts via MCP, audience content-fit scoring, Persona Match and synthetic user research. Self-hosted, open source (Apache 2.0).",
        offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
        license: "https://www.apache.org/licenses/LICENSE-2.0",
      },
      {
        "@type": "FAQPage",
        mainEntity: PERSONA_FAQ.map((item) => ({
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
 * Persona solution landing (/persona) — "Give your AI a voice that sounds like
 * your company".
 *
 * Reframes prompt-engineering-per-agent as a reusable, governable brand voice:
 * define once, attach to any agent, enforce vocabulary in two layers, and
 * measure whether content fits its audience. Mirrors the AEM/Compare shell:
 * dark hero, dependency-free routing via plain <a href> full loads, FAQPage
 * JSON-LD, and a live content-fit demo as the centerpiece proof. The long-form
 * companion is "The Persona Book" in the docs.
 */
export function PersonaPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <PersonaJsonLd />
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
              Personas — brand voice for AI
            </p>
            <h1 className="mt-3 max-w-3xl text-3xl font-extrabold leading-[1.1] tracking-tight sm:text-4xl lg:text-5xl">
              Your customer doesn't want to talk to an LLM. They want to talk to
              your company.
            </h1>
            <p className="mt-5 max-w-2xl text-lg leading-relaxed text-[#aeb7d4]">
              A generic AI voice is indistinguishable from the next ten companies
              that asked the same model the same thing — and it quotes prices you
              never approved. A Persona is the voice profile of your AI: define it
              once, attach it to any agent, and every reply sounds like your most
              senior, most on-brand representative. Swap the LLM, swap the tools —
              the voice stays. Open source, Apache 2.0.
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
                <a href={PERSONA_BOOK_URL} target="_blank" rel="noreferrer">
                  Read the Persona Book
                </a>
              </Button>
            </div>
          </Container>
        </section>

        {/* live content-fit demo — the centerpiece proof */}
        <section className="py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <p className="text-xs font-extrabold tracking-[0.12em] text-primary uppercase">
                Live — a persona as an audience
              </p>
              <h2 className="mt-3 text-2xl font-extrabold tracking-tight sm:text-3xl">
                Does this content fit its reader?
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                A persona isn't only a voice — it can also be a reader you measure
                content against. Pick an audience, paste some text, and get a
                fit verdict: a readability score plus a text-grounded AI review
                that flags the exact spans that miss, each with a rewrite
                suggestion. This runs live against the demo backend.
              </p>
            </div>
            <div className="mx-auto max-w-2xl">
              <ContentFitDemo />
            </div>
          </Container>
        </section>

        {/* same question, three voices */}
        <section className="border-t border-border bg-muted/40 py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <p className="text-xs font-extrabold tracking-[0.12em] text-primary uppercase">
                One model, three voices
              </p>
              <h2 className="mt-3 text-2xl font-extrabold tracking-tight sm:text-3xl">
                Same question — “is the annual plan worth it?”
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                The same AI, the same model, the same question. Only the persona
                changes — and with it, how your company sounds.
              </p>
            </div>
            <div className="grid gap-5 md:grid-cols-3">
              {PERSONA_VOICES.map((v) => (
                <Card key={v.style} className="flex h-full flex-col gap-0 p-6">
                  <h3 className="text-base font-bold">{v.style}</h3>
                  <p className="mt-1 text-xs font-semibold tracking-wide text-primary">
                    {v.config}
                  </p>
                  <p className="mt-4 flex-1 text-sm leading-relaxed text-muted-foreground">
                    {v.answer}
                  </p>
                  <p className="mt-4 border-t border-border pt-3 text-xs text-muted-foreground/80">
                    {v.use}
                  </p>
                </Card>
              ))}
            </div>
          </Container>
        </section>

        {/* how a persona reaches production */}
        <section className="py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-12 max-w-2xl text-center">
              <p className="text-xs font-extrabold tracking-[0.12em] text-primary uppercase">
                How it works
              </p>
              <h2 className="mt-3 text-2xl font-extrabold tracking-tight sm:text-3xl">
                Define once — govern everywhere
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                A persona is decoupled from the model and the tools, so it's the
                one place your brand voice lives.
              </p>
            </div>
            <ol className="mx-auto grid max-w-5xl gap-8 sm:grid-cols-2">
              {PERSONA_STEPS.map((s) => (
                <li key={s.step} className="flex gap-3.5">
                  <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-extrabold text-primary">
                    {s.step}
                  </span>
                  <div className="min-w-0">
                    <h3 className="text-base font-bold">{s.title}</h3>
                    <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                      {s.body}
                    </p>
                  </div>
                </li>
              ))}
            </ol>
          </Container>
        </section>

        {/* persona surfaces — more than a system prompt */}
        <section className="border-t border-border py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                Far more than a system prompt
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                A Persona is something you operate directly — a voice, an
                audience, and a research participant, all reusable.
              </p>
            </div>
            <div className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
              {PERSONA_SURFACES.map((d) => (
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

        {/* capability matrix */}
        <section className="border-t border-border bg-muted/40 py-16 lg:py-20">
          <Container>
            <div className="mx-auto mb-10 max-w-2xl text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                Why not just write a good prompt?
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                You can get part-way with prompt engineering. Reuse, enforcement
                and measurement are where a governed Persona pulls ahead.
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[680px] border-collapse text-sm">
                <thead>
                  <tr>
                    <th className="w-2/5 p-3 text-left font-semibold"></th>
                    <th className="rounded-t-xl bg-primary/10 p-3 text-center font-extrabold text-primary">
                      {PERSONA_COLUMNS.turing}
                    </th>
                    <th className="p-3 text-center font-semibold text-muted-foreground">
                      {PERSONA_COLUMNS.prompt}
                    </th>
                    <th className="p-3 text-center font-semibold text-muted-foreground">
                      {PERSONA_COLUMNS.chatbot}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {PERSONA_MATRIX.map((row, i) => (
                    <tr key={row.capability} className="border-t border-border">
                      <td className="p-3 font-medium">{row.capability}</td>
                      <td
                        className={cn(
                          "p-3 text-center bg-primary/5",
                          i === PERSONA_MATRIX.length - 1 && "rounded-b-xl"
                        )}
                      >
                        <Cell value={row.turing} highlight />
                      </td>
                      <td className="p-3 text-center">
                        <Cell value={row.prompt} />
                      </td>
                      <td className="p-3 text-center">
                        <Cell value={row.chatbot} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="mt-4 text-center text-xs text-muted-foreground">
              “Prompt engineering” = hand-written system prompts maintained per
              agent; “off-the-shelf chatbot” = a hosted assistant with a single
              system prompt. High-level, as of 2026.
            </p>
          </Container>
        </section>

        {/* objection-answering FAQ */}
        <section className="py-16 lg:py-20">
          <Container className="max-w-3xl">
            <div className="mb-10 text-center">
              <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">
                Teams ask us
              </h2>
              <p className="mt-3 text-base leading-relaxed text-muted-foreground">
                The questions worth raising before you give your AI a brand voice
                — answered straight.
              </p>
            </div>
            <dl className="divide-y divide-border">
              {PERSONA_FAQ.map((item) => (
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
        <section className="border-t border-border pb-16">
          <Container>
            <Card className="mx-auto mt-16 max-w-3xl gap-0 border-primary/20 bg-primary/5 p-8 text-center sm:p-10">
              <h2 className="text-2xl font-extrabold tracking-tight">
                Give every agent one voice — yours
              </h2>
              <p className="mx-auto mt-3 max-w-xl text-base leading-relaxed text-muted-foreground">
                Self-host under Apache 2.0, define a persona in the admin UI, and
                attach it to any AI Agent. The complete, didactic walkthrough —
                with a worked example for every feature — lives in the Persona
                Book.
              </p>
              <div className="mt-8 flex flex-wrap justify-center gap-3">
                <Button asChild size="lg">
                  <a href={PERSONA_BOOK_URL} target="_blank" rel="noreferrer">
                    Read the Persona Book <ArrowRight className="size-4" />
                  </a>
                </Button>
                <Button asChild size="lg" variant="outline">
                  <a href={LINKS.docs}>Get started</a>
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
