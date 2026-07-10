import type { Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  TuringRichContent,
  TuringMarkdown,
  TuringD2Diagram,
  type TuringMarkdownSegmentProps,
  type TuringD2Props,
} from "@viglet/turing-react-ui";
import { DEMO_ORIGIN } from "@/lib/turing-demo";

// Dogfooding: the demo renders assistant answers with the SAME components the
// product ships (@viglet/turing-react-ui) — TuringRichContent splits the reply
// into markdown / ```html / ```d2 segments and dispatches each. Prose goes
// through TuringMarkdown (react-markdown under the hood, with the product's
// sandbox: URL handling); ```html renders in the sandboxed iframe; ```d2
// renders as a diagram via TuringD2Diagram (T632). The D2 engine
// (@terrastruct/d2) is lazy-imported inside the shared component, so it is
// code-split into its own chunk and only fetched when an answer actually
// contains a ```d2 block — heavy optional dep, zero cost otherwise.

/** Tag-level styling for the markdown the assistant emits. */
const MD_COMPONENTS: Components = {
  p: ({ node, ...p }) => <p className="leading-relaxed" {...p} />,
  ol: ({ node, ...p }) => (
    <ol className="list-decimal space-y-1 pl-5" {...p} />
  ),
  ul: ({ node, ...p }) => <ul className="list-disc space-y-1 pl-5" {...p} />,
  li: ({ node, ...p }) => <li className="leading-relaxed" {...p} />,
  a: ({ node, ...p }) => (
    <a
      className="text-primary underline underline-offset-2"
      target="_blank"
      rel="noreferrer"
      {...p}
    />
  ),
  strong: ({ node, ...p }) => <strong className="font-semibold" {...p} />,
  h1: ({ node, ...p }) => <h3 className="mt-1 text-base font-bold" {...p} />,
  h2: ({ node, ...p }) => <h3 className="mt-1 text-base font-bold" {...p} />,
  h3: ({ node, ...p }) => <h4 className="mt-1 text-sm font-bold" {...p} />,
  blockquote: ({ node, ...p }) => (
    <blockquote className="border-l-2 border-primary/40 pl-3 italic" {...p} />
  ),
  code: ({ node, ...p }) => (
    <code
      className="rounded bg-background/70 px-1 py-0.5 font-mono text-[0.85em]"
      {...p}
    />
  ),
  pre: ({ node, ...p }) => (
    <pre
      className="my-1.5 overflow-x-auto rounded-md bg-background/70 p-2.5 font-mono text-[0.8em] leading-relaxed [&>code]:bg-transparent [&>code]:p-0"
      {...p}
    />
  ),
  table: ({ node, ...p }) => (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-[0.85em]" {...p} />
    </div>
  ),
  th: ({ node, ...p }) => (
    <th className="border border-border px-2 py-1 text-left font-semibold" {...p} />
  ),
  td: ({ node, ...p }) => (
    <td className="border border-border px-2 py-1" {...p} />
  ),
};

function SiteMarkdown({ text }: Readonly<TuringMarkdownSegmentProps>) {
  return (
    <TuringMarkdown
      components={MD_COMPONENTS}
      remarkPlugins={[remarkGfm]}
      baseUrl={DEMO_ORIGIN}
    >
      {text}
    </TuringMarkdown>
  );
}

/**
 * ```d2 diagram segment renderer (T632). Skins the headless TuringD2Diagram —
 * the D2 engine is lazy-imported inside it, so this stays code-split. While the
 * engine loads it shows the caption; if it can't compile (or the peer is
 * missing) it falls back to the diagram source, so nothing is lost.
 */
function SiteD2({ code }: Readonly<TuringD2Props>) {
  return (
    <TuringD2Diagram
      code={code}
      className="my-2 overflow-x-auto rounded-md border border-border bg-background/70 p-2.5"
      classNames={{
        diagram: "[&_svg]:h-auto [&_svg]:max-w-full",
        loading: "text-sm text-muted-foreground",
        error: "text-sm text-muted-foreground",
        code: "overflow-x-auto font-mono text-[0.8em] leading-relaxed",
      }}
      labels={{
        loading: "Rendering diagram…",
        error: "Could not render diagram",
        diagram: "Diagram",
      }}
    />
  );
}

/** Renders one assistant reply with the product's rich-content pipeline. */
export function RichAnswer({ content }: Readonly<{ content: string }>) {
  return (
    <TuringRichContent
      content={content}
      markdown={SiteMarkdown}
      d2={SiteD2}
      htmlSandbox={{ className: "my-2 w-full rounded-md border border-border" }}
      className="space-y-2 text-sm"
    />
  );
}
