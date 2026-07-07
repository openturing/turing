/**
 * Normalized model for a {@code turing eval} suite — the in-memory shape the
 * runner executes, decoupled from the YAML/JSON surface syntax.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

/** A whole {@code *.eval.yaml} file after normalization. */
export interface EvalSuite {
  /** Source file path (for reporting). */
  file: string;
  /** Agent id the conversation runs against; falls back to project / CLI flag. */
  agent?: string;
  /** Optional LLM instance id; when absent the server picks the agent default. */
  llm?: string;
  /** Optional chat-flow id to drive each conversation. */
  flow?: string;
  fixtures: EvalFixture[];
}

export interface EvalFixture {
  id: string;
  description?: string;
  steps: EvalStep[];
}

export type EvalStep =
  | { kind: "user"; text: string }
  | { kind: "assert"; assertions: Assertion[] };

export type Assertion =
  | { type: "assistant.matches"; pattern: string }
  | { type: "assistant.not_matches"; pattern: string }
  | { type: "assistant.contains"; value: string }
  | { type: "assistant.not_contains"; value: string }
  | { type: "slot"; name: string; expected: string }
  | { type: "tool_called"; tool: string }
  | { type: "tool_not_called"; tool: string }
  | { type: "persona.forbidden"; values: string[] }
  | { type: "persona.required"; values: string[] }
  | { type: "node"; expected: string };

/** Outcome of evaluating one assertion. */
export interface AssertionResult {
  assertion: Assertion;
  passed: boolean;
  /** Human-readable explanation shown on failure (and optionally on pass). */
  detail: string;
}

export interface FixtureResult {
  fixture: EvalFixture;
  passed: boolean;
  assertions: AssertionResult[];
  /** Set when a turn could not be executed (network / chat error). */
  error?: string;
}

export interface SuiteResult {
  suite: EvalSuite;
  fixtures: FixtureResult[];
  passed: boolean;
}
