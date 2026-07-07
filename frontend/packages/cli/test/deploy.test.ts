import { test } from "node:test";
import assert from "node:assert/strict";

import { buildFlowImportPayload, buildToolPayload } from "../src/commands/deploy.js";

test("buildFlowImportPayload wraps a TranspiledFlow", () => {
  const flow = {
    name: "Welcome",
    description: "hi",
    guardrailMethod: "LLM_JUDGE",
    triggerMode: "ONCE",
    triggerLanguage: "PT",
    graph: { nodes: [{ id: "n1" }], edges: [] },
    slots: [{ name: "email" }],
  };
  const payload = buildFlowImportPayload(flow) as any;
  assert.equal(payload.chatFlow.name, "Welcome");
  assert.equal(payload.chatFlow.guardrailMethod, "LLM_JUDGE");
  assert.deepEqual(JSON.parse(payload.chatFlow.definitionJson), { nodes: [{ id: "n1" }], edges: [] });
  assert.deepEqual(payload.slots, [{ name: "email" }]);
});

test("buildFlowImportPayload passes an existing import DTO through", () => {
  const dto = { chatFlow: { name: "X", definitionJson: "{}" } };
  assert.equal(buildFlowImportPayload(dto), dto);
});

test("buildToolPayload defaults from a bare groovy name", () => {
  const p = buildToolPayload("search-programs", "return 1");
  assert.equal(p.title, "Search Programs");
  assert.equal(p.groovyScript, "return 1");
  assert.equal(p.returnType, "string");
  assert.equal(p.parametersJson, "[]");
  assert.equal(p.enabled, 1);
});

test("buildToolPayload honours a sidecar", () => {
  const p = buildToolPayload("t", "code", {
    title: "Custom",
    returnType: "json",
    parametersJson: '[{"name":"q","type":"string"}]',
    id: "tool-9",
  });
  assert.equal(p.title, "Custom");
  assert.equal(p.returnType, "json");
  assert.equal(p.id, "tool-9");
});
