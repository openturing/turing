import { useMemo } from "react";
import { useTuringSlots, type UseTuringSlotsOptions } from "./use-turing-slots";

/**
 * How to coerce the raw slot string into the consumer's {@code T}:
 *
 * <ul>
 *   <li>{@code "auto"} (default) — runs {@code JSON.parse} only when the
 *       raw value looks like JSON (starts with {@code [} or {@code &#123;}).
 *       Other values are returned as-is. Works for the dominant Turing
 *       pattern: simple slots ({@code name}, {@code area}) stay raw
 *       strings, JSON-encoded slots ({@code programas_match},
 *       {@code career_path}, {@code proposta_in_company}) are parsed.</li>
 *   <li>{@code "json"} — always runs {@code JSON.parse}, even if the
 *       value doesn't pattern-match. Use when you know the slot is JSON
 *       and want a hard failure on anything malformed.</li>
 *   <li>{@code "raw"} — never parses. Escape hatch when a string slot
 *       happens to start with {@code [} / {@code &#123;}
 *       (e.g. {@code "[Editado]"}).</li>
 *   <li>{@code (raw) =&gt; T} — custom parser. Use for non-JSON
 *       encodings (URL-encoded params, comma-separated lists, base64).</li>
 * </ul>
 *
 * @since 2026.2.7
 */
export type UseTuringSlotParseMode<T> = "auto" | "json" | "raw" | ((raw: string) => T);

export interface UseTuringSlotOptions<T> extends UseTuringSlotsOptions {
  /** Parsing strategy. Defaults to {@code "auto"} — see {@link UseTuringSlotParseMode}. */
  readonly parse?: UseTuringSlotParseMode<T>;
}

export interface UseTuringSlotReturn<T = string> {
  /**
   * Parsed slot value, or {@code null} when the slot is not yet captured
   * or when parsing failed (in which case {@link parseError} explains why).
   */
  readonly value: T | null;
  /**
   * Raw slot string, before any parsing. Useful for debugging and for
   * code paths that need the original (e.g. clipboard copy of a URL slot).
   */
  readonly rawValue: string | null;
  /** Conversation id the slots were read for. */
  readonly conversationId: string | null;
  /** Whether the underlying request is still in flight (first load). */
  readonly isLoading: boolean;
  /**
   * Error message from the parser. Non-null only when {@link rawValue}
   * is non-null but parsing produced an exception or returned a value
   * the caller can't use. Consumer can fall back to {@link rawValue} or
   * surface the error in dev tools.
   */
  readonly parseError: string | null;
  /** Force a refetch of the underlying slots map. */
  readonly refresh: () => void;
}

const LIKELY_JSON_PATTERN = /^[[{]/;

/**
 * Single-slot selector built on top of {@link useTuringSlots}. Two flavors:
 *
 * <ol>
 *   <li><b>Raw string</b> — {@code useTuringSlot("name")} returns
 *       {@code value: string | null}. Backward-compatible with v2026.2.7
 *       — existing call sites unchanged.</li>
 *   <li><b>Typed JSON</b> — {@code useTuringSlot<MyType>("programas_match")}
 *       runs {@code JSON.parse} automatically (when the raw value looks
 *       like JSON) and types the return as {@code MyType | null}. Removes
 *       the boilerplate of manual {@code safeParse&lt;MyType&gt;(slots.x)}
 *       wrappers in the consumer.</li>
 * </ol>
 *
 * @example
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
 * <p>Parse failures surface via {@link UseTuringSlotReturn.parseError} —
 * {@code value} is {@code null}, {@code rawValue} keeps the original
 * string. No exception is thrown so the consuming component never crashes
 * on a malformed slot.
 *
 * @since 2026.2.7
 */
export function useTuringSlot<T = string>(
  name: string,
  options?: UseTuringSlotOptions<T>,
): UseTuringSlotReturn<T> {
  const { slots, conversationId, status, refresh } = useTuringSlots(options);
  const raw = slots[name];
  const rawValue = raw && raw.length > 0 ? raw : null;
  const parseMode = options?.parse ?? "auto";

  // Memoize the parse step so JSON.parse / custom fn don't re-run on
  // every render when the raw string hasn't changed. When the caller
  // passes an inline lambda for `parse`, its identity changes each render
  // and the memo invalidates — that's correct, the work to redo is one
  // function call on a typical &lt;1 KB slot value, not a re-fetch.
  const parsedResult = useMemo<{ value: T | null; parseError: string | null }>(() => {
    if (rawValue === null) {
      return { value: null, parseError: null };
    }
    return parseRaw<T>(rawValue, parseMode);
  }, [rawValue, parseMode]);

  return {
    value: parsedResult.value,
    rawValue,
    conversationId,
    isLoading: status === "loading",
    parseError: parsedResult.parseError,
    refresh,
  };
}

/**
 * Pure parsing helper — extracted so it can be unit-tested in isolation
 * once the SDK gains test infrastructure. Behaviour rules are documented
 * on {@link UseTuringSlotParseMode}.
 */
function parseRaw<T>(
  raw: string,
  mode: UseTuringSlotParseMode<T>,
): { value: T | null; parseError: string | null } {
  if (mode === "raw") {
    return { value: raw as unknown as T, parseError: null };
  }
  if (typeof mode === "function") {
    try {
      return { value: mode(raw), parseError: null };
    } catch (e) {
      return {
        value: null,
        parseError: e instanceof Error ? e.message : "custom parser threw",
      };
    }
  }
  if (mode === "auto" && !LIKELY_JSON_PATTERN.test(raw)) {
    // Doesn't look like JSON — return as-is. Caller is responsible for
    // the type assertion; with the default {@code T = string} this is
    // a safe no-op. With a non-string T and "auto" mode, the caller
    // explicitly chose to risk the cast.
    return { value: raw as unknown as T, parseError: null };
  }
  // mode === "json", or mode === "auto" + raw looks like JSON.
  try {
    return { value: JSON.parse(raw) as T, parseError: null };
  } catch (e) {
    return {
      value: null,
      parseError: e instanceof Error ? e.message : "JSON.parse failed",
    };
  }
}
