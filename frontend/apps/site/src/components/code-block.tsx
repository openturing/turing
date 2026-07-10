import { type ReactNode } from "react";

// Lightweight, dependency-free, SSR-safe syntax highlight. Deterministic string
// → token mapping, so the server-rendered markup and the client hydration match
// exactly (no react-syntax-highlighter / shiki dependency, which the dependency-
// light site avoids). Covers the few languages used in the snippets across the
// site (bash, json, tsx, yaml) well enough to read as colored code.
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

export function CodeBlock({
  caption,
  code,
}: Readonly<{ caption: string; code: string }>) {
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
