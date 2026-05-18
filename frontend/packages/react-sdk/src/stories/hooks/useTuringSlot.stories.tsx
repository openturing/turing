import type { Meta, StoryObj } from "@storybook/react-vite";
import { useMemo } from "react";

/**
 * # useTuringSlot
 *
 * Strongly-typed selector for a single chat-flow slot, built on top of
 * `useTuringSlots`. Eliminates the boilerplate of manually pulling
 * `slots[name]` + casting + `JSON.parse`-wrapping. Two surfaces:
 *
 * 1. **Raw string** — `useTuringSlot("name")` returns `value: string | null`,
 *    matching the original `useTuringSlots()[name]` shape. Drop-in.
 * 2. **Typed JSON** — `useTuringSlot<MyType>("programas_match")` runs
 *    `JSON.parse` when the raw value looks like JSON (starts with `[` /
 *    `&#123;`) and types the result as `MyType | null`. Parse failures
 *    don't throw — they surface as `parseError: string`.
 *
 * ## The 4 parse modes
 *
 * | Mode | Behavior | Typical use |
 * |------|----------|-------------|
 * | `"auto"` (default) | `JSON.parse` only when raw looks like JSON | mixed pool of slots — simple strings stay raw, JSON-encoded ones parsed |
 * | `"json"` | Always `JSON.parse`. Anything malformed → `parseError` | slots you know are JSON; want hard failure on bad input |
 * | `"raw"` | Never parses, return original string | string slot that happens to start with `[` / `&#123;` (e.g. `"[Editado]"`) |
 * | `(raw) => T` (custom fn) | Caller-supplied parser | non-JSON encodings: CSV, URL params, base64 |
 *
 * ## Usage
 * ```tsx
 * // Raw string slot — no generic, no parse option.
 * const name = useTuringSlot("name");
 * return <h1>Olá, {name.value ?? "visitante"}</h1>;
 *
 * // JSON-encoded slot — generic triggers auto JSON.parse.
 * const programs = useTuringSlot<ProgramMatch[]>("programas_match");
 * if (!programs.value) return null;
 * return <ProgramGrid programs={programs.value} />;
 *
 * // Custom parser — comma-separated list.
 * const tags = useTuringSlot<string[]>("tags", {
 *   parse: (raw) => raw.split(",").map((s) => s.trim()).filter(Boolean),
 * });
 * ```
 *
 * ## Return shape
 * - `value: T | null` — parsed value, or `null` when slot empty or parse failed
 * - `rawValue: string | null` — original string, kept for debugging / fallback
 * - `parseError: string | null` — non-null when parsing threw; `value` is `null`
 * - `isLoading: boolean` — first-load indicator from the underlying fetch
 * - `refresh(): void` — force a refetch
 * - `conversationId: string | null` — the conversation read from
 *
 * This story runs the actual `parseRaw` algorithm against a fixed raw
 * value so you can flip parse modes and observe the contract — without
 * needing a live Turing instance.
 */

// ─── Inline copy of the SDK's parse algorithm ─────────────────────────
// Mirrors useTuringSlot.parseRaw 1:1 so the story exercises the real
// behavior. Kept inline (rather than imported) because the actual hook
// transitively depends on `useTuringSlots` → `fetch` → a configured
// Turing server, which doesn't exist in Storybook.

