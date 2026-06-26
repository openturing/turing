/**
 * Pure eval runner: drives each fixture's conversation through an
 * {@link EvalBackend} and scores its assertions. Kept free of HTTP/IO so the
 * fixture loop + assertion semantics are unit-testable with a fake backend;
 * the real backend lives in {@code ./backend.ts}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import type {
  Assertion,
  AssertionResult,
  EvalFixture,
  EvalSuite,
  FixtureResult,
  SuiteResult,
} from "./types.js";

/** One row of the T60 slot-write audit, as needed for {@code tool_called}. */
export interface AuditEntry {
  slotName?: string | null;
  source?: string | null;
  originDetail?: string | null;
}

export interface TurnInput {
  agentId: string;
  llm?: string;
  flow?: string;
  conversationId: string;
  messages: { role: string; content: string }[];
}

/** Backend seam the runner depends on; implemented over {@code TuringClient}. */
export interface EvalBackend {
  /** Runs one chat turn and returns the concatenated assistant text. */
  runTurn(input: TurnInput): Promise<string>;
  getSlots(conversationId: string): Promise<Record<string, string>>;
  getAudit(conversationId: string): Promise<AuditEntry[]>;
  getNode(conversationId: string): Promise<string | null>;
}

export interface RunOptions {
  /** Resolved agent id (suite.agent wins, else this). */
  agentId: string;
  /** Mints a unique conversation id per fixture (defaults to crypto.randomUUID). */
  newConversationId?: (fixtureId: string) => string;
  /** Progress callback, one line at a time. */
  onLog?: (line: string) => void;
}

export async function runSuite(
  suite: EvalSuite,
  backend: EvalBackend,
  options: RunOptions,
): Promise<SuiteResult> {
  const agentId = suite.agent ?? options.agentId;
  const mint = options.newConversationId ?? ((id) => `eval-${id}-${crypto.randomUUID()}`);
  const log = options.onLog ?? (() => {});

  const fixtures: FixtureResult[] = [];
  for (const fixture of suite.fixtures) {
    fixtures.push(await runFixture(fixture, suite, backend, agentId, mint(fixture.id), log));
  }
  return { suite, fixtures, passed: fixtures.every((f) => f.passed) };
}

async function runFixture(
  fixture: EvalFixture,
  suite: EvalSuite,
  backend: EvalBackend,
  agentId: string,
  conversationId: string,
  log: (line: string) => void,
): Promise<FixtureResult> {
  const messages: { role: string; content: string }[] = [];
  let lastAssistant = "";
  const assertions: AssertionResult[] = [];

  for (const step of fixture.steps) {
    if (step.kind === "user") {
      messages.push({ role: "user", content: step.text });
      try {
        const reply = await backend.runTurn({
          agentId,
          llm: suite.llm,
          flow: suite.flow,
          conversationId,
          messages,
        });
        lastAssistant = reply;
        messages.push({ role: "assistant", content: reply });
      } catch (err) {
        return {
          fixture,
          passed: false,
          assertions,
          error: `turn failed at "${truncate(step.text, 60)}": ${(err as Error).message}`,
        };
      }
    } else {
      // Lazily fetch the supporting state once per assert step.
      let slots: Record<string, string> | undefined;
      let audit: AuditEntry[] | undefined;
      let node: string | null | undefined;
      for (const a of step.assertions) {
        if (a.type === "slot" && !slots) slots = await safe(backend.getSlots(conversationId), {});
        if ((a.type === "tool_called" || a.type === "tool_not_called") && !audit) {
          audit = await safe(backend.getAudit(conversationId), []);
        }
        if (a.type === "node" && node === undefined) node = await safe(backend.getNode(conversationId), null);
        assertions.push(evaluate(a, lastAssistant, slots ?? {}, audit ?? [], node ?? null));
      }
    }
  }
  const passed = assertions.every((r) => r.passed);
  log(`${passed ? "✓" : "✗"} ${fixture.id} — ${assertions.filter((a) => a.passed).length}/${assertions.length} assertions`);
  return { fixture, passed, assertions };
}

