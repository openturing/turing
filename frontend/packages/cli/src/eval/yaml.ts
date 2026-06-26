/**
 * A small, dependency-free YAML reader covering the subset the eval format
 * uses: block mappings, block sequences, scalars (plain / single- / double-
 * quoted), inline flow sequences ({@code [a, "b", c]}), {@code #} comments and
 * blank lines. It is intentionally NOT a general YAML parser — anchors, multi-
 * document streams, block scalars ({@code |}/{@code >}), and flow mappings are
 * out of scope. Keeping it zero-dep matches the monorepo's package convention
 * (see {@code @viglet/turing-flow-dsl}) so the CLI builds with {@code tsc}
 * alone.
 *
 * <p>Unquoted scalars are coerced: {@code true}/{@code false} → boolean,
 * {@code null}/{@code ~}/empty → null, integer/float literals → number,
 * everything else stays a string. Quote a value to force it to a string.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

interface RawLine {
  indent: number;
  text: string;
  lineNo: number;
}

export class YamlError extends Error {
  constructor(message: string, lineNo?: number) {
    super(lineNo === undefined ? message : `${message} (line ${lineNo})`);
    this.name = "YamlError";
  }
}

/** Parses a YAML document into plain JS values. Returns {@code {}} when empty. */
export function parseYaml(text: string): unknown {
  const lines = tokenize(text);
  if (lines.length === 0) return {};
  const [value] = parseNode(lines, 0, lines[0]!.indent);
  return value;
}

function tokenize(text: string): RawLine[] {
  const out: RawLine[] = [];
  const rawLines = text.split(/\r?\n/);
  for (let i = 0; i < rawLines.length; i++) {
    const stripped = stripComment(rawLines[i]!).replace(/\s+$/, "");
    if (stripped.trim() === "" || stripped.trim() === "---") continue;
    const indent = stripped.length - stripped.replace(/^ +/, "").length;
    out.push({ indent, text: stripped.slice(indent), lineNo: i + 1 });
  }
  return out;
}

/** Cuts an unquoted trailing {@code #} comment, respecting quoted spans. */
function stripComment(line: string): string {
  let quote: '"' | "'" | null = null;
  for (let i = 0; i < line.length; i++) {
    const c = line[i]!;
    if (quote) {
      if (c === quote) quote = null;
    } else if (c === '"' || c === "'") {
      quote = c;
    } else if (c === "#" && (i === 0 || line[i - 1] === " " || line[i - 1] === "\t")) {
      return line.slice(0, i);
    }
  }
  return line;
}

function parseNode(lines: RawLine[], i: number, indent: number): [unknown, number] {
  const line = lines[i]!;
  if (isDash(line.text)) return parseSeq(lines, i, indent);
  if (findColon(line.text) !== -1) return parseMap(lines, i, indent);
  // a lone scalar line
  return [parseScalar(line.text), i + 1];
}

function isDash(text: string): boolean {
  return text === "-" || text.startsWith("- ");
}

function parseSeq(lines: RawLine[], start: number, indent: number): [unknown[], number] {
  const arr: unknown[] = [];
  let i = start;
  while (i < lines.length && lines[i]!.indent === indent && isDash(lines[i]!.text)) {
    const text = lines[i]!.text;
    const m = /^-(\s*)/.exec(text)!;
    const dashPrefix = 1 + m[1]!.length;
    const rest = text.slice(dashPrefix);
    const contentIndent = indent + dashPrefix;
    if (rest === "") {
      // Nested node lives on the following deeper lines.
      const childStart = i + 1;
      if (childStart < lines.length && lines[childStart]!.indent > indent) {
        const [val, ni] = parseNode(lines, childStart, lines[childStart]!.indent);
        arr.push(val);
        i = ni;
      } else {
        arr.push(null);
        i++;
      }
    } else {
      // Inline content after the dash; continuation lines are deeper than the dash.
      let j = i + 1;
      while (j < lines.length && lines[j]!.indent > indent) j++;
      const synthetic: RawLine = { indent: contentIndent, text: rest, lineNo: lines[i]!.lineNo };
      const sub = [synthetic, ...lines.slice(i + 1, j)];
      const [val] = parseNode(sub, 0, contentIndent);
      arr.push(val);
      i = j;
    }
  }
  return [arr, i];
}

function parseMap(lines: RawLine[], start: number, indent: number): [Record<string, unknown>, number] {
  const obj: Record<string, unknown> = {};
  let i = start;
  while (i < lines.length && lines[i]!.indent === indent && !isDash(lines[i]!.text)) {
    const line = lines[i]!;
    const colon = findColon(line.text);
    if (colon === -1) throw new YamlError(`Expected 'key: value' mapping entry`, line.lineNo);
    const key = unquote(line.text.slice(0, colon).trim());
    const valueText = line.text.slice(colon + 1).trim();
    if (valueText === "") {
      const childStart = i + 1;
      if (childStart < lines.length && lines[childStart]!.indent > indent) {
        const [val, ni] = parseNode(lines, childStart, lines[childStart]!.indent);
        obj[key] = val;
        i = ni;
      } else {
        obj[key] = null;
        i++;
      }
    } else {
      obj[key] = parseScalar(valueText);
      i++;
    }
  }
  return [obj, i];
}

/** Index of the colon terminating a mapping key (": " or trailing ":"), outside quotes. */
function findColon(text: string): number {
  let quote: '"' | "'" | null = null;
  for (let i = 0; i < text.length; i++) {
    const c = text[i]!;
    if (quote) {
      if (c === quote) quote = null;
    } else if (c === '"' || c === "'") {
      quote = c;
    } else if (c === ":" && (i === text.length - 1 || text[i + 1] === " ")) {
      return i;
    }
  }
  return -1;
}

function parseScalar(raw: string): unknown {
  const s = raw.trim();
  if (s === "") return null;
  if (s[0] === '"') return JSON.parse(s);
  if (s[0] === "'") return s.slice(1, -1).replace(/''/g, "'");
  if (s[0] === "[") return parseFlowSeq(s);
  if (s === "true") return true;
  if (s === "false") return false;
  if (s === "null" || s === "~") return null;
  if (/^-?\d+$/.test(s)) return Number(s);
  if (/^-?\d*\.\d+$/.test(s)) return Number(s);
  return s;
}

function parseFlowSeq(s: string): unknown[] {
  const inner = s.slice(1, -1).trim();
  if (inner === "") return [];
  const parts: string[] = [];
  let depth = 0;
  let quote: '"' | "'" | null = null;
  let cur = "";
  for (let i = 0; i < inner.length; i++) {
    const c = inner[i]!;
    if (quote) {
      cur += c;
      if (c === quote) quote = null;
    } else if (c === '"' || c === "'") {
      quote = c;
      cur += c;
    } else if (c === "[") {
      depth++;
      cur += c;
    } else if (c === "]") {
      depth--;
      cur += c;
    } else if (c === "," && depth === 0) {
      parts.push(cur);
      cur = "";
    } else {
      cur += c;
    }
  }
  if (cur.trim() !== "") parts.push(cur);
  return parts.map((p) => parseScalar(p));
}

function unquote(s: string): string {
  if (s[0] === '"') return JSON.parse(s) as string;
  if (s[0] === "'") return s.slice(1, -1).replace(/''/g, "'");
  return s;
}
