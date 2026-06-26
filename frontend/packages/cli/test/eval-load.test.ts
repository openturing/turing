import { test } from "node:test";
import assert from "node:assert/strict";

import { parseYaml } from "../src/eval/yaml.js";
import { normalizeSuite, EvalParseError } from "../src/eval/load.js";

test("normalizes the design-doc example suite", () => {
  const yaml = [
    "fixtures:",
    "  - id: happy-path-cfo",
    "    conversation:",
    '      - user: "Quero saber dos seus programas"',
    '      - assert.assistant.matches: ".*cargo.*"',
    '      - user: "Sou CFO numa fintech"',
    '      - assert.slot.cargo_atual: "CFO"',
    "      - assert.tool_called: search_ee_programs",
    "      - assert.persona_invariants:",
    '          forbidden: ["talvez", "não posso"]',
  ].join("\n");
  const suite = normalizeSuite(parseYaml(yaml), "lead.eval.yaml");
  assert.equal(suite.fixtures.length, 1);
  const steps = suite.fixtures[0]!.steps;
  assert.equal(steps[0]!.kind, "user");
  assert.deepEqual(steps[1]!, { kind: "assert", assertions: [{ type: "assistant.matches", pattern: ".*cargo.*" }] });
  assert.deepEqual(steps[3]!, { kind: "assert", assertions: [{ type: "slot", name: "cargo_atual", expected: "CFO" }] });
  assert.deepEqual(steps[4]!, { kind: "assert", assertions: [{ type: "tool_called", tool: "search_ee_programs" }] });
  assert.deepEqual(steps[5]!, { kind: "assert", assertions: [{ type: "persona.forbidden", values: ["talvez", "não posso"] }] });
});

test("supports a nested assert: block", () => {
  const raw = {
    fixtures: [
      { id: "f1", conversation: [{ user: "hi" }, { assert: { "assistant.contains": "hello", "persona.required": ["thanks"] } }] },
    ],
  };
  const suite = normalizeSuite(raw, "x.eval.json");
  const assertStep = suite.fixtures[0]!.steps[1]!;
  assert.equal(assertStep.kind, "assert");
  assert.equal((assertStep as any).assertions.length, 2);
});

test("throws on unknown assertion key", () => {
  const raw = { fixtures: [{ id: "f", conversation: [{ "assert.bogus": 1 }] }] };
  assert.throws(() => normalizeSuite(raw, "x.eval.json"), EvalParseError);
});

test("throws when fixtures is missing", () => {
  assert.throws(() => normalizeSuite({}, "x.eval.json"), EvalParseError);
});
