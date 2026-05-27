import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, it } from "node:test";
import { buildImportPayload, parseDeployArgs, resolveConfig, runDeploy, TuringClient, } from "../src/deploy.js";
const BASE_FLOW = {
    name: "Lead capture",
    description: "test",
    guardrailMethod: "LLM_JUDGE",
    triggerDescription: null,
    triggerMode: "ONCE",
    triggerLanguage: "AUTO",
    graph: {
        nodes: [
            { id: "start", type: "start", position: { x: 0, y: 0 }, data: { label: "START", type: "start" } },
            { id: "end", type: "end", position: { x: 0, y: 120 }, data: { label: "END", type: "end" } },
        ],
        edges: [
            { id: "e1", source: "start", target: "end", sourceHandle: null, targetHandle: null, label: null },
        ],
    },
};
/* ─────────────────────── Config resolution ─────────────────────── */
describe("resolveConfig", () => {
    it("merges package.json → env → flags (last wins)", () => {
        const config = resolveConfig({ url: "https://pkg.example", agentId: "pkg-agent", username: "pkg-user" }, { TURING_URL: "https://env.example", TURING_USERNAME: "env-user" }, { agentId: "flag-agent" });
        assert.equal(config.url, "https://env.example");
        assert.equal(config.username, "env-user");
        assert.equal(config.agentId, "flag-agent");
        assert.equal(config.mode, "bundle"); // default
        assert.equal(config.createOnly, false); // default
    });
    it("strips trailing slashes from the URL", () => {
        const config = resolveConfig({}, {}, { url: "https://turing.example///", agentId: "a", username: "u" });
        assert.equal(config.url, "https://turing.example");
    });
    it("throws when a required field is missing", () => {
        assert.throws(() => resolveConfig({}, {}, {}), (err) => err instanceof Error &&
            err.message.includes("Missing required deploy config"));
    });
    it("rejects unknown deploy modes", () => {
        assert.throws(() => resolveConfig({}, {}, { url: "x", agentId: "a", username: "u", mode: "yolo" }), (err) => err instanceof Error && err.message.includes("Invalid mode"));
    });
    it("honors TURING_CREATE_ONLY=1", () => {
        const config = resolveConfig({}, { TURING_CREATE_ONLY: "1" }, { url: "x", agentId: "a", username: "u" });
        assert.equal(config.createOnly, true);
    });
});
/* ─────────────────────── Argv parser ─────────────────────── */
describe("parseDeployArgs", () => {
    it("parses positional input dir + every long flag", () => {
        const parsed = parseDeployArgs([
            "dist",
            "--url", "https://example",
            "--agent", "agent-1",
            "--username", "alex",
            "--mode", "single",
            "--create-only",
        ]);
        assert.equal(parsed.inputDir, "dist");
        assert.equal(parsed.flags.url, "https://example");
        assert.equal(parsed.flags.agentId, "agent-1");
        assert.equal(parsed.flags.username, "alex");
        assert.equal(parsed.flags.mode, "single");
        assert.equal(parsed.flags.createOnly, true);
    });
    it("rejects unknown flags", () => {
        assert.throws(() => parseDeployArgs(["dist", "--secret-mode"]), (err) => err instanceof Error && err.message.includes("Unknown deploy option"));
    });
    it("requires a positional input dir", () => {
        assert.throws(() => parseDeployArgs(["--url", "x"]), (err) => err instanceof Error && err.message.includes("Missing input directory"));
    });
});
/* ─────────────────────── Payload assembly ─────────────────────── */
describe("buildImportPayload", () => {
    it("stringifies graph into definitionJson and preserves slots/personas", () => {
        const payload = buildImportPayload({
            ...BASE_FLOW,
            slots: [{ name: "email", type: "STRING" }],
            personas: [{ name: "Mentor" }],
        });
        assert.equal(typeof payload.chatFlow.definitionJson, "string");
        const parsed = JSON.parse(payload.chatFlow.definitionJson);
        assert.equal(parsed.nodes.length, 2);
        assert.equal(parsed.edges.length, 1);
        assert.equal(payload.chatFlow.guardrailMethod, "LLM_JUDGE");
        assert.deepEqual(payload.slots, [{ name: "email", type: "STRING" }]);
        assert.deepEqual(payload.personas, [{ name: "Mentor" }]);
    });
    it("omits slots/personas when empty", () => {
        const payload = buildImportPayload(BASE_FLOW);
        assert.equal(payload.slots, undefined);
        assert.equal(payload.personas, undefined);
    });
});
/* ─────────────────────── TuringClient ─────────────────────── */
describe("TuringClient", () => {
    it("authenticates, picks up XSRF-TOKEN, and sends it on subsequent POSTs", async () => {
        const log = [];
        const fetchStub = makeFetchStub((req) => {
            log.push(req);
            if (req.url.endsWith("/api/csrf")) {
                return jsonResponse({ token: "csrf-abc", headerName: "X-XSRF-TOKEN" }, {
                    headers: { "set-cookie": ["XSRF-TOKEN=csrf-abc; Path=/", "JSESSIONID=sess-1; Path=/"] },
                });
            }
            return jsonResponse({ id: "new-id" });
        });
        const client = new TuringClient("https://turing.example", "alex", "hunter2", fetchStub);
        await client.authenticate();
        await client.post("/api/ai-agent/agent-1/chat-flow/import", { chatFlow: { name: "x" } });
        // Authenticate
        assert.equal(log[0]?.url, "https://turing.example/api/csrf");
        assert.equal(log[0]?.headers.Authorization, "Basic " + Buffer.from("alex:hunter2").toString("base64"));
        // POST carries CSRF + cookies + Basic auth + JSON body
        const post = log[1];
        assert.ok(post, "POST call was recorded");
        assert.equal(post.method, "POST");
        assert.equal(post.headers["X-XSRF-TOKEN"], "csrf-abc");
        assert.ok(post.headers.Cookie?.includes("XSRF-TOKEN=csrf-abc"), "XSRF cookie roundtripped");
        assert.ok(post.headers.Cookie?.includes("JSESSIONID=sess-1"), "session cookie roundtripped");
        assert.equal(post.headers.Authorization, log[0]?.headers.Authorization);
        assert.ok(post.body && JSON.parse(post.body).chatFlow.name === "x");
    });
    it("throws a friendly HttpError on 401 during authenticate", async () => {
        const fetchStub = makeFetchStub(() => ({
            ok: false,
            status: 401,
            statusText: "Unauthorized",
            async text() { return ""; },
            async json() { return {}; },
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            headers: { getSetCookie() { return []; } },
        }));
        const client = new TuringClient("https://x", "u", "p", fetchStub);
        await assert.rejects(() => client.authenticate(), (err) => err instanceof Error && err.message.includes("Authentication failed"));
    });
});
/* ─────────────────────── End-to-end runDeploy ─────────────────────── */
describe("runDeploy", () => {
    it("uploads every *.chat-flow.json in bundle mode (single round-trip)", async () => {
        const dir = mkdtempSync(join(tmpdir(), "flow-dsl-deploy-"));
        try {
            writeChatFlow(dir, "lead-capture", { ...BASE_FLOW, name: "Lead capture" });
            writeChatFlow(dir, "in-company", { ...BASE_FLOW, name: "In-company quote" });
            const calls = capturedCalls();
            const fetchStub = makeFetchStub((req) => {
                calls.push(req);
                if (req.url.endsWith("/api/csrf")) {
                    return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN" }, {
                        headers: { "set-cookie": ["XSRF-TOKEN=t; Path=/"] },
                    });
                }
                if (req.url.endsWith("/chat-flow/import-bundle")) {
                    return jsonResponse([{ id: "flow-1" }, { id: "flow-2" }]);
                }
                throw new Error("unexpected request: " + req.url);
            });
            const result = await runDeploy({ inputDir: dir, flags: { url: "https://t.example", agentId: "agent-1", username: "alex", mode: "bundle" } }, {
                cwd: dir,
                env: { TURING_PASSWORD: "secret" },
                fetchImpl: fetchStub,
                log: () => undefined,
            });
            assert.equal(result.ok, 2);
            assert.equal(result.failed, 0);
            // Bundle path: 1 GET /csrf + 1 POST /import-bundle
            assert.equal(calls.list.length, 2);
            assert.ok(calls.list[1]?.url.endsWith("/chat-flow/import-bundle"));
            const body = JSON.parse(calls.list[1]?.body ?? "[]");
            // readdir order isn't guaranteed across OSes — assert membership, not order.
            assert.deepEqual(body.map((b) => b.chatFlow.name).sort(), ["In-company quote", "Lead capture"]);
        }
        finally {
            rmSync(dir, { recursive: true, force: true });
        }
    });
    it("in single mode, updates flows that already exist by name (PUT) and creates the rest (POST /import)", async () => {
        const dir = mkdtempSync(join(tmpdir(), "flow-dsl-deploy-"));
        try {
            writeChatFlow(dir, "lead-capture", { ...BASE_FLOW, name: "Lead capture" });
            writeChatFlow(dir, "in-company", { ...BASE_FLOW, name: "In-company quote" });
            const calls = capturedCalls();
            const fetchStub = makeFetchStub((req) => {
                calls.push(req);
                if (req.url.endsWith("/api/csrf")) {
                    return jsonResponse({ token: "t", headerName: "X-XSRF-TOKEN" }, {
                        headers: { "set-cookie": ["XSRF-TOKEN=t; Path=/"] },
                    });
                }
                if (req.method === "GET" && req.url.endsWith("/chat-flow")) {
                    // "Lead capture" already exists, "In-company quote" does not.
                    return jsonResponse([{ id: "existing-uuid", name: "Lead capture" }]);
                }
                if (req.method === "PUT" && req.url.endsWith("/chat-flow/existing-uuid")) {
                    return jsonResponse({ id: "existing-uuid", name: "Lead capture" });
                }
                if (req.method === "POST" && req.url.endsWith("/chat-flow/import")) {
                    return jsonResponse({ id: "fresh-uuid", name: "In-company quote" });
                }
                throw new Error("unexpected request: " + req.method + " " + req.url);
            });
            const result = await runDeploy({ inputDir: dir, flags: { url: "https://t.example", agentId: "agent-1", username: "alex", mode: "single" } }, {
                cwd: dir,
                env: { TURING_PASSWORD: "secret" },
                fetchImpl: fetchStub,
                log: () => undefined,
            });
            assert.equal(result.ok, 2);
            const methods = new Set(calls.list.map((c) => `${c.method} ${c.url.split("/").pop()}`));
            assert.ok(methods.has("PUT existing-uuid"));
            assert.ok(methods.has("POST import"));
        }
        finally {
            rmSync(dir, { recursive: true, force: true });
        }
    });
    it("--create-only skips the GET /chat-flow lookup so every file POSTs to /import", async () => {
        const dir = mkdtempSync(join(tmpdir(), "flow-dsl-deploy-"));
        try {
            writeChatFlow(dir, "lead-capture", BASE_FLOW);
            const calls = capturedCalls();
            const fetchStub = makeFetchStub((req) => {
                calls.push(req);
                if (req.url.endsWith("/api/csrf")) {
                    return jsonResponse({ token: "t" }, { headers: { "set-cookie": ["XSRF-TOKEN=t"] } });
                }
                if (req.url.endsWith("/chat-flow/import"))
                    return jsonResponse({ id: "new" });
                throw new Error("unexpected: " + req.method + " " + req.url);
            });
            await runDeploy({
                inputDir: dir,
                flags: { url: "https://t", agentId: "a", username: "u", mode: "single", createOnly: true },
            }, {
                cwd: dir,
                env: { TURING_PASSWORD: "p" },
                fetchImpl: fetchStub,
                log: () => undefined,
            });
            const getList = calls.list.find((c) => c.method === "GET" && c.url.endsWith("/chat-flow"));
            assert.equal(getList, undefined, "GET /chat-flow must be skipped under --create-only");
        }
        finally {
            rmSync(dir, { recursive: true, force: true });
        }
    });
    it("aborts on a non-2xx upload (fail fast for CI)", async () => {
        const dir = mkdtempSync(join(tmpdir(), "flow-dsl-deploy-"));
        try {
            writeChatFlow(dir, "lead-capture", BASE_FLOW);
            const fetchStub = makeFetchStub((req) => {
                if (req.url.endsWith("/api/csrf")) {
                    return jsonResponse({ token: "t" }, { headers: { "set-cookie": ["XSRF-TOKEN=t"] } });
                }
                return {
                    ok: false,
                    status: 400,
                    statusText: "Bad Request",
                    async text() { return "validation failed: duplicate node id"; },
                    async json() { return {}; },
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    headers: { getSetCookie() { return []; } },
                };
            });
            await assert.rejects(() => runDeploy({ inputDir: dir, flags: { url: "https://t", agentId: "a", username: "u", mode: "bundle" } }, { cwd: dir, env: { TURING_PASSWORD: "p" }, fetchImpl: fetchStub, log: () => undefined }), (err) => err instanceof Error && err.message.includes("400"));
        }
        finally {
            rmSync(dir, { recursive: true, force: true });
        }
    });
    it("calls the prompt only when TURING_PASSWORD is unset", async () => {
        const dir = mkdtempSync(join(tmpdir(), "flow-dsl-deploy-"));
        try {
            writeChatFlow(dir, "lead-capture", BASE_FLOW);
            let promptCount = 0;
            const fetchStub = makeFetchStub((req) => {
                if (req.url.endsWith("/api/csrf")) {
                    return jsonResponse({ token: "t" }, { headers: { "set-cookie": ["XSRF-TOKEN=t"] } });
                }
                return jsonResponse([{ id: "new" }]);
            });
            await runDeploy({ inputDir: dir, flags: { url: "https://t", agentId: "a", username: "u", mode: "bundle" } }, {
                cwd: dir,
                env: {}, // no TURING_PASSWORD
                fetchImpl: fetchStub,
                prompt: async () => { promptCount++; return "from-prompt"; },
                log: () => undefined,
            });
            assert.equal(promptCount, 1);
        }
        finally {
            rmSync(dir, { recursive: true, force: true });
        }
    });
});
function capturedCalls() {
    const list = [];
    return {
        list,
        push(req) { list.push(req); },
    };
}
function makeFetchStub(handler) {
    return (async (input, init) => {
        const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
        const method = init?.method ?? "GET";
        const headers = {};
        if (init?.headers) {
            const h = init.headers;
            for (const k of Object.keys(h))
                headers[k] = h[k];
        }
        const req = { url, method, headers, body: init?.body };
        return handler(req);
    });
}
function jsonResponse(payload, options = {}) {
    const cookies = options.headers?.["set-cookie"] ?? [];
    return {
        ok: (options.status ?? 200) < 400,
        status: options.status ?? 200,
        statusText: "OK",
        async text() { return JSON.stringify(payload); },
        async json() { return payload; },
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        headers: { getSetCookie() { return cookies; } },
    };
}
function writeChatFlow(dir, name, flow) {
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, `${name}.chat-flow.json`), JSON.stringify(flow, null, 2));
}
//# sourceMappingURL=deploy.test.js.map