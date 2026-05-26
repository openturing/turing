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

import type { TranspiledFlow } from "./types.js";

/* ─────────────────────── Config resolution ─────────────────────── */

/**
 * Subset of fields the deploy CLI reads from a project's {@code package.json}.
 *
 * <p>Example:
 * <pre>{@code
 *   {
 *     "turing": {
 *       "url": "https://turing.empresa.com",
 *       "agentId": "abc-123-uuid",
 *       "username": "alexandre.oliveira",
 *       "mode": "bundle"
 *     }
 *   }
 * }</pre>
 */
export interface PackageTuringConfig {
  url?: string;
  agentId?: string;
  username?: string;
  /** Default deploy mode — {@code "single"} (per-file POST) or {@code "bundle"} (one round-trip). */
  mode?: DeployMode;
  /** When true, never PUT existing flows by name match; always POST new ones. */
  createOnly?: boolean;
}

export type DeployMode = "single" | "bundle";

/**
 * Fully resolved deploy configuration after merging package.json, env, and
 * CLI flags. Missing required fields cause the CLI to print a friendly
 * error and exit 1 — see {@link resolveConfig}.
 */
export interface DeployConfig {
  url: string;
  agentId: string;
  username: string;
  password: string;
  mode: DeployMode;
  createOnly: boolean;
}

/**
 * Per-call overrides the CLI parses out of {@code process.argv}.
 *
 * @internal exported for tests only.
 */
export interface DeployFlags {
  url?: string;
  agentId?: string;
  username?: string;
  password?: string;
  mode?: DeployMode;
  createOnly?: boolean;
}

/**
 * Loads the {@code "turing"} block from the nearest {@code package.json}
 * walking up from {@code cwd}. Returns an empty object when no package.json
 * is found or when it has no {@code turing} key — the CLI can still proceed
 * if env vars / flags supply every field.
 *
 * @internal exported for tests only.
 */
