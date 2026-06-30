import { type ReactNode } from "react";
import { ArrowLeft, ArrowRight, Check } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { NativeTools } from "@/components/sections/native-tools";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Container } from "@/components/brand";
import {
  FEATURE_DETAILS,
  FEATURE_SLUGS,
  type FeatureDetail,
} from "@/lib/site-content";

// Lightweight, dependency-free, SSR-safe syntax highlight. Deterministic string
// → token mapping, so the server-rendered markup and the client hydration match
// exactly (no react-syntax-highlighter / shiki dependency, which the dependency-
// light site avoids). Covers the few languages used in the snippets below
// (bash, json, tsx, yaml) well enough to read as colored code.
// The `(?<!:)` before `//` keeps URLs (https://…) from being treated as a
// line comment; `#` covers bash/yaml comments.
const SYNTAX_RE =
  /(#[^\n]*|(?<!:)\/\/[^\n]*)|("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')|\b(true|false|null|const|let|var|function|return|import|from|export|async|await|new)\b|(\b\d+(?:\.\d+)?\b)/g;

// index 0..3 mirrors the regex capture groups: comment, string, keyword, number.
const TOKEN_CLASS = [
  "text-slate-500 italic",
  "text-emerald-300",
  "text-sky-300",
  "text-amber-300",
];

function highlightCode(code: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  let last = 0;
  let key = 0;
  SYNTAX_RE.lastIndex = 0;
  let m: RegExpExecArray | null = SYNTAX_RE.exec(code);
  while (m !== null) {
    if (m.index > last) nodes.push(code.slice(last, m.index));
    let gi = 3;
    if (m[1]) gi = 0;
    else if (m[2]) gi = 1;
    else if (m[3]) gi = 2;
    nodes.push(
      <span key={key++} className={TOKEN_CLASS[gi]}>
        {m[0]}
      </span>
    );
    last = m.index + m[0].length;
    m = SYNTAX_RE.exec(code);
  }
  if (last < code.length) nodes.push(code.slice(last));
  return nodes;
}

function CodeBlock({ caption, code }: Readonly<{ caption: string; code: string }>) {
  return (
    <figure className="overflow-hidden rounded-xl border border-border bg-[#0d1020] text-[#e6e9f5] shadow-lg">
      <figcaption className="border-b border-white/10 px-4 py-2 text-xs font-semibold text-[#9aa3c7]">
        {caption}
      </figcaption>
      <pre className="overflow-x-auto px-4 py-4 text-sm leading-relaxed">
        <code className="font-mono">{highlightCode(code)}</code>
      </pre>
    </figure>
  );
}

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
          <Container className="grid gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:items-start">
            <ul className="space-y-6">
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
              <div className="lg:sticky lg:top-24">
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