const LIKELY_JSON_PATTERN = /^[[{]/;

type ParseMode = "auto" | "json" | "raw" | "csv" | "urlencoded";

function parseRaw(
  raw: string,
  mode: ParseMode,
): { value: unknown; parseError: string | null } {
  if (mode === "raw") {
    return { value: raw, parseError: null };
  }
  if (mode === "csv") {
    try {
      const value = raw.split(",").map((s) => s.trim()).filter(Boolean);
      return { value, parseError: null };
    } catch (e) {
      return { value: null, parseError: e instanceof Error ? e.message : "csv parser threw" };
    }
  }
  if (mode === "urlencoded") {
    try {
      const params = new URLSearchParams(raw);
      const value: Record<string, string> = {};
      params.forEach((v, k) => { value[k] = v; });
      return { value, parseError: null };
    } catch (e) {
      return { value: null, parseError: e instanceof Error ? e.message : "urlencoded parser threw" };
    }
  }
  if (mode === "auto" && !LIKELY_JSON_PATTERN.test(raw)) {
    return { value: raw, parseError: null };
  }
  try {
    return { value: JSON.parse(raw) as unknown, parseError: null };
  } catch (e) {
    return {
      value: null,
      parseError: e instanceof Error ? e.message : "JSON.parse failed",
    };
  }
}

// ─── Demo component ────────────────────────────────────────────────────

function SlotDemo({
  slotName,
  rawValue,
  parseMode,
}: {
  slotName: string;
  rawValue: string;
  parseMode: ParseMode;
}) {
  // Recompute with `useMemo` to match the hook's behavior of memoizing
  // the parse step on `rawValue` + `parseMode`.
  const { value, parseError } = useMemo(
    () => parseRaw(rawValue, parseMode),
    [rawValue, parseMode],
  );

  const rawIsEmpty = rawValue.length === 0;
  const effectiveRaw = rawIsEmpty ? null : rawValue;
  const effectiveValue = rawIsEmpty ? null : value;

  const valueLabel = (() => {
    if (effectiveValue === null) return "null";
    if (typeof effectiveValue === "string") return JSON.stringify(effectiveValue);
    return JSON.stringify(effectiveValue, null, 2);
  })();

  const valueType = (() => {
    if (effectiveValue === null) return "null";
    if (Array.isArray(effectiveValue)) return `${typeof effectiveValue} (array, ${effectiveValue.length} item${effectiveValue.length === 1 ? "" : "s"})`;
    if (typeof effectiveValue === "object") return `${typeof effectiveValue} (record, ${Object.keys(effectiveValue).length} key${Object.keys(effectiveValue).length === 1 ? "" : "s"})`;
    return typeof effectiveValue;
  })();

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "640px" }}>
      {/* Slot context */}
      <div
        style={{
          padding: "12px 14px",
          background: "#f8fafc",
          borderRadius: "8px",
          fontSize: "12px",
          color: "#475569",
          marginBottom: "12px",
          fontFamily: "monospace",
        }}
      >
        <strong>useTuringSlot&lt;T&gt;(</strong>
        <code style={{ color: "#0f172a" }}>"{slotName}"</code>
        {parseMode !== "auto" && (
          <>
            <strong>, {"{ parse: "}</strong>
            <code style={{ color: "#0f172a" }}>
              {parseMode === "csv" || parseMode === "urlencoded"
                ? `(raw) => /* ${parseMode} */`
                : `"${parseMode}"`}
            </code>
            <strong>{" }"}</strong>
          </>
        )}
        <strong>)</strong>
      </div>

      {/* Mode hint */}
      <div
        style={{
          padding: "10px 14px",
          background: "#eff6ff",
          borderLeft: "3px solid #2563eb",
          borderRadius: "0 6px 6px 0",
          fontSize: "12px",
          color: "#1e3a8a",
          marginBottom: "12px",
        }}
      >
        {parseMode === "auto" && (
          <>
            <strong>auto mode</strong> — runs <code>JSON.parse</code> only when raw matches{" "}
            <code>/^[[{"{"}]/</code>. Plain strings pass through untouched.
          </>
        )}
        {parseMode === "json" && (
          <>
            <strong>json mode</strong> — always runs <code>JSON.parse</code>. Plain strings
            without quotes will surface a <code>parseError</code>.
          </>
        )}
        {parseMode === "raw" && (
          <>
            <strong>raw mode</strong> — never parses. Escape hatch for strings that{" "}
            <em>look</em> like JSON but shouldn't be parsed.
          </>
        )}
        {parseMode === "csv" && (
          <>
            <strong>custom fn</strong> — caller-supplied parser. Here: split by{" "}
            <code>,</code>, trim, drop empty entries → <code>string[]</code>.
          </>
        )}
        {parseMode === "urlencoded" && (
          <>
            <strong>custom fn</strong> — caller-supplied parser. Here: parse with{" "}
            <code>URLSearchParams</code> → <code>Record&lt;string, string&gt;</code>.
          </>
        )}
      </div>

      {/* Return shape table */}
      <div
        style={{
          border: "1px solid #e2e8f0",
          borderRadius: "10px",
          overflow: "hidden",
          marginBottom: "10px",
        }}
      >
        <div
          style={{
            padding: "8px 14px",
            background: "#f1f5f9",
            fontSize: "11px",
            fontWeight: 600,
            color: "#475569",
            textTransform: "uppercase",
            letterSpacing: "0.05em",
          }}
        >
          Hook return
        </div>

        <Row label="rawValue" value={effectiveRaw === null ? "null" : JSON.stringify(effectiveRaw)} />
        <Row
          label="value"
          value={valueLabel}
          highlight={parseError ? "error" : effectiveValue !== null ? "success" : "neutral"}
        />
        <Row label="typeof value" value={valueType} />
        <Row
          label="parseError"
          value={parseError === null ? "null" : JSON.stringify(parseError)}
          highlight={parseError ? "error" : "neutral"}
        />
        <Row label="isLoading" value="false" />
      </div>

      {/* Consumer pattern */}
      <details
        style={{
          fontSize: "12px",
          color: "#475569",
          marginTop: "10px",
        }}
      >
        <summary style={{ cursor: "pointer", fontWeight: 600 }}>
          Como o componente consumiria isso
        </summary>
        <pre
          style={{
            background: "#0f172a",
            color: "#e2e8f0",
            padding: "12px",
            borderRadius: "6px",
            fontSize: "11px",
            overflowX: "auto",
            marginTop: "8px",
          }}
        >
{`const slot = useTuringSlot${parseMode === "auto" ? "" : "<T>"}("${slotName}"${
  parseMode === "auto"
    ? ""
    : `, { parse: ${
        parseMode === "csv" || parseMode === "urlencoded"
          ? `(raw) => /* ${parseMode} */`
          : `"${parseMode}"`
      } }`
});

if (slot.isLoading) return <Spinner />;
if (slot.parseError) {
  console.warn("Bad slot:", slot.rawValue, slot.parseError);
  return <Fallback raw={slot.rawValue} />;
}
if (!slot.value) return null;

return <View data={slot.value} />;`}
        </pre>
      </details>
    </div>
  );
}

