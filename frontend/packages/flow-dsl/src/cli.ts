#!/usr/bin/env node
/**
 * `turing-flow-dsl build <input-dir> [--out <output-dir>]`
 *
 * <p>Zero-dep CLI that scans an input directory for chat-flow sources
 * and emits the editor's JSON wire shape into the output directory.
 *
 * <p>Input file types:
 * <ul>
 *   <li>{@code *.flow.json} — back-compat raw export from the editor; the
 *       file is parsed, lightly normalised through {@link transpileFlow}'s
 *       sister path (it's already in the wire shape, so it's copied
 *       through verbatim), and written under the same base name with
 *       a {@code .chat-flow.json} extension. This satisfies the T98
 *       "back-compat reading raw JSON" requirement so a team can mix
 *       hand-authored JSON and DSL TS files in the same {@code flows/}
 *       directory.</li>
 *   <li>{@code *.flow.mjs} / {@code *.flow.js} — ES module compiled from
 *       a {@code *.flow.ts} source (run {@code tsc} first). The CLI
 *       dynamically imports the file; the default export (or a
 *       {@code flow}/{@code flows} named export) must be a
 *       {@link FlowSpec} or {@code FlowSpec[]}, which the CLI runs
 *       through {@link transpileFlow}.</li>
 * </ul>
 *
 * <p>Output: each input becomes {@code <basename>.chat-flow.json} in the
 * output directory (default {@code ./dist}), ready to drop on the editor's
 * "Import JSON" button or feed to {@code POST /chat-flow/import}.
 *
 * <p>Exit codes: {@code 0} success, {@code 1} on validation or IO error.
 * The CLI prints one line per file plus a summary at the end so it slots
 * into CI / npm scripts naturally.
 *
 * @since 2026.3.1
 */

