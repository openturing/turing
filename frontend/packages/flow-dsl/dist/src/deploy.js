/**
 * `turing-flow-dsl deploy <dir>` — uploads transpiled chat-flow JSON
 * files to a running Turing instance.
 *
 * <p>Design constraints:
 * <ul>
 *   <li>Zero runtime dependencies — uses Node 20+ built-ins ({@code fetch},
 *       {@code node:readline}, {@code node:fs}). No {@code axios}, no
 *       {@code prompts} library.</li>
 *   <li>Connection details (URL, agent id, username) live in
 *       {@code package.json} under {@code "turing": { … }} so they're
 *       reviewable in PRs. Only the password is interactive.</li>
 *   <li>Env vars ({@code TURING_URL}, {@code TURING_AGENT_ID},
 *       {@code TURING_USERNAME}, {@code TURING_PASSWORD}) override
 *       package.json so CI can run unattended.</li>
 *   <li>CLI flags ({@code --url}, {@code --agent}, {@code --username},
 *       {@code --mode bundle|single}, {@code --create-only}) override
 *       both env and package.json.</li>
 *   <li>Idempotent by default: GETs the agent's flow list, matches by
 *       name, picks {@code POST /import} for new flows and
 *       {@code PUT /chat-flow/{id}} for existing ones. {@code --create-only}
 *       disables the update path (every flow is created fresh; safer when
 *       names might collide with unrelated flows).</li>
 *   <li>Aborts on the first non-2xx response so CI fails fast.</li>
 * </ul>
 *
 * @since 2026.3.1
 */
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { createInterface } from "node:readline";
import { basename, dirname, join, resolve } from "node:path";
/**
 * Loads the {@code "turing"} block from the nearest {@code package.json}
 * walking up from {@code cwd}. Returns an empty object when no package.json
 * is found or when it has no {@code turing} key — the CLI can still proceed
 * if env vars / flags supply every field.
 *
 * @internal exported for tests only.
 */
export function loadPackageConfig(cwd) {
    let dir = resolve(cwd);
    while (true) {
        const candidate = join(dir, "package.json");
        if (existsSync(candidate)) {
            try {
                const parsed = JSON.parse(readFileSync(candidate, "utf8"));
                return parsed.turing ?? {};
            }
            catch {
                return {};
            }
        }
        const parent = dirname(dir);
        if (parent === dir)
            return {};
        dir = parent;
    }
}
/**
 * Merges package.json → env vars → CLI flags (last wins). Throws if a
 * required field is still missing so the CLI surface stays declarative
 * (no "did you forget…?" warnings sprinkled through the upload path).
 *
 * @internal exported for tests only.
 */
export function resolveConfig(pkg, env, flags) {
    const url = flags.url ?? env.TURING_URL ?? pkg.url;
    const agentId = flags.agentId ?? env.TURING_AGENT_ID ?? pkg.agentId;
    const username = flags.username ?? env.TURING_USERNAME ?? pkg.username;
    const password = flags.password ?? env.TURING_PASSWORD;
    const mode = flags.mode ?? env.TURING_MODE ?? pkg.mode ?? "bundle";
    const createOnly = flags.createOnly ?? envBool(env.TURING_CREATE_ONLY) ?? pkg.createOnly ?? false;
    const missing = [];
    if (!url)
        missing.push("url (package.json turing.url, TURING_URL, or --url)");
    if (!agentId)
        missing.push("agentId (package.json turing.agentId, TURING_AGENT_ID, or --agent)");
    if (!username)
        missing.push("username (package.json turing.username, TURING_USERNAME, or --username)");
    if (missing.length > 0) {
        throw new Error(`Missing required deploy config: ${missing.join("; ")}`);
    }
    if (mode !== "single" && mode !== "bundle") {
        throw new Error(`Invalid mode '${mode}'. Use 'single' or 'bundle'.`);
    }
    // password is resolved separately (interactive prompt) when not supplied
    // via env/flag — the caller does that step.
    return {
        url: url.replace(/\/+$/, ""),
        agentId: agentId,
        username: username,
        password: password ?? "",
        mode,
        createOnly,
    };
}
function envBool(value) {
    if (value === undefined)
        return undefined;
    return value === "1" || value.toLowerCase() === "true";
}
/* ─────────────────────── TTY password prompt ─────────────────────── */
/**
 * Reads a password from the TTY with input echo suppressed. Throws when
 * stdin is not a TTY (CI without {@code TURING_PASSWORD} env hits this
 * path — fail loudly instead of hanging on a {@code read} that nobody
 * will answer).
 *
 * @internal exported for tests only.
 */
