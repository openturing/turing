#!/usr/bin/env node
/**
 * `turing` — the Viglet Turing ES developer CLI.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code init <name>} — scaffold a new agent project.</li>
 *   <li>{@code dev} — boot a local Turing stack (Docker Compose), optionally
 *       redeploying on file change ({@code --watch}).</li>
 *   <li>{@code deploy} — push agent + flows + tools + skills to an instance.</li>
 *   <li>{@code logs --conversation <id>} — tail live chat events.</li>
 *   <li>{@code eval [path]} — run YAML eval suites ({@code --watch} to re-run).</li>
 *   <li>{@code eval record <conversationId>} — freeze a conversation as a fixture.</li>
 * </ul>
 *
 * <p>Zero runtime dependencies — Node 22+ built-ins only. Connection details
 * resolve from {@code turing.config.json} → env vars → flags (see {@code config.ts}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import { writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { TuringClient } from "./client.js";
import {
  loadProject,
  promptPassword,
  resolveConnection,
  type ConnectionFlags,
} from "./config.js";
import { ClientEvalBackend } from "./eval/backend.js";
import { recordFixture } from "./eval/record.js";
import { composeUp, resolveComposeFile, watchProject, type DevArgs } from "./commands/dev.js";
import { deployProject } from "./commands/deploy.js";
import { discoverSuiteFiles, runEvalFiles } from "./commands/eval.js";
import { parseInitArgs, runInit } from "./commands/init.js";
import { runLogs } from "./commands/logs.js";

const log = (line: string) => console.log(line);

/* ─────────────────────── shared flag parsing ─────────────────────── */

/** Splits off the recognized connection flags, leaving positionals + the rest. */
function extractConnectionFlags(argv: readonly string[]): { flags: ConnectionFlags; positionals: string[]; extra: Record<string, string | boolean> } {
  const flags: ConnectionFlags = {};
  const positionals: string[] = [];
  const extra: Record<string, string | boolean> = {};
  const args = [...argv];
  for (let i = 0; i < args.length; i++) {
    const t = args[i]!;
    const eq = t.startsWith("--") ? t.indexOf("=") : -1;
    const key = eq === -1 ? t : t.slice(0, eq);
    const inlineVal = eq === -1 ? undefined : t.slice(eq + 1);
    const next = () => inlineVal ?? args[++i];
    switch (key) {
      case "--url": flags.url = next(); break;
      case "--env": flags.env = next(); break;
      case "--token": flags.token = next(); break;
      case "--username": case "--user": flags.username = next(); break;
      case "--password": flags.password = next(); break;
      case "--agent": case "--agent-id": flags.agentId = next(); break;
      default:
        if (key.startsWith("--")) {
          extra[key.slice(2)] = inlineVal ?? true;
        } else {
          positionals.push(t);
        }
    }
  }
  return { flags, positionals, extra };
}

/** Builds an authenticated-capable client + resolved agent id from the env. */
async function connect(flags: ConnectionFlags, cwd: string) {
  const project = loadProject(cwd);
  const connection = await resolveConnection(project.config, process.env, flags, promptPassword);
  const client = new TuringClient(connection.url, connection.auth);
  return { project, connection, client };
}

/* ─────────────────────── command dispatch ─────────────────────── */

async function main(argv: string[]): Promise<void> {
  const command = argv[0];
  const rest = argv.slice(1);
  switch (command) {
    case "init":
      runInit(parseInitArgs(rest), process.cwd(), log);
      return;
    case "dev":
      await devCommand(rest);
      return;
    case "deploy":
      await deployCommand(rest);
      return;
    case "logs":
      await logsCommand(rest);
      return;
    case "eval":
      await evalCommand(rest);
      return;
    case "version":
    case "--version":
    case "-v":
      log("turing CLI (Viglet Turing ES)");
      return;
    case undefined:
    case "help":
    case "--help":
    case "-h":
      printHelp();
      return;
    default:
      throw new Error(`Unknown command: ${command}. Run \`turing help\`.`);
  }
}

async function devCommand(rest: string[]): Promise<void> {
  const { flags, extra } = extractConnectionFlags(rest);
  const devArgs: DevArgs = {
    file: typeof extra.file === "string" ? extra.file : undefined,
    detach: extra.detach === true || extra.d === true,
    watch: extra.watch === true,
  };
  const cwd = process.cwd();
  const file = resolveComposeFile(cwd, devArgs.file);

  if (!devArgs.watch) {
    const code = await composeUp(file, devArgs.detach ?? false, log);
    process.exitCode = code;
    return;
  }

  // Watch mode: bring the stack up detached, then redeploy on change.
  await composeUp(file, true, log);
  log("Stack is up (detached). Watching for changes — Ctrl-C to stop.");
  let connected: Awaited<ReturnType<typeof connect>> | null = null;
  try {
    connected = await connect(flags, cwd);
  } catch (err) {
    log(`(watch) redeploy disabled: ${(err as Error).message}`);
  }
  const stop = watchProject(cwd, () => {
    if (!connected) return;
    void redeploy(connected).catch((e) => log(`✗ redeploy failed: ${(e as Error).message}`));
  });
  await new Promise<void>((res) => {
    process.on("SIGINT", () => {
      stop();
      res();
    });
  });
}