import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { basename, extname, join, relative, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { parseDeployArgs, runDeploy } from "./deploy.js";
import { FlowSpecError, transpileFlow } from "./transpile.js";
import type { FlowSpec, TranspiledFlow } from "./types.js";

interface CliArgs {
  command: "build" | "deploy" | "help";
  inputDir: string;
  outputDir: string;
  /** Raw argv tail forwarded to the per-command parser (deploy uses it). */
  rest: string[];
}

function parseArgs(argv: readonly string[]): CliArgs {
  const args = [...argv];
  const command = (args.shift() ?? "help") as CliArgs["command"];
  if (command === "deploy") {
    return { command, inputDir: "", outputDir: "", rest: args };
  }
  if (command !== "build") {
    return { command: "help", inputDir: "", outputDir: "", rest: [] };
  }
  let inputDir = "";
  let outputDir = "./dist";
  for (let i = 0; i < args.length; i++) {
    const token = args[i];
    if (token === "--out" || token === "-o") {
      const next = args[i + 1];
      if (!next) {
        throw new Error("--out expects a directory argument.");
      }
      outputDir = next;
      i++;
    } else if (token && !token.startsWith("-")) {
      inputDir = token;
    } else if (token) {
      throw new Error(`Unknown option: ${token}`);
    }
  }
  if (!inputDir) {
    throw new Error("Missing input directory. Usage: turing-flow-dsl build <input-dir> [--out <output-dir>]");
  }
  return { command, inputDir, outputDir, rest: [] };
}

function printHelp(): void {
  // eslint-disable-next-line no-console
  console.log(
    [
      "turing-flow-dsl — typed chat-flow sources for Viglet Turing ES.",
      "",
      "Usage:",
      "  turing-flow-dsl build  <input-dir> [--out <output-dir>]",
      "  turing-flow-dsl deploy <input-dir> [--url U] [--agent ID]",
      "                                     [--username U] [--mode bundle|single]",
      "                                     [--create-only]",
      "",
      "build — inputs:",
      "  *.flow.json       — raw editor export (passed through).",
      "  *.flow.mjs / .js  — compiled DSL module (default export = FlowSpec).",
      "build — outputs:",
      "  <basename>.chat-flow.json in <output-dir> (default: ./dist).",
      "",
      "deploy — uploads every *.chat-flow.json in <input-dir> to a running",
      "Turing instance. Connection details live in package.json under",
      "\"turing\": { url, agentId, username, mode }. Env vars (TURING_URL,",
      "TURING_AGENT_ID, TURING_USERNAME, TURING_PASSWORD, TURING_MODE)",
      "override package.json; CLI flags override env. Password is prompted",
      "interactively when TURING_PASSWORD is not set.",
    ].join("\n"),
  );
}

/**
 * Walks {@code inputDir}, processes every matching file, writes the
 * transpiled JSON into {@code outputDir}. Returns the count of files
 * processed and failed so the test harness can drive it without spawning
 * a real process.
 */
export async function build(inputDir: string, outputDir: string): Promise<{ ok: number; failed: number }> {
  const resolvedInput = resolve(inputDir);
  if (!existsSync(resolvedInput) || !statSync(resolvedInput).isDirectory()) {
    throw new Error(`Input directory not found: ${resolvedInput}`);
  }
  const resolvedOutput = resolve(outputDir);
  mkdirSync(resolvedOutput, { recursive: true });

  let ok = 0;
  let failed = 0;
  for (const entry of readdirSync(resolvedInput)) {
    const absolute = join(resolvedInput, entry);
    if (!statSync(absolute).isFile()) continue;
    const kind = classify(entry);
    if (!kind) continue;
    try {
      const flows = kind === "json"
        ? await readJsonFlow(absolute)
        : await importFlow(absolute);
      for (const flow of flows) {
        const transpiled = transpileFlow(flow);
        const outName = `${baseName(entry, transpiled.name)}.chat-flow.json`;
        const outPath = join(resolvedOutput, outName);
        writeFileSync(outPath, JSON.stringify(transpiled, null, 2) + "\n", "utf8");
        // eslint-disable-next-line no-console
        console.log(`✓ ${relative(process.cwd(), absolute)} → ${relative(process.cwd(), outPath)}`);
      }
      ok++;
    } catch (err) {
      failed++;
      const message = err instanceof FlowSpecError ? err.message : (err as Error).message;
      // eslint-disable-next-line no-console
      console.error(`✗ ${relative(process.cwd(), absolute)}: ${message}`);
    }
  }
  // eslint-disable-next-line no-console
  console.log(`\n${ok} flow(s) transpiled, ${failed} failed.`);
  return { ok, failed };
}

type InputKind = "json" | "module";

function classify(filename: string): InputKind | null {
  if (filename.endsWith(".flow.json")) return "json";
  if (filename.endsWith(".flow.mjs") || filename.endsWith(".flow.js")) return "module";
  // Heuristic: a plain "*.json" file in the flows dir is opt-in via the
  // .flow.json suffix only; treating every JSON as a flow would surprise
  // teams keeping fixtures or i18n data alongside their flow definitions.
  return null;
}

/**
 * Strips the {@code .flow.json}/{@code .flow.mjs}/{@code .flow.js} suffix
 * and falls back to a sanitised flow name if the on-disk filename was
 * generic (e.g. {@code index.flow.mjs}).
 */
function baseName(filename: string, flowName: string): string {
  const ext = extname(filename);
  const stem = basename(filename, ext).replace(/\.flow$/, "");
  if (stem === "" || stem === "index") {
    return slugify(flowName);
  }
  return stem;
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "") || "flow";
}

async function readJsonFlow(absolute: string): Promise<FlowSpec[]> {
  const text = readFileSync(absolute, "utf8");
  const parsed = JSON.parse(text) as unknown;
  return normaliseExport(parsed);
}

async function importFlow(absolute: string): Promise<FlowSpec[]> {
  const url = pathToFileURL(absolute).href;
  const mod = (await import(url)) as Record<string, unknown>;
  const candidate = mod.default ?? mod.flow ?? mod.flows;
  return normaliseExport(candidate);
}