export async function promptPassword(promptText) {
    if (!process.stdin.isTTY) {
        throw new Error("stdin is not a TTY — set TURING_PASSWORD env var when running in CI.");
    }
    // node:readline doesn't expose a built-in "mask" mode, so we manually
    // wire stdin into raw mode and intercept output to keep typed chars off
    // the screen. The visible cursor still advances after Enter, which
    // matches the UX of `ssh`/`git push` over HTTPS.
    const rl = createInterface({ input: process.stdin, output: process.stdout, terminal: true });
    process.stdout.write(promptText);
    const muted = new MutedStdout(process.stdout);
    // readline's `output` property is typed as a full writable stream, but
    // at runtime it only invokes `.write()`. Cast to keep tsc happy.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rl.output = muted;
    return new Promise((accept, reject) => {
        rl.question("", (answer) => {
            rl.close();
            process.stdout.write("\n");
            accept(answer);
        });
        rl.on("error", reject);
    });
}
/**
 * Wraps a writable stream so any char that is not a newline is silently
 * swallowed — used to mute the readline echo while still letting the
 * prompt text we wrote BEFORE attaching the wrapper land on screen.
 */
class MutedStdout {
    inner;
    constructor(inner) {
        this.inner = inner;
    }
    write(chunk) {
        const text = typeof chunk === "string" ? chunk : Buffer.from(chunk).toString("utf8");
        if (text.includes("\n")) {
            return this.inner.write("\n");
        }
        return true;
    }
    on(_event, _listener) {
        return this;
    }
}
/* ─────────────────────── HTTP client ─────────────────────── */
/**
 * Minimal Turing HTTP client built on Node 20+'s global {@code fetch}.
 * Carries the {@code XSRF-TOKEN} cookie and matching header across
 * requests so the CSRF filter on {@code /api/ai-agent/**} is satisfied.
 *
 * @internal exported for tests only.
 */
