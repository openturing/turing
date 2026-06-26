/**
 * {@code turing init <name>} — scaffolds a new agent project directory.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";

import { scaffoldFiles } from "../scaffold.js";

export interface InitArgs {
  name: string;
  /** Target directory (defaults to ./<name>). */
  dir?: string;
  force?: boolean;
}

export function parseInitArgs(argv: readonly string[]): InitArgs {
  let name = "";
  let dir: string | undefined;
  let force = false;
  for (let i = 0; i < argv.length; i++) {
    const token = argv[i]!;
    if (token === "--dir") {
      dir = argv[++i];
    } else if (token === "--force" || token === "-f") {
      force = true;
    } else if (token.startsWith("-")) {
      throw new Error(`Unknown init option: ${token}`);
    } else if (!name) {
      name = token;
    } else {
      throw new Error(`Unexpected argument: ${token}`);
    }
  }
  if (!name) throw new Error("Usage: turing init <name> [--dir <path>] [--force]");
  return { name, dir, force };
}

export function runInit(args: InitArgs, cwd: string, log: (line: string) => void): void {
  const targetDir = resolve(cwd, args.dir ?? args.name);
  const files = scaffoldFiles(args.name);
  if (!args.force && existsSync(join(targetDir, "turing.config.json"))) {
    throw new Error(`A Turing project already exists at ${targetDir} (use --force to overwrite).`);
  }
  mkdirSync(targetDir, { recursive: true });
  for (const [relPath, content] of Object.entries(files)) {
    const abs = join(targetDir, relPath);
    mkdirSync(dirname(abs), { recursive: true });
    writeFileSync(abs, content, "utf8");
    log(`  + ${relPath}`);
  }
  log(`\nScaffolded '${args.name}' at ${targetDir}`);
  log("Next: cd into it, then `turing dev` (local stack) and `turing deploy`.");
}
