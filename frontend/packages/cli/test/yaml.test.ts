import { test } from "node:test";
import assert from "node:assert/strict";

import { parseYaml } from "../src/eval/yaml.js";

test("parses a flat mapping with scalars", () => {
  const doc = parseYaml("agent: abc\nllm: gpt\ncount: 3\non: true\noff: false\nnada: null\n") as Record<string, unknown>;
  assert.equal(doc.agent, "abc");
  assert.equal(doc.count, 3);
  assert.equal(doc.on, true);
  assert.equal(doc.off, false);
  assert.equal(doc.nada, null);
});

test("strips comments outside quotes but keeps # inside quotes", () => {
  const doc = parseYaml('a: 1 # comment\nb: "has # hash"\n') as Record<string, unknown>;
  assert.equal(doc.a, 1);
  assert.equal(doc.b, "has # hash");
});

test("parses a block sequence of mappings", () => {
  const yaml = [
    "fixtures:",
    "  - id: one",
    "    conversation:",
    "      - user: hello",
    "      - assert.assistant.matches: .+",
    "  - id: two",
    "    conversation:",
    "      - user: bye",
  ].join("\n");
  const doc = parseYaml(yaml) as { fixtures: any[] };
  assert.equal(doc.fixtures.length, 2);
  assert.equal(doc.fixtures[0].id, "one");
  assert.equal(doc.fixtures[0].conversation[0].user, "hello");
  assert.equal(doc.fixtures[0].conversation[1]["assert.assistant.matches"], ".+");
  assert.equal(doc.fixtures[1].id, "two");
});

test("parses inline flow sequences", () => {
  const doc = parseYaml('forbidden: ["talvez", "não posso", plain]\nempty: []\n') as Record<string, unknown>;
  assert.deepEqual(doc.forbidden, ["talvez", "não posso", "plain"]);
  assert.deepEqual(doc.empty, []);
});

test("parses a nested map under a sequence item key", () => {
  const yaml = [
    "conversation:",
    "  - assert.persona_invariants:",
    "      forbidden: [a, b]",
  ].join("\n");
  const doc = parseYaml(yaml) as { conversation: any[] };
  assert.deepEqual(doc.conversation[0]["assert.persona_invariants"].forbidden, ["a", "b"]);
});

test("double-quoted scalars honor escapes; single quotes are literal", () => {
  const doc = parseYaml('a: "line\\nbreak"\nb: \'it\'\'s\'\n') as Record<string, unknown>;
  assert.equal(doc.a, "line\nbreak");
  assert.equal(doc.b, "it's");
});

test("empty document is an empty object", () => {
  assert.deepEqual(parseYaml("\n# just a comment\n"), {});
});