async function redeploy(connected: Awaited<ReturnType<typeof connect>>): Promise<void> {
  log("↻ change detected — redeploying…");
  await deployProject({
    client: connected.client,
    projectDir: connected.project.dir,
    agentId: connected.connection.agentId,
    configPath: resolve(connected.project.dir, "turing.config.json"),
    log,
  });
}

async function deployCommand(rest: string[]): Promise<void> {
  const { flags } = extractConnectionFlags(rest);
  const cwd = process.cwd();
  const connected = await connect(flags, cwd);
  log(`Deploying to ${connected.connection.url}…`);
  const summary = await deployProject({
    client: connected.client,
    projectDir: connected.project.dir,
    agentId: connected.connection.agentId,
    configPath: resolve(connected.project.dir, "turing.config.json"),
    log,
  });
  log(`\nDeployed agent ${summary.agentId}: ${summary.flows} flow(s), ${summary.tools} tool(s), ${summary.skills} skill(s).`);
}

async function logsCommand(rest: string[]): Promise<void> {
  const { flags, extra } = extractConnectionFlags(rest);
  const conversationId = typeof extra.conversation === "string" ? extra.conversation : undefined;
  if (!conversationId) throw new Error("Usage: turing logs --conversation <id> [--slots]");
  const connected = await connect(flags, process.cwd());
  const controller = new AbortController();
  process.on("SIGINT", () => controller.abort());
  await runLogs(connected.client, { conversationId, slots: extra.slots === true }, log, controller.signal);
}

async function evalCommand(rest: string[]): Promise<void> {
  // `eval record <conversationId>` sub-command.
  if (rest[0] === "record") {
    await evalRecord(rest.slice(1));
    return;
  }
  const { flags, positionals, extra } = extractConnectionFlags(rest);
  const cwd = process.cwd();
  const connected = await connect(flags, cwd);
  const agentId = connected.connection.agentId;
  if (!agentId) throw new Error("No agent id. Set agentId in turing.config.json, TURING_AGENT_ID, or --agent.");
  const backend = new ClientEvalBackend(connected.client);
  await connected.client.authenticate();

  const target = positionals[0];
  const runOnce = async () => {
    const files = discoverSuiteFiles(cwd, target);
    const passed = await runEvalFiles(files, backend, agentId, log);
    return passed;
  };

  if (extra.watch === true) {
    log("Eval watch mode — re-running on change (Ctrl-C to stop).");
    await runOnce();
    const stop = watchProject(cwd, () => void runOnce().catch((e) => log(`✗ ${(e as Error).message}`)));
    await new Promise<void>((res) => process.on("SIGINT", () => { stop(); res(); }));
    return;
  }
  const passed = await runOnce();
  if (!passed) process.exitCode = 1;
}

async function evalRecord(rest: string[]): Promise<void> {
  const { flags, positionals, extra } = extractConnectionFlags(rest);
  const conversationId = positionals[0];
  if (!conversationId) throw new Error("Usage: turing eval record <conversationId> [--out <file>]");
  const connected = await connect(flags, process.cwd());
  await connected.client.authenticate();
  const yaml = await recordFixture(connected.client, conversationId, {
    agentId: connected.connection.agentId,
    stamp: new Date().toISOString().slice(0, 10),
  });
  const out = typeof extra.out === "string" ? extra.out : undefined;
  if (out) {
    writeFileSync(resolve(process.cwd(), out), yaml, "utf8");
    log(`Wrote fixture to ${out}`);
  } else {
    process.stdout.write(yaml);
  }
}

function printHelp(): void {
  log(
    [
      "turing — the Viglet Turing ES developer CLI.",
      "",
      "Usage:",
      "  turing init <name> [--dir <path>] [--force]",
      "  turing dev [--file <compose>] [--detach] [--watch]",
      "  turing deploy [--env <name>] [connection flags]",
      "  turing logs --conversation <id> [--slots] [connection flags]",
      "  turing eval [path] [--watch] [connection flags]",
      "  turing eval record <conversationId> [--out <file>] [connection flags]",
      "",
      "Connection flags (override turing.config.json + env):",
      "  --url <u> --env <name> --token <t> --username <u> --password <p> --agent <id>",
      "",
      "Env vars: TURING_URL, TURING_ENV, TURING_TOKEN, TURING_USERNAME,",
      "          TURING_PASSWORD, TURING_AGENT_ID. Prefer TURING_TOKEN for CI.",
    ].join("\n"),
  );
}

const invokedDirectly = import.meta.url === pathToFileURL(process.argv[1] ?? "").href;
if (invokedDirectly) {
  main(process.argv.slice(2)).catch((err) => {
    console.error(`turing: ${(err as Error).message}`);
    process.exitCode = 1;
  });
}

export { main };
