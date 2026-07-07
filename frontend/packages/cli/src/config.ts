/**
 * Connection + project configuration for the {@code turing} CLI.
 *
 * <p>A scaffolded project carries a {@code turing.config.json} at its root:
 * <pre>{@code
 *   {
 *     "name": "my-agent",
 *     "agentId": "abc-123-uuid",      // filled in after first deploy
 *     "default": "local",
 *     "envs": {
 *       "local":   { "url": "http://localhost:2700" },
 *       "staging": { "url": "https://staging.turing.example.com" }
 *     }
 *   }
 * }</pre>
 *
 * <p>Resolution precedence (last wins): {@code turing.config.json} →
 * environment variables → CLI flags. The token / password are never written
 * to disk; supply them via {@code TURING_TOKEN} / {@code TURING_PASSWORD} (CI)
 * or the interactive prompt.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { createInterface } from "node:readline";

import type { TuringAuth } from "./client.js";

export interface ProjectEnv {
  url?: string;
}

export interface ProjectConfig {
  name?: string;
  agentId?: string;
  /** Name of the default env block (falls back to "local"). */
  default?: string;
  envs?: Record<string, ProjectEnv>;
}

/** A located project config plus the directory it was found in. */
export interface LoadedProject {
  config: ProjectConfig;
  /** Absolute directory containing {@code turing.config.json}, or the cwd when none was found. */
  dir: string;
  /** True when an actual {@code turing.config.json} was read from disk. */
  found: boolean;
}

/**
 * Walks up from {@code cwd} looking for {@code turing.config.json}. Returns an
 * empty config rooted at {@code cwd} when none is found — flags / env vars can
 * still supply every field.
 */
export function loadProject(cwd: string): LoadedProject {
  let dir = resolve(cwd);
  for (;;) {
    const candidate = join(dir, "turing.config.json");
    if (existsSync(candidate)) {
      try {
        const config = JSON.parse(readFileSync(candidate, "utf8")) as ProjectConfig;
        return { config, dir, found: true };
      } catch {
        return { config: {}, dir, found: true };
      }
    }
    const parent = dirname(dir);
    if (parent === dir) return { config: {}, dir: resolve(cwd), found: false };
    dir = parent;
  }
}

/** Per-call flag overrides shared by {@code deploy}, {@code logs}, {@code eval}. */
export interface ConnectionFlags {
  url?: string;
  env?: string;
  token?: string;
  username?: string;
  password?: string;
  agentId?: string;
}

/** Fully resolved connection used to build a {@link TuringClient}. */
export interface Connection {
  url: string;
  auth: TuringAuth;
  agentId?: string;
}

/**
 * Merges project config → env vars → flags into a {@link Connection}.
 *
 * <p>When {@code TURING_TOKEN}/{@code --token} is present, dev-token auth is
 * used. Otherwise HTTP Basic with the resolved username; the password is taken
 * from {@code TURING_PASSWORD}/{@code --password} or, if absent and
 * {@code prompt} is provided, requested interactively. Throws when neither a
 * token nor a username can be resolved, or when the URL is missing.
 */
export async function resolveConnection(
  project: ProjectConfig,
  env: Record<string, string | undefined>,
  flags: ConnectionFlags,
  prompt?: (text: string) => Promise<string>,
): Promise<Connection> {
  const envName = flags.env ?? env.TURING_ENV ?? project.default ?? "local";
  const envBlock = project.envs?.[envName] ?? {};

  const url = flags.url ?? env.TURING_URL ?? envBlock.url;
  if (!url) {
    throw new Error(
      `Missing instance URL. Provide --url, set TURING_URL, or add envs.${envName}.url to turing.config.json.`,
    );
  }
  const baseUrl = url.replace(/\/+$/, "");
  const agentId = flags.agentId ?? env.TURING_AGENT_ID ?? project.agentId;

  const token = flags.token ?? env.TURING_TOKEN;
  if (token) {
    return { url: baseUrl, auth: { kind: "token", token }, agentId };
  }

  const username = flags.username ?? env.TURING_USERNAME;
  if (!username) {
    throw new Error(
      "No credentials. Set TURING_TOKEN (recommended for CI) or provide --username (+ --password / TURING_PASSWORD).",
    );
  }
  let password = flags.password ?? env.TURING_PASSWORD;
  if (!password && prompt) {
    password = await prompt(`Password for ${username} at ${baseUrl}: `);
  }
  if (!password) {
    throw new Error("Password is required (set TURING_PASSWORD or enter it at the prompt).");
  }
  return { url: baseUrl, auth: { kind: "basic", username, password }, agentId };
}

/**
 * Reads a password from the TTY with echo suppressed. Throws when stdin is not
 * a TTY (CI without {@code TURING_PASSWORD}/{@code TURING_TOKEN} hits this —
 * fail loudly rather than hang).
 */
export async function promptPassword(promptText: string): Promise<string> {
  if (!process.stdin.isTTY) {
    throw new Error("stdin is not a TTY — set TURING_TOKEN or TURING_PASSWORD when running unattended.");
  }
  const rl = createInterface({ input: process.stdin, output: process.stdout, terminal: true });
  process.stdout.write(promptText);
  const muted = new MutedStdout(process.stdout);
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

class MutedStdout {
  constructor(private readonly inner: NodeJS.WritableStream) {}
  write(chunk: string | Uint8Array): boolean {
    const text = typeof chunk === "string" ? chunk : Buffer.from(chunk).toString("utf8");
    if (text.includes("\n")) return this.inner.write("\n");
    return true;
  }
  on(_event: string, _listener: (...args: unknown[]) => void): this {
    return this;
  }
}