export class TuringClient {
    baseUrl;
    fetchImpl;
    csrfToken = null;
    csrfHeaderName = "X-XSRF-TOKEN";
    sessionCookie = null;
    basicAuth;
    constructor(baseUrl, username, password, fetchImpl = fetch) {
        this.baseUrl = baseUrl;
        this.fetchImpl = fetchImpl;
        this.basicAuth = "Basic " + Buffer.from(`${username}:${password}`).toString("base64");
    }
    /**
     * First call — fetches a CSRF token AND establishes a session cookie.
     * Subsequent state-changing calls reuse both.
     */
    async authenticate() {
        const response = await this.fetchImpl(this.url("/api/csrf"), {
            method: "GET",
            headers: { Authorization: this.basicAuth, Accept: "application/json" },
        });
        if (!response.ok) {
            throw new HttpError(`Authentication failed (${response.status} ${response.statusText}). Check username/password.`, response.status);
        }
        this.captureSession(response);
        const body = (await response.json());
        this.csrfToken = body.token;
        if (body.headerName)
            this.csrfHeaderName = body.headerName;
    }
    async get(path) {
        const response = await this.fetchImpl(this.url(path), {
            method: "GET",
            headers: this.headers(),
        });
        return this.handle(response, "GET", path);
    }
    async post(path, body) {
        const response = await this.fetchImpl(this.url(path), {
            method: "POST",
            headers: { ...this.headers(), "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
        return this.handle(response, "POST", path);
    }
    async put(path, body) {
        const response = await this.fetchImpl(this.url(path), {
            method: "PUT",
            headers: { ...this.headers(), "Content-Type": "application/json" },
            body: JSON.stringify(body),
        });
        return this.handle(response, "PUT", path);
    }
    url(path) {
        return `${this.baseUrl}${path.startsWith("/") ? path : "/" + path}`;
    }
    headers() {
        const h = { Authorization: this.basicAuth, Accept: "application/json" };
        if (this.csrfToken)
            h[this.csrfHeaderName] = this.csrfToken;
        if (this.sessionCookie)
            h.Cookie = this.sessionCookie;
        return h;
    }
    async handle(response, method, path) {
        this.captureSession(response);
        if (!response.ok) {
            const detail = await response.text().catch(() => "");
            throw new HttpError(`${method} ${path} failed (${response.status} ${response.statusText})${detail ? ": " + truncate(detail, 240) : ""}`, response.status);
        }
        if (response.status === 204)
            return undefined;
        return (await response.json());
    }
    captureSession(response) {
        // node:fetch exposes Set-Cookie via getSetCookie() (Node 20+). We
        // collapse the cookies we care about (JSESSIONID + XSRF-TOKEN) into a
        // single Cookie header string used for the next request.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const getSetCookie = response.headers.getSetCookie;
        const cookies = typeof getSetCookie === "function" ? getSetCookie.call(response.headers) : [];
        if (cookies.length === 0)
            return;
        const parsed = new Map();
        if (this.sessionCookie) {
            for (const pair of this.sessionCookie.split("; ")) {
                const [k, v] = splitOnce(pair, "=");
                if (k && v !== undefined)
                    parsed.set(k, v);
            }
        }
        for (const raw of cookies) {
            const [pair] = raw.split(";");
            if (!pair)
                continue;
            const [k, v] = splitOnce(pair, "=");
            if (k && v !== undefined)
                parsed.set(k, v);
            if (k === "XSRF-TOKEN" && v)
                this.csrfToken = decodeURIComponent(v);
        }
        this.sessionCookie = [...parsed.entries()].map(([k, v]) => `${k}=${v}`).join("; ");
    }
}
export class HttpError extends Error {
    status;
    constructor(message, status) {
        super(message);
        this.status = status;
        this.name = "HttpError";
    }
}
function splitOnce(input, sep) {
    const idx = input.indexOf(sep);
    if (idx === -1)
        return [input, undefined];
    return [input.slice(0, idx).trim(), input.slice(idx + 1)];
}
function truncate(s, max) {
    return s.length <= max ? s : s.slice(0, max) + "…";
}
/* ─────────────────────── Payload assembly ─────────────────────── */
/**
 * Translates a {@link TranspiledFlow} into the {@code TurChatFlowImportDto}
 * shape the backend expects: top-level {@code chatFlow} carrying
 * {@code definitionJson} as a stringified graph, plus optional
 * {@code personas} and {@code slots} arrays the import endpoint dedups
 * against existing rows.
 *
 * @internal exported for tests only.
 */
export function buildImportPayload(flow) {
    const definitionJson = JSON.stringify({
        nodes: flow.graph.nodes,
        edges: flow.graph.edges,
    });
    const chatFlow = {
        name: flow.name,
        description: flow.description,
        definitionJson,
        enabled: 1,
        guardrailMethod: flow.guardrailMethod,
        triggerDescription: flow.triggerDescription,
        triggerMode: flow.triggerMode,
        triggerLanguage: flow.triggerLanguage,
    };
    if (flow.id)
        chatFlow.id = flow.id;
    const payload = { chatFlow };
    if (flow.slots && flow.slots.length > 0)
        payload.slots = flow.slots;
    if (flow.personas && flow.personas.length > 0)
        payload.personas = flow.personas;
    return payload;
}
/**
 * High-level deploy entry-point. Reads every {@code *.chat-flow.json} in
 * {@code inputDir}, looks up existing flows on the agent (unless
 * {@code config.createOnly}), and either POSTs to {@code /import} (new)
 * or PUTs to {@code /chat-flow/{id}} (existing match by name).
 *
 * <p>{@code mode: "bundle"} short-circuits the per-flow loop and sends
 * every payload through {@code POST /import-bundle} in one request — the
 * backend's atomic transaction wires cross-flow {@code subFlowId}
 * references in that path, so use bundle whenever flows reference each
 * other.
 */
export async function deployFlows(inputDir, client, config) {
    const resolved = resolve(inputDir);
    if (!existsSync(resolved) || !statSync(resolved).isDirectory()) {
        throw new Error(`Input directory not found: ${resolved}`);
    }
    const files = readdirSync(resolved)
        .filter((name) => name.endsWith(".chat-flow.json"))
        .map((name) => join(resolved, name));
    if (files.length === 0) {
        throw new Error(`No *.chat-flow.json files found in ${resolved} — run 'turing-flow-dsl build' first.`);
    }
    await client.authenticate();
    const flows = files.map((file) => ({
        file,
        transpiled: JSON.parse(readFileSync(file, "utf8")),
    }));
    if (config.mode === "bundle") {
        return deployBundle(client, config, flows);
    }
    return deploySingle(client, config, flows);
}
async function deploySingle(client, config, flows) {
    const existing = config.createOnly
        ? new Map()
        : await fetchExistingByName(client, config.agentId);
    const results = [];
    for (const { file, transpiled } of flows) {
        const payload = buildImportPayload(transpiled);
        const existingId = existing.get(transpiled.name);
        if (existingId) {
            const updated = await client.put(`/api/ai-agent/${config.agentId}/chat-flow/${existingId}`, { ...payload.chatFlow, id: existingId });
            results.push({ file, flowName: transpiled.name, action: "updated", flowId: updated.id ?? existingId });
        }
        else {
            const created = await client.post(`/api/ai-agent/${config.agentId}/chat-flow/import`, payload);
            results.push({ file, flowName: transpiled.name, action: "created", flowId: created.id ?? "" });
        }
    }
    return results;
}
async function deployBundle(client, config, flows) {
    const bundle = flows.map((entry) => buildImportPayload(entry.transpiled));
    const created = await client.post(`/api/ai-agent/${config.agentId}/chat-flow/import-bundle`, bundle);
    return flows.map((entry, index) => ({
        file: entry.file,
        flowName: entry.transpiled.name,
        action: "created",
        flowId: created[index]?.id ?? "",
    }));
}
async function fetchExistingByName(client, agentId) {
    const list = await client.get(`/api/ai-agent/${agentId}/chat-flow`);
    const byName = new Map();
    for (const flow of list) {
        if (flow.id && flow.name)
            byName.set(flow.name, flow.id);
    }
    return byName;
}
/**
 * Parses the {@code argv} tail (everything after the {@code deploy}
 * subcommand) into a {@link DeployArgs}.
 *
 * @internal exported for tests only.
 */
export function parseDeployArgs(argv) {
    const args = [...argv];
    let inputDir = "";
    const flags = {};
    for (let i = 0; i < args.length; i++) {
        const token = args[i];
        if (!token)
            continue;
        switch (token) {
            case "--url":
                flags.url = requireNext(args, i++, "--url");
                break;
            case "--agent":
            case "--agent-id":
                flags.agentId = requireNext(args, i++, "--agent");
                break;
            case "--username":
            case "--user":
                flags.username = requireNext(args, i++, "--username");
                break;
            case "--mode": {
                const mode = requireNext(args, i++, "--mode");
                if (mode !== "single" && mode !== "bundle") {
                    throw new Error(`--mode must be 'single' or 'bundle', got '${mode}'.`);
                }
                flags.mode = mode;
                break;
            }
            case "--create-only":
                flags.createOnly = true;
                break;
            default:
                if (token.startsWith("-")) {
                    throw new Error(`Unknown deploy option: ${token}`);
                }
                if (inputDir) {
                    throw new Error(`Unexpected positional argument: ${token}`);
                }
                inputDir = token;
        }
    }
    if (!inputDir) {
        throw new Error("Missing input directory. Usage: turing-flow-dsl deploy <dir> [flags]");
    }
    return { inputDir, flags };
}
function requireNext(args, index, optionName) {
    const next = args[index + 1];
    if (!next)
        throw new Error(`${optionName} expects a value.`);
    return next;
}
/**
 * Glue function the CLI calls. Pure-data inputs so the test suite can
 * drive the whole pipeline with a stub fetch and a stub prompt.
 */
export async function runDeploy(args, options) {
    const log = options.log ?? ((line) => console.log(line));
    const pkg = loadPackageConfig(options.cwd);
    const config = resolveConfig(pkg, options.env, args.flags);
    if (!config.password) {
        const prompter = options.prompt ?? promptPassword;
        config.password = await prompter(`Password for ${config.username} at ${config.url}: `);
    }
    if (!config.password) {
        throw new Error("Password is required (set TURING_PASSWORD or enter at the prompt).");
    }
    log(`Deploying to ${config.url} (agent ${config.agentId}, user ${config.username}, mode ${config.mode})`);
    const client = new TuringClient(config.url, config.username, config.password, options.fetchImpl);
    const results = await deployFlows(args.inputDir, client, config);
    for (const r of results) {
        const tag = r.action === "created" ? "✓ created" : "↻ updated";
        log(`${tag}  ${basename(r.file)}  →  ${r.flowName} (${r.flowId || "no id"})`);
    }
    log(`${results.length} flow(s) deployed.`);
    return { ok: results.length, failed: 0 };
}
//# sourceMappingURL=deploy.js.map