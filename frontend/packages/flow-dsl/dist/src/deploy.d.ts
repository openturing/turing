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
import type { TranspiledFlow } from "./types.js";
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
export declare function loadPackageConfig(cwd: string): PackageTuringConfig;
/**
 * Merges package.json → env vars → CLI flags (last wins). Throws if a
 * required field is still missing so the CLI surface stays declarative
 * (no "did you forget…?" warnings sprinkled through the upload path).
 *
 * @internal exported for tests only.
 */
export declare function resolveConfig(pkg: PackageTuringConfig, env: Record<string, string | undefined>, flags: DeployFlags): DeployConfig;
/**
 * Reads a password from the TTY with input echo suppressed. Throws when
 * stdin is not a TTY (CI without {@code TURING_PASSWORD} env hits this
 * path — fail loudly instead of hanging on a {@code read} that nobody
 * will answer).
 *
 * @internal exported for tests only.
 */
export declare function promptPassword(promptText: string): Promise<string>;
/**
 * Minimal Turing HTTP client built on Node 20+'s global {@code fetch}.
 * Carries the {@code XSRF-TOKEN} cookie and matching header across
 * requests so the CSRF filter on {@code /api/ai-agent/**} is satisfied.
 *
 * @internal exported for tests only.
 */
export declare class TuringClient {
    private readonly baseUrl;
    private readonly fetchImpl;
    private csrfToken;
    private csrfHeaderName;
    private sessionCookie;
    private readonly basicAuth;
    constructor(baseUrl: string, username: string, password: string, fetchImpl?: typeof fetch);
    /**
     * First call — fetches a CSRF token AND establishes a session cookie.
     * Subsequent state-changing calls reuse both.
     */
    authenticate(): Promise<void>;
    get<T>(path: string): Promise<T>;
    post<T>(path: string, body: unknown): Promise<T>;
    put<T>(path: string, body: unknown): Promise<T>;
    private url;
    private headers;
    private handle;
    private captureSession;
}
export declare class HttpError extends Error {
    readonly status: number;
    constructor(message: string, status: number);
}
/**
 * Translates a {@link TranspiledFlow} into the {@code TurChatFlowImportDto}
 * shape the backend expects: top-level {@code chatFlow} carrying
 * {@code definitionJson} as a stringified graph, plus optional
 * {@code personas} and {@code slots} arrays the import endpoint dedups
 * against existing rows.
 *
 * @internal exported for tests only.
 */
export declare function buildImportPayload(flow: TranspiledFlow): ChatFlowImportPayload;
export interface ChatFlowImportPayload {
    chatFlow: Record<string, unknown>;
    slots?: unknown[];
    personas?: unknown[];
}
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
export declare function deployFlows(inputDir: string, client: TuringClient, config: DeployConfig): Promise<DeployResult[]>;
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
export declare function parseDeployArgs(argv: readonly string[]): DeployArgs;
/**
 * Glue function the CLI calls. Pure-data inputs so the test suite can
 * drive the whole pipeline with a stub fetch and a stub prompt.
 */
export declare function runDeploy(args: DeployArgs, options: {
    cwd: string;
    env: Record<string, string | undefined>;
    fetchImpl?: typeof fetch;
    prompt?: (text: string) => Promise<string>;
    log?: (line: string) => void;
}): Promise<{
    ok: number;
    failed: number;
}>;
//# sourceMappingURL=deploy.d.ts.map