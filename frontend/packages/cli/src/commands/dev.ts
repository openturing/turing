/**
 * {@code turing dev} — boots a local Turing stack via Docker Compose and,
 * with {@code --watch}, redeploys the project whenever a source file changes
 * (the pragmatic "hot reload of flow + tools" from §IX.8.a).
 *
 * <p>The differentiator over a bare SDK harness: one command brings the whole
 * stack up. By default it attaches to {@code docker compose up}; {@code --watch}
 * brings it up detached and then watches {@code agent.json} / {@code flows/} /
 * {@code tools/} / {@code skills/}, debouncing a redeploy on change.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import { spawn } from "node:child_process";
import { existsSync, watch } from "node:fs";
import { join } from "node:path";

export interface DevArgs {
  /** Compose file override (defaults to docker-compose.yml | docker-compose.dev.yml). */
  file?: string;
  detach?: boolean;
  watch?: boolean;
}

/** Resolves the compose file to use, or throws when none is present. */
export function resolveComposeFile(projectDir: string, override?: string): string {
  if (override) {
    const abs = join(projectDir, override);
    if (!existsSync(abs)) throw new Error(`Compose file not found: ${abs}`);
    return abs;
  }
  for (const name of ["docker-compose.yml", "docker-compose.yaml", "docker-compose.dev.yml"]) {
    const abs = join(projectDir, name);
    if (existsSync(abs)) return abs;
  }
  throw new Error("No docker-compose file found. Run `turing init` or pass --file.");
}

/** Spawns `docker compose -f <file> up [-d]`, resolving with the exit code. */
export function composeUp(file: string, detach: boolean, log: (line: string) => void): Promise<number> {
  const args = ["compose", "-f", file, "up"];
  if (detach) args.push("-d");
  log(`$ docker ${args.join(" ")}`);
  return new Promise((resolve, reject) => {
    const child = spawn("docker", args, {
      stdio: "inherit",
      shell: process.platform === "win32",
    });
    child.on("error", (err) => {
      reject(
        (err as NodeJS.ErrnoException).code === "ENOENT"
          ? new Error("`docker` was not found on PATH. Install Docker Desktop / Engine first.")
          : err,
      );
    });
    child.on("exit", (code) => resolve(code ?? 0));
  });
}

/**
 * Watches the project's source dirs and invokes {@code onChange} (debounced)
 * whenever something changes. Returns a stop function.
 */
export function watchProject(projectDir: string, onChange: () => void, debounceMs = 400): () => void {
  const targets = ["agent.json", "flows", "tools", "skills"]
    .map((t) => join(projectDir, t))
    .filter((p) => existsSync(p));
  let timer: NodeJS.Timeout | null = null;
  const fire = () => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(onChange, debounceMs);
  };
  const watchers = targets.map((t) => watch(t, { recursive: true }, fire));
  return () => {
    if (timer) clearTimeout(timer);
    for (const w of watchers) w.close();
  };
}
