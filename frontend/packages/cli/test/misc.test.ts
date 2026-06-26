import { test } from "node:test";
import assert from "node:assert/strict";

import { scaffoldFiles } from "../src/scaffold.js";
import { buildFixtureYaml } from "../src/eval/record.js";
import { resolveConnection } from "../src/config.js";
import { consumeSse } from "../src/client.js";
import { parseYaml } from "../src/eval/yaml.js";
import { normalizeSuite } from "../src/eval/load.js";

/* ── scaffold ── */

test("scaffoldFiles produces the expected project skeleton", () => {
  const files = scaffoldFiles("lead-bot");
  assert.ok(files["turing.config.json"]);
  assert.ok(files["agent.json"]);
  assert.ok(files["evals/smoke.eval.yaml"]);
  assert.ok(files["docker-compose.dev.yml"]);
  const cfg = JSON.parse(files["turing.config.json"]!);
  assert.equal(cfg.name, "lead-bot");
  assert.equal(cfg.envs.local.url, "http://localhost:2700");
  const agent = JSON.parse(files["agent.json"]!);
  assert.equal(agent.title, "Lead Bot");
  assert.equal(agent.enabled, 1);
});

/* ── record (T127) ── */

test("buildFixtureYaml freezes user turns + slot snapshot, and round-trips", () => {
  const yaml = buildFixtureYaml(
    {
      conversationId: "abc-123",
      messages: [
        { role: "user", content: "Quero saber dos programas" },
        { role: "assistant", content: "Qual seu cargo?" },
        { role: "user", content: "Sou CFO" },
      ],
      slots: { cargo: "CFO" },
    },
    { agentId: "agent-9", stamp: "2026-06-26" },
  );
  const parsed = parseYaml(yaml) as any;
  assert.equal(parsed.agent, "agent-9");
  const suite = normalizeSuite(parsed, "rec.eval.yaml");
  const userSteps = suite.fixtures[0]!.steps.filter((s) => s.kind === "user");
  assert.equal(userSteps.length, 2);
  const slotAssert = suite.fixtures[0]!.steps.find(
    (s) => s.kind === "assert" && s.assertions.some((a) => a.type === "slot"),
  );
  assert.ok(slotAssert);
});

/* ── config ── */

test("resolveConnection prefers token, then basic, with precedence", async () => {
  const project = { default: "local", envs: { local: { url: "http://h:2700" } }, agentId: "a1" };
  const tok = await resolveConnection(project, {}, { token: "T" });
  assert.deepEqual(tok.auth, { kind: "token", token: "T" });
  assert.equal(tok.url, "http://h:2700");
  assert.equal(tok.agentId, "a1");

  const basic = await resolveConnection(project, { TURING_USERNAME: "u", TURING_PASSWORD: "p" }, {});
  assert.deepEqual(basic.auth, { kind: "basic", username: "u", password: "p" });

  // Flag overrides env (token supplied so the credential check passes).
  const flagWins = await resolveConnection(project, { TURING_URL: "http://env" }, { url: "http://flag", token: "T" });
  assert.equal(flagWins.url, "http://flag");
});

test("resolveConnection throws without a url or credentials", async () => {
  await assert.rejects(() => resolveConnection({}, {}, {}), /Missing instance URL/);
  await assert.rejects(() => resolveConnection({ envs: { local: { url: "http://h" } }, default: "local" }, {}, {}), /No credentials/);
});

/* ── SSE parsing ── */

function streamOf(text: string): ReadableStream<Uint8Array> {
  const bytes = new TextEncoder().encode(text);
  return new ReadableStream({
    start(controller) {
      // Split into two chunks to exercise the buffering across reads.
      const mid = Math.floor(bytes.length / 2);
      controller.enqueue(bytes.slice(0, mid));
      controller.enqueue(bytes.slice(mid));
      controller.close();
    },
  });
}

test("consumeSse decodes data frames and ignores comments", async () => {
  const events: string[] = [];
  await consumeSse(
    streamOf(': heartbeat\ndata: {"content":"a"}\n\ndata: {"content":"b"}\n\n'),
    (d) => events.push(d),
  );
  assert.deepEqual(events, ['{"content":"a"}', '{"content":"b"}']);
});