export function loadPackageConfig(cwd: string): PackageTuringConfig {
  let dir = resolve(cwd);
  while (true) {
    const candidate = join(dir, "package.json");
    if (existsSync(candidate)) {
      try {
        const parsed = JSON.parse(readFileSync(candidate, "utf8")) as { turing?: PackageTuringConfig };
        return parsed.turing ?? {};
      } catch {
        return {};
      }
    }
    const parent = dirname(dir);
    if (parent === dir) return {};
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
export function resolveConfig(
  pkg: PackageTuringConfig,
  env: Record<string, string | undefined>,
  flags: DeployFlags,
): DeployConfig {
  const url = flags.url ?? env.TURING_URL ?? pkg.url;
  const agentId = flags.agentId ?? env.TURING_AGENT_ID ?? pkg.agentId;
  const username = flags.username ?? env.TURING_USERNAME ?? pkg.username;
  const password = flags.password ?? env.TURING_PASSWORD;
  const mode: DeployMode = flags.mode ?? (env.TURING_MODE as DeployMode | undefined) ?? pkg.mode ?? "bundle";
  const createOnly = flags.createOnly ?? envBool(env.TURING_CREATE_ONLY) ?? pkg.createOnly ?? false;

  const missing: string[] = [];
  if (!url) missing.push("url (package.json turing.url, TURING_URL, or --url)");
  if (!agentId) missing.push("agentId (package.json turing.agentId, TURING_AGENT_ID, or --agent)");
  if (!username) missing.push("username (package.json turing.username, TURING_USERNAME, or --username)");
  if (missing.length > 0) {
    throw new Error(`Missing required deploy config: ${missing.join("; ")}`);
  }
  if (mode !== "single" && mode !== "bundle") {
    throw new Error(`Invalid mode '${mode}'. Use 'single' or 'bundle'.`);
  }
  // password is resolved separately (interactive prompt) when not supplied
  // via env/flag — the caller does that step.
  return {
    url: url!.replace(/\/+$/, ""),
    agentId: agentId!,
    username: username!,
    password: password ?? "",
    mode,
    createOnly,
  };
}

function envBool(value: string | undefined): boolean | undefined {
  if (value === undefined) return undefined;
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
export async function promptPassword(promptText: string): Promise<string> {
  if (!process.stdin.isTTY) {
    throw new Error(
      "stdin is not a TTY — set TURING_PASSWORD env var when running in CI.",
    );
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
  (rl as unknown as { output: NodeJS.WritableStream }).output = muted as unknown as NodeJS.WritableStream;
  return new Promise<string>((accept, reject) => {
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
  constructor(private readonly inner: NodeJS.WritableStream) {}
  write(chunk: string | Uint8Array): boolean {
    const text = typeof chunk === "string" ? chunk : Buffer.from(chunk).toString("utf8");
    if (text.includes("\n")) {
      return this.inner.write("\n");
    }
    return true;
  }
  on(_event: string, _listener: (...args: unknown[]) => void): this {
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
  private csrfToken: string | null = null;
  private csrfHeaderName = "X-XSRF-TOKEN";
  private sessionCookie: string | null = null;
  private readonly basicAuth: string;

  constructor(
    private readonly baseUrl: string,
    username: string,
    password: string,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {
    this.basicAuth = "Basic " + Buffer.from(`${username}:${password}`).toString("base64");
  }

  /**
   * First call — fetches a CSRF token AND establishes a session cookie.
   * Subsequent state-changing calls reuse both.
   */
  async authenticate(): Promise<void> {
    const response = await this.fetchImpl(this.url("/api/csrf"), {
      method: "GET",
      headers: { Authorization: this.basicAuth, Accept: "application/json" },
    });
    if (!response.ok) {
      throw new HttpError(
        `Authentication failed (${response.status} ${response.statusText}). Check username/password.`,
        response.status,
      );
    }
    this.captureSession(response);
    const body = (await response.json()) as { token: string; headerName?: string };
    this.csrfToken = body.token;
    if (body.headerName) this.csrfHeaderName = body.headerName;
  }

  async get<T>(path: string): Promise<T> {
    const response = await this.fetchImpl(this.url(path), {
      method: "GET",
      headers: this.headers(),
    });
    return this.handle<T>(response, "GET", path);
  }

  async post<T>(path: string, body: unknown): Promise<T> {
    const response = await this.fetchImpl(this.url(path), {
      method: "POST",
      headers: { ...this.headers(), "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return this.handle<T>(response, "POST", path);
  }

  async put<T>(path: string, body: unknown): Promise<T> {
    const response = await this.fetchImpl(this.url(path), {
      method: "PUT",
      headers: { ...this.headers(), "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return this.handle<T>(response, "PUT", path);
  }

  private url(path: string): string {
    return `${this.baseUrl}${path.startsWith("/") ? path : "/" + path}`;
  }

  private headers(): Record<string, string> {
    const h: Record<string, string> = { Authorization: this.basicAuth, Accept: "application/json" };
    if (this.csrfToken) h[this.csrfHeaderName] = this.csrfToken;
    if (this.sessionCookie) h.Cookie = this.sessionCookie;
    return h;
  }

  private async handle<T>(response: Response, method: string, path: string): Promise<T> {
    this.captureSession(response);
    if (!response.ok) {
      const detail = await response.text().catch(() => "");
      throw new HttpError(
        `${method} ${path} failed (${response.status} ${response.statusText})${detail ? ": " + truncate(detail, 240) : ""}`,
        response.status,
      );
    }
    if (response.status === 204) return undefined as T;
    return (await response.json()) as T;
  }

  private captureSession(response: Response): void {
    // node:fetch exposes Set-Cookie via getSetCookie() (Node 20+). We
    // collapse the cookies we care about (JSESSIONID + XSRF-TOKEN) into a
    // single Cookie header string used for the next request.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const getSetCookie = (response.headers as unknown as { getSetCookie?: () => string[] }).getSetCookie;
    const cookies: string[] = typeof getSetCookie === "function" ? getSetCookie.call(response.headers) : [];
    if (cookies.length === 0) return;
    const parsed = new Map<string, string>();
    if (this.sessionCookie) {
      for (const pair of this.sessionCookie.split("; ")) {
        const [k, v] = splitOnce(pair, "=");
        if (k && v !== undefined) parsed.set(k, v);
      }
    }
    for (const raw of cookies) {
      const [pair] = raw.split(";");
      if (!pair) continue;
      const [k, v] = splitOnce(pair, "=");
      if (k && v !== undefined) parsed.set(k, v);
      if (k === "XSRF-TOKEN" && v) this.csrfToken = decodeURIComponent(v);
    }
    this.sessionCookie = [...parsed.entries()].map(([k, v]) => `${k}=${v}`).join("; ");
  }
}

export class HttpError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = "HttpError";
  }
}

function splitOnce(input: string, sep: string): [string, string | undefined] {
  const idx = input.indexOf(sep);
  if (idx === -1) return [input, undefined];
  return [input.slice(0, idx).trim(), input.slice(idx + 1)];
}

function truncate(s: string, max: number): string {
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
export function buildImportPayload(flow: TranspiledFlow): ChatFlowImportPayload {
  const definitionJson = JSON.stringify({
    nodes: flow.graph.nodes,
    edges: flow.graph.edges,
  });
  const chatFlow: Record<string, unknown> = {
    name: flow.name,
    description: flow.description,
    definitionJson,
    enabled: 1,
    guardrailMethod: flow.guardrailMethod,
    triggerDescription: flow.triggerDescription,
    triggerMode: flow.triggerMode,
    triggerLanguage: flow.triggerLanguage,
  };
  if (flow.id) chatFlow.id = flow.id;
  const payload: ChatFlowImportPayload = { chatFlow };
  if (flow.slots && flow.slots.length > 0) payload.slots = flow.slots;
  if (flow.personas && flow.personas.length > 0) payload.personas = flow.personas;
  return payload;
}

export interface ChatFlowImportPayload {
  chatFlow: Record<string, unknown>;
  slots?: unknown[];
  personas?: unknown[];
}

/* ─────────────────────── Deploy orchestration ─────────────────────── */

/**
 * Single per-flow outcome returned by {@link deployFlows}. The CLI prints
 * one of these per file plus a summary line at the end.
 */
export interface DeployResult {
  file: string;
  flowName: string;
  action: "created" | "updated";
  flowId: string;
}

export interface ExistingFlowSummary {
  id?: string;
  name?: string;
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
export async function deployFlows(
  inputDir: string,
  client: TuringClient,
  config: DeployConfig,
): Promise<DeployResult[]> {
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
  const flows: { file: string; transpiled: TranspiledFlow }[] = files.map((file) => ({
    file,
    transpiled: JSON.parse(readFileSync(file, "utf8")) as TranspiledFlow,
  }));

  if (config.mode === "bundle") {
    return deployBundle(client, config, flows);
  }
  return deploySingle(client, config, flows);
}

async function deploySingle(
  client: TuringClient,
  config: DeployConfig,
  flows: { file: string; transpiled: TranspiledFlow }[],
): Promise<DeployResult[]> {
  const existing = config.createOnly
    ? new Map<string, string>()
    : await fetchExistingByName(client, config.agentId);

  const results: DeployResult[] = [];
  for (const { file, transpiled } of flows) {
    const payload = buildImportPayload(transpiled);
    const existingId = existing.get(transpiled.name);
    if (existingId) {
      const updated = await client.put<{ id?: string; name?: string }>(
        `/api/ai-agent/${config.agentId}/chat-flow/${existingId}`,
        { ...payload.chatFlow, id: existingId },
      );
      results.push({ file, flowName: transpiled.name, action: "updated", flowId: updated.id ?? existingId });
    } else {
      const created = await client.post<{ id?: string; name?: string }>(
        `/api/ai-agent/${config.agentId}/chat-flow/import`,
        payload,
      );
      results.push({ file, flowName: transpiled.name, action: "created", flowId: created.id ?? "" });
    }
  }
  return results;
}

async function deployBundle(
  client: TuringClient,
  config: DeployConfig,
  flows: { file: string; transpiled: TranspiledFlow }[],
): Promise<DeployResult[]> {
  const bundle = flows.map((entry) => buildImportPayload(entry.transpiled));
  const created = await client.post<{ id?: string; name?: string }[]>(
    `/api/ai-agent/${config.agentId}/chat-flow/import-bundle`,
    bundle,
  );
  return flows.map((entry, index) => ({
    file: entry.file,
    flowName: entry.transpiled.name,
    action: "created" as const,
    flowId: created[index]?.id ?? "",
  }));
}

async function fetchExistingByName(client: TuringClient, agentId: string): Promise<Map<string, string>> {
  const list = await client.get<ExistingFlowSummary[]>(`/api/ai-agent/${agentId}/chat-flow`);
  const byName = new Map<string, string>();
  for (const flow of list) {
    if (flow.id && flow.name) byName.set(flow.name, flow.id);
  }
  return byName;
}

/* ─────────────────────── CLI entry ─────────────────────── */

/**
 * Parsed shape of {@code turing-flow-dsl deploy <dir> [flags]} ready to
 * hand to {@link runDeploy}.
 *
 * @internal exported for tests only.
 */
export interface DeployArgs {
  inputDir: string;
  flags: DeployFlags;
}

/**
 * Parses the {@code argv} tail (everything after the {@code deploy}
 * subcommand) into a {@link DeployArgs}.
 *
 * @internal exported for tests only.
 */
export function parseDeployArgs(argv: readonly string[]): DeployArgs {
  const args = [...argv];
  let inputDir = "";
  const flags: DeployFlags = {};
  for (let i = 0; i < args.length; i++) {
    const token = args[i];
    if (!token) continue;
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

function requireNext(args: string[], index: number, optionName: string): string {
  const next = args[index + 1];
  if (!next) throw new Error(`${optionName} expects a value.`);
  return next;
}

/**
 * Glue function the CLI calls. Pure-data inputs so the test suite can
 * drive the whole pipeline with a stub fetch and a stub prompt.
 */
export async function runDeploy(
  args: DeployArgs,
  options: {
    cwd: string;
    env: Record<string, string | undefined>;
    fetchImpl?: typeof fetch;
    prompt?: (text: string) => Promise<string>;
    log?: (line: string) => void;
  },
): Promise<{ ok: number; failed: number }> {
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
  log(
    `Deploying to ${config.url} (agent ${config.agentId}, user ${config.username}, mode ${config.mode})`,
  );
  const client = new TuringClient(config.url, config.username, config.password, options.fetchImpl);
  const results = await deployFlows(args.inputDir, client, config);
  for (const r of results) {
    const tag = r.action === "created" ? "✓ created" : "↻ updated";
    log(`${tag}  ${basename(r.file)}  →  ${r.flowName} (${r.flowId || "no id"})`);
  }
  log(`${results.length} flow(s) deployed.`);
  return { ok: results.length, failed: 0 };
}
