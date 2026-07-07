/**
 * Loads and normalizes {@code *.eval.yaml} (or {@code .eval.json}) suite files
 * into the {@link EvalSuite} model the runner consumes.
 *
 * <p>The surface syntax mirrors the §IX.8.b design. A fixture's
 * {@code conversation} is a list of steps; each step is either a {@code user}
 * turn or one or more assertions. Assertions accept both the flat dotted form
 * used in the design doc and a nested {@code assert:} block:
 *
 * <pre>{@code
 * fixtures:
 *   - id: happy-path-cfo
 *     conversation:
 *       - user: "Quero saber dos seus programas"
 *       - assert.assistant.matches: ".*cargo.*"
 *       - user: "Sou CFO numa fintech"
 *       - assert.slot.cargo_atual: "CFO"
 *         assert.tool_called: search_ee_programs
 *         assert.persona_invariants:
 *           forbidden: ["talvez", "não posso"]
 * }</pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import { readFileSync } from "node:fs";

import { parseYaml } from "./yaml.js";
import type { Assertion, EvalFixture, EvalStep, EvalSuite } from "./types.js";

export class EvalParseError extends Error {
  constructor(message: string, public readonly file: string) {
    super(`${file}: ${message}`);
    this.name = "EvalParseError";
  }
}

/** Reads a suite file from disk and normalizes it. */
export function loadSuiteFile(file: string): EvalSuite {
  const text = readFileSync(file, "utf8");
  const raw = file.endsWith(".json") ? (JSON.parse(text) as unknown) : parseYaml(text);
  return normalizeSuite(raw, file);
}

/** Normalizes an already-parsed object into an {@link EvalSuite}. */
export function normalizeSuite(raw: unknown, file: string): EvalSuite {
  if (!isObject(raw)) throw new EvalParseError("top-level document must be a mapping", file);
  const fixturesRaw = raw.fixtures;
  if (!Array.isArray(fixturesRaw)) throw new EvalParseError("missing 'fixtures' list", file);
  const fixtures = fixturesRaw.map((f, idx) => normalizeFixture(f, idx, file));
  return {
    file,
    agent: optString(raw.agent),
    llm: optString(raw.llm),
    flow: optString(raw.flow),
    fixtures,
  };
}

function normalizeFixture(raw: unknown, idx: number, file: string): EvalFixture {
  if (!isObject(raw)) throw new EvalParseError(`fixture #${idx + 1} must be a mapping`, file);
  const id = optString(raw.id) ?? `fixture-${idx + 1}`;
  const conversation = raw.conversation;
  if (!Array.isArray(conversation)) {
    throw new EvalParseError(`fixture '${id}' is missing a 'conversation' list`, file);
  }
  const steps = conversation.map((s, sIdx) => normalizeStep(s, id, sIdx, file));
  return { id, description: optString(raw.description), steps };
}

function normalizeStep(raw: unknown, fixtureId: string, idx: number, file: string): EvalStep {
  if (!isObject(raw)) throw new EvalParseError(`fixture '${fixtureId}' step #${idx + 1} must be a mapping`, file);
  if ("user" in raw) {
    return { kind: "user", text: String(raw.user ?? "") };
  }
  // Collect assertion entries from both flat dotted keys and a nested `assert:` block.
  const entries: [string, unknown][] = [];
  for (const [key, value] of Object.entries(raw)) {
    if (key === "assert") {
      if (isObject(value)) {
        for (const [k, v] of Object.entries(value)) entries.push([k, v]);
      }
    } else if (key.startsWith("assert.")) {
      entries.push([key.slice("assert.".length), value]);
    } else {
      entries.push([key, value]);
    }
  }
  if (entries.length === 0) {
    throw new EvalParseError(`fixture '${fixtureId}' step #${idx + 1} has neither 'user' nor assertions`, file);
  }
  const assertions = entries.map(([k, v]) => toAssertion(k, v, fixtureId, file));
  return { kind: "assert", assertions };
}

function toAssertion(key: string, value: unknown, fixtureId: string, file: string): Assertion {
  switch (key) {
    case "assistant.matches":
      return { type: "assistant.matches", pattern: String(value) };
    case "assistant.not_matches":
      return { type: "assistant.not_matches", pattern: String(value) };
    case "assistant.contains":
      return { type: "assistant.contains", value: String(value) };
    case "assistant.not_contains":
      return { type: "assistant.not_contains", value: String(value) };
    case "tool_called":
      return { type: "tool_called", tool: String(value) };
    case "tool_not_called":
      return { type: "tool_not_called", tool: String(value) };
    case "node":
      return { type: "node", expected: String(value) };
    case "persona.forbidden":
      return { type: "persona.forbidden", values: toStringArray(value) };
    case "persona.required":
      return { type: "persona.required", values: toStringArray(value) };
    case "persona_invariants":
    case "persona": {
      // A nested map { forbidden: [...], required: [...] } — the design's form.
      // We can only return a single Assertion per key, so collapse to whichever
      // sub-keys are present, preferring 'forbidden'. Callers that need both
      // should use the flat persona.forbidden / persona.required keys.
      if (isObject(value)) {
        if ("forbidden" in value) return { type: "persona.forbidden", values: toStringArray(value.forbidden) };
        if ("required" in value) return { type: "persona.required", values: toStringArray(value.required) };
      }
      throw new EvalParseError(
        `fixture '${fixtureId}': '${key}' needs a 'forbidden' or 'required' list`,
        file,
      );
    }
    default:
      if (key.startsWith("slot.")) {
        return { type: "slot", name: key.slice("slot.".length), expected: String(value) };
      }
      throw new EvalParseError(`fixture '${fixtureId}': unknown assertion '${key}'`, file);
  }
}

function toStringArray(value: unknown): string[] {
  if (Array.isArray(value)) return value.map((v) => String(v));
  if (value === null || value === undefined) return [];
  return [String(value)];
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function optString(value: unknown): string | undefined {
  return value === null || value === undefined ? undefined : String(value);
}
