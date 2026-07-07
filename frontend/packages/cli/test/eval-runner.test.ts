import { test } from "node:test";
import assert from "node:assert/strict";

import { normalizeSuite } from "../src/eval/load.js";
import { evaluate, runSuite, type AuditEntry, type EvalBackend, type TurnInput } from "../src/eval/runner.js";

test("evaluate covers each assertion family", () => {
  const slots = { cargo_atual: "CFO" };
  const audit: AuditEntry[] = [{ slotName: "cargo_atual", source: "TOOL", originDetail: "search_ee_programs" }];

  assert.equal(evaluate({ type: "assistant.matches", pattern: ".*cargo.*" }, "qual seu cargo?", {}, [], null).passed, true);
  assert.equal(evaluate({ type: "assistant.not_matches", pattern: "erro" }, "tudo certo", {}, [], null).passed, true);
  assert.equal(evaluate({ type: "assistant.contains", value: "cargo" }, "seu cargo", {}, [], null).passed, true);
  assert.equal(evaluate({ type: "assistant.not_contains", value: "x" }, "abc", {}, [], null).passed, true);
  assert.equal(evaluate({ type: "slot", name: "cargo_atual", expected: "CFO" }, "", slots, [], null).passed, true);
  assert.equal(evaluate({ type: "slot", name: "cargo_atual", expected: "CEO" }, "", slots, [], null).passed, false);
  assert.equal(evaluate({ type: "tool_called", tool: "search_ee_programs" }, "", {}, audit, null).passed, true);
  assert.equal(evaluate({ type: "tool_not_called", tool: "delete_account" }, "", {}, audit, null).passed, true);
  assert.equal(evaluate({ type: "persona.forbidden", values: ["talvez"] }, "Talvez sim", {}, [], null).passed, false);
  assert.equal(evaluate({ type: "persona.required", values: ["obrigado"] }, "Muito obrigado", {}, [], null).passed, true);
  assert.equal(evaluate({ type: "node", expected: "ai-objetivo" }, "", {}, [], "ai-objetivo").passed, true);
});

test("evaluate fails an invalid regex cleanly", () => {
  const r = evaluate({ type: "assistant.matches", pattern: "(" }, "x", {}, [], null);
  assert.equal(r.passed, false);
  assert.match(r.detail, /invalid regex/);
});

test("runSuite drives the conversation and scores fixtures", async () => {
  const raw = {
    fixtures: [
      {
        id: "happy",
        conversation: [
          { user: "oi" },
          { "assert.assistant.matches": ".+" },
          { user: "sou CFO" },
          { "assert.slot.cargo": "CFO" },
        ],
      },
    ],
  };
  const suite = normalizeSuite(raw, "x.eval.json");

  const turns: TurnInput[] = [];
  const turnLengths: number[] = [];
  const backend: EvalBackend = {
    async runTurn(input) {
      // Snapshot the count now: the runner reuses one mutable array (the real
      // backend serializes it synchronously via fetch, so this is faithful).
      turnLengths.push(input.messages.length);
      turns.push(input);
      return "olá, qual seu cargo?";
    },
    async getSlots() {
      return { cargo: "CFO" };
    },
    async getAudit() {
      return [];
    },
    async getNode() {
      return null;
    },
  };

  const result = await runSuite(suite, backend, { agentId: "agent-1", newConversationId: () => "conv-1" });
  assert.equal(result.passed, true);
  assert.equal(result.fixtures[0]!.assertions.length, 2);
  // Second turn should carry the accumulated history (user, assistant, user).
  assert.equal(turnLengths[1], 3);
  assert.equal(turns[1]!.conversationId, "conv-1");
});

test("runSuite reports a failed assertion", async () => {
  const suite = normalizeSuite(
    { fixtures: [{ id: "f", conversation: [{ user: "hi" }, { "assert.slot.x": "1" }] }] },
    "x.eval.json",
  );
  const backend: EvalBackend = {
    async runTurn() {
      return "hi";
    },
    async getSlots() {
      return {};
    },
    async getAudit() {
      return [];
    },
    async getNode() {
      return null;
    },
  };
  const result = await runSuite(suite, backend, { agentId: "a", newConversationId: () => "c" });
  assert.equal(result.passed, false);
});

test("runSuite captures a turn error", async () => {
  const suite = normalizeSuite({ fixtures: [{ id: "f", conversation: [{ user: "hi" }] }] }, "x.eval.json");
  const backend: EvalBackend = {
    async runTurn() {
      throw new Error("boom");
    },
    async getSlots() {
      return {};
    },
    async getAudit() {
      return [];
    },
    async getNode() {
      return null;
    },
  };
  const result = await runSuite(suite, backend, { agentId: "a", newConversationId: () => "c" });
  assert.equal(result.passed, false);
  assert.match(result.fixtures[0]!.error!, /boom/);
});