/** Evaluates a single assertion. Exported for unit tests. */
export function evaluate(
  a: Assertion,
  assistant: string,
  slots: Record<string, string>,
  audit: AuditEntry[],
  node: string | null,
): AssertionResult {
  switch (a.type) {
    case "assistant.matches":
    case "assistant.not_matches": {
      const re = tryRegex(a.pattern);
      if (!re) return fail(a, `invalid regex: ${a.pattern}`);
      const hit = re.test(assistant);
      const want = a.type === "assistant.matches";
      return result(a, hit === want, `/${a.pattern}/ ${hit ? "matched" : "did not match"} the reply`);
    }
    case "assistant.contains":
      return result(a, assistant.includes(a.value), `reply ${assistant.includes(a.value) ? "contains" : "does not contain"} "${a.value}"`);
    case "assistant.not_contains":
      return result(a, !assistant.includes(a.value), `reply ${assistant.includes(a.value) ? "contains" : "does not contain"} "${a.value}"`);
    case "slot": {
      const actual = slots[a.name];
      const ok = actual !== undefined && String(actual) === a.expected;
      return result(a, ok, `slot '${a.name}' = ${actual === undefined ? "(unset)" : JSON.stringify(actual)}, expected ${JSON.stringify(a.expected)}`);
    }
    case "tool_called":
    case "tool_not_called": {
      const called = audit.some((e) => e.source === "TOOL" && toolMatches(e, a.tool));
      const want = a.type === "tool_called";
      return result(a, called === want, `tool '${a.tool}' ${called ? "fired" : "did not fire"} (via slot-audit TOOL writes)`);
    }
    case "persona.forbidden": {
      const lower = assistant.toLowerCase();
      const present = a.values.filter((v) => lower.includes(v.toLowerCase()));
      return result(a, present.length === 0, present.length ? `forbidden phrase(s) present: ${present.join(", ")}` : "no forbidden phrases");
    }
    case "persona.required": {
      const lower = assistant.toLowerCase();
      const missing = a.values.filter((v) => !lower.includes(v.toLowerCase()));
      return result(a, missing.length === 0, missing.length ? `missing required phrase(s): ${missing.join(", ")}` : "all required phrases present");
    }
    case "node":
      return result(a, node === a.expected, `cursor node = ${node === null ? "(none)" : node}, expected ${a.expected}`);
  }
}

function toolMatches(e: AuditEntry, tool: string): boolean {
  const needle = tool.toLowerCase();
  const detail = (e.originDetail ?? "").toLowerCase();
  return detail === needle || detail.includes(needle) || (e.slotName ?? "").toLowerCase() === needle;
}

function tryRegex(pattern: string): RegExp | null {
  try {
    return new RegExp(pattern);
  } catch {
    return null;
  }
}

function result(assertion: Assertion, passed: boolean, detail: string): AssertionResult {
  return { assertion, passed, detail };
}

function fail(assertion: Assertion, detail: string): AssertionResult {
  return { assertion, passed: false, detail };
}

async function safe<T>(p: Promise<T>, fallback: T): Promise<T> {
  try {
    return await p;
  } catch {
    return fallback;
  }
}

function truncate(s: string, max: number): string {
  return s.length <= max ? s : s.slice(0, max) + "…";
}

/** Renders a short human label for an assertion (used in reports). */
export function describeAssertion(a: Assertion): string {
  switch (a.type) {
    case "assistant.matches":
      return `assistant matches /${a.pattern}/`;
    case "assistant.not_matches":
      return `assistant does not match /${a.pattern}/`;
    case "assistant.contains":
      return `assistant contains "${a.value}"`;
    case "assistant.not_contains":
      return `assistant does not contain "${a.value}"`;
    case "slot":
      return `slot ${a.name} == ${JSON.stringify(a.expected)}`;
    case "tool_called":
      return `tool ${a.tool} called`;
    case "tool_not_called":
      return `tool ${a.tool} not called`;
    case "persona.forbidden":
      return `persona forbids [${a.values.join(", ")}]`;
    case "persona.required":
      return `persona requires [${a.values.join(", ")}]`;
    case "node":
      return `cursor node == ${a.expected}`;
  }
}
