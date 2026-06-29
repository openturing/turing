import { TuringD2Diagram, TuringMarkdown } from "@viglet/turing-react-sdk";
import rehypeHighlight from "rehype-highlight";
import remarkGfm from "remark-gfm";

/**
 * Markdown segment renderer for {@link TuringRichContent}. Plugs the app's own
 * `react-markdown` setup (GFM + syntax highlight) into the headless splitter —
 * the library ships no markdown dependency of its own.
 */
export function MarkdownBlock({ text }: Readonly<{ text: string }>) {
  return (
    <TuringMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeHighlight]}>
      {text}
    </TuringMarkdown>
  );
}

/**
 * ```d2 diagram segment renderer (T297). The D2 engine (`@terrastruct/d2`) is
 * lazy-imported inside `TuringD2Diagram`, so it is code-split and only loaded
 * when an answer actually contains a diagram.
 */
export function D2Block({ code }: Readonly<{ code: string }>) {
  return (
    <TuringD2Diagram
      code={code}
      className="my-2 overflow-x-auto rounded-lg border border-border/60 bg-card p-2"
    />
  );
}