function normaliseExport(value: unknown): FlowSpec[] {
  if (!value) {
    throw new Error("Module did not export a flow (expected default or named 'flow'/'flows').");
  }
  if (Array.isArray(value)) {
    return value.filter((v): v is FlowSpec => isFlowLike(v));
  }
  if (isFlowLike(value)) {
    return [value];
  }
  // Editor-export back-compat: the JSON already carries the transpiled
  // shape ({@code graph.nodes/edges}). Convert it into a degenerate
  // FlowSpec so {@link transpileFlow} re-emits it normalised. This is
  // the "back-compat reading raw JSON" path called out by T98.
  if (looksLikeTranspiled(value)) {
    return [reverseTranspile(value as TranspiledFlow)];
  }
  throw new Error("Module exported something that is not a FlowSpec or FlowSpec[].");
}

function isFlowLike(value: unknown): value is FlowSpec {
  if (typeof value !== "object" || value === null) return false;
  const obj = value as Record<string, unknown>;
  return typeof obj.name === "string" && Array.isArray(obj.nodes) && Array.isArray(obj.edges);
}

function looksLikeTranspiled(value: unknown): boolean {
  if (typeof value !== "object" || value === null) return false;
  const obj = value as Record<string, unknown>;
  if (typeof obj.name !== "string") return false;
  const graph = obj.graph as Record<string, unknown> | undefined;
  return !!graph && Array.isArray(graph.nodes) && Array.isArray(graph.edges);
}

/**
 * Lossy projection from the transpiled (editor) shape back into a
 * {@link FlowSpec}. Strips positions and {@code data.type}, lifts the
 * remaining {@code data.*} fields onto the node, and drops null fields
 * on edges. Used only for the back-compat JSON pass-through path.
 */
function reverseTranspile(transpiled: TranspiledFlow): FlowSpec {
  const nodes = transpiled.graph.nodes.map((n) => {
    const { type: _ignoreInnerType, ...rest } = (n.data ?? {}) as Record<string, unknown>;
    return { id: n.id, type: n.type, ...rest } as unknown as FlowSpec["nodes"][number];
  });
  const edges = transpiled.graph.edges.map((e) => {
    const edge: FlowSpec["edges"][number] = {
      id: e.id,
      source: e.source,
      target: e.target,
    };
    if (e.sourceHandle) edge.sourceHandle = e.sourceHandle;
    if (e.targetHandle) edge.targetHandle = e.targetHandle;
    if (e.label) edge.label = e.label;
    return edge;
  });
  return {
    id: transpiled.id,
    name: transpiled.name,
    description: transpiled.description ?? undefined,
    guardrailMethod: transpiled.guardrailMethod,
    triggerDescription: transpiled.triggerDescription ?? undefined,
    triggerMode: transpiled.triggerMode,
    triggerLanguage: transpiled.triggerLanguage,
    nodes,
    edges,
    slots: transpiled.slots,
    personas: transpiled.personas,
  };
}

async function main(): Promise<void> {
  try {
    const args = parseArgs(process.argv.slice(2));
    if (args.command === "help") {
      printHelp();
      return;
    }
    if (args.command === "deploy") {
      const deployArgs = parseDeployArgs(args.rest);
      const result = await runDeploy(deployArgs, {
        cwd: process.cwd(),
        env: process.env,
      });
      if (result.failed > 0) process.exitCode = 1;
      return;
    }
    const { failed } = await build(args.inputDir, args.outputDir);
    if (failed > 0) {
      process.exitCode = 1;
    }
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error(`turing-flow-dsl: ${(err as Error).message}`);
    process.exitCode = 1;
  }
}

// Guard so the test file can import { build } without invoking the CLI.
const invokedDirectly =
  import.meta.url === pathToFileURL(process.argv[1] ?? "").href;
if (invokedDirectly) {
  void main();
}