function Row({
  label,
  value,
  highlight = "neutral",
}: {
  label: string;
  value: string;
  highlight?: "success" | "error" | "neutral";
}) {
  const bg = highlight === "success" ? "#f0fdf4"
    : highlight === "error" ? "#fef2f2"
    : "white";
  const code = highlight === "success" ? "#166534"
    : highlight === "error" ? "#991b1b"
    : "#0f172a";
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "space-between",
        gap: "10px",
        padding: "8px 14px",
        background: bg,
        borderBottom: "1px solid #f1f5f9",
        fontSize: "12px",
        alignItems: "flex-start",
      }}
    >
      <code style={{ color: "#475569", flexShrink: 0 }}>{label}</code>
      <pre
        style={{
          margin: 0,
          color: code,
          textAlign: "right",
          fontFamily: "monospace",
          fontSize: "11px",
          whiteSpace: "pre-wrap",
          wordBreak: "break-all",
          maxWidth: "65%",
        }}
      >
        {value}
      </pre>
    </div>
  );
}

const meta: Meta<typeof SlotDemo> = {
  title: "Hooks/useTuringSlot",
  component: SlotDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Single-slot selector with type-safe parsing. Cycle the `parseMode` arg to watch the same `rawValue` flow through each of the 4 modes — `auto` (default), `json`, `raw`, and two custom-fn examples (CSV-list, URL-encoded).",
      },
    },
  },
  argTypes: {
    slotName: {
      description: "Slot name passed to the hook.",
      control: { type: "text" },
    },
    rawValue: {
      description: "Raw slot value as it would arrive from the server.",
      control: { type: "text" },
    },
    parseMode: {
      description:
        "Parsing strategy. The 3 built-in modes (`auto`/`json`/`raw`) plus 2 custom-fn examples (`csv` / `urlencoded`) showing how `(raw) => T` works.",
      control: { type: "select" },
      options: ["auto", "json", "raw", "csv", "urlencoded"],
    },
  },
};

export default meta;
type Story = StoryObj<typeof SlotDemo>;

// ─── Stories — one per parse mode + edge cases ─────────────────────────

export const AutoMode_JsonSlot: Story = {
  name: "auto mode · JSON-looking slot (parsed)",
  args: {
    slotName: "programas_match",
    rawValue: '[{"nome":"MBA Executivo","area":"Liderança"},{"nome":"CFO Track","area":"Finanças"}]',
    parseMode: "auto",
  },
};

export const AutoMode_PlainString: Story = {
  name: "auto mode · plain string (untouched)",
  args: {
    slotName: "name",
    rawValue: "Alexandre Oliveira",
    parseMode: "auto",
  },
};

export const JsonMode_Strict: Story = {
  name: "json mode · always parses",
  args: {
    slotName: "career_path",
    rawValue: '{"current":"Gerente","next":"Diretor","horizon":"3 anos"}',
    parseMode: "json",
  },
};

export const JsonMode_ParseError: Story = {
  name: "json mode · bad input → parseError",
  args: {
    slotName: "career_path",
    rawValue: "Diretor de Marketing",
    parseMode: "json",
  },
};

export const RawMode_EscapeHatch: Story = {
  name: "raw mode · string starting with [ (no parse)",
  args: {
    slotName: "status_label",
    rawValue: "[Editado pelo consultor]",
    parseMode: "raw",
  },
};

export const CustomParser_CSV: Story = {
  name: "custom fn · comma-separated list → string[]",
  args: {
    slotName: "tags",
    rawValue: "executivo, fintech, c-level, lideranca",
    parseMode: "csv",
  },
};

export const CustomParser_UrlEncoded: Story = {
  name: "custom fn · URL params → Record<string,string>",
  args: {
    slotName: "utm_params",
    rawValue: "utm_source=linkedin&utm_campaign=education-ee-2026&utm_medium=organic",
    parseMode: "urlencoded",
  },
};

export const EmptySlot: Story = {
  name: "empty slot · value & rawValue both null",
  args: {
    slotName: "objetivo",
    rawValue: "",
    parseMode: "auto",
  },
};
