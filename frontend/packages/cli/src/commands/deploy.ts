/**
 * {@code turing deploy} — pushes a project's agent, chat-flows, custom tools,
 * and skills to a running Turing instance.
 *
 * <p>Idempotent: the agent is created on first deploy (its id is written back
 * to {@code turing.config.json}) and updated thereafter; flows go through the
 * atomic {@code /chat-flow/import-bundle} endpoint; tools and skills match by
 * title / name and PUT-or-POST accordingly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { basename, join } from "node:path";

import type { TuringClient } from "../client.js";
import { buildZip, type ZipEntry } from "../zip.js";

/* ─────────────────────── pure payload builders ─────────────────────── */

/** Builds the {@code TurChatFlowImportDto} from a flow file's parsed JSON. */
export function buildFlowImportPayload(parsed: unknown): Record<string, unknown> {
  const obj = parsed as Record<string, unknown>;
  // Already an import DTO ({ chatFlow: {...} }) — pass through.
  if (obj && typeof obj === "object" && "chatFlow" in obj) return obj;
  // A TranspiledFlow ({ name, graph: { nodes, edges }, ... }) from flow-dsl.
  const graph = obj.graph as { nodes?: unknown; edges?: unknown } | undefined;
  const definitionJson = graph
    ? JSON.stringify({ nodes: graph.nodes ?? [], edges: graph.edges ?? [] })
    : JSON.stringify({ nodes: obj.nodes ?? [], edges: obj.edges ?? [] });
  const chatFlow: Record<string, unknown> = {
    name: obj.name,
    description: obj.description ?? null,
    definitionJson,
    enabled: 1,
    guardrailMethod: obj.guardrailMethod ?? "HEURISTIC",
    triggerDescription: obj.triggerDescription ?? null,
    triggerMode: obj.triggerMode ?? "ALWAYS",
    triggerLanguage: obj.triggerLanguage ?? "AUTO",
  };
  if (obj.id) chatFlow.id = obj.id;
  const payload: Record<string, unknown> = { chatFlow };
  if (Array.isArray(obj.slots) && obj.slots.length) payload.slots = obj.slots;
  if (Array.isArray(obj.personas) && obj.personas.length) payload.personas = obj.personas;
  return payload;
}

/** Builds a custom-tool DTO from a `.groovy` script (+ optional `.tool.json` sidecar). */
export function buildToolPayload(name: string, groovyScript: string, sidecar?: Record<string, unknown>): Record<string, unknown> {
  return {
    title: sidecar?.title ?? titleCase(name),
    description: sidecar?.description ?? "",
    llmDescription: sidecar?.llmDescription ?? "",
    groovyScript,
    parametersJson: sidecar?.parametersJson ?? "[]",
    returnType: sidecar?.returnType ?? "string",
    icon: sidecar?.icon ?? "tool",
    enabled: sidecar?.enabled ?? 1,
    ...(sidecar?.id ? { id: sidecar.id } : {}),
  };
}

/* ─────────────────────── deploy orchestration ─────────────────────── */

export interface DeployContext {
  client: TuringClient;
  projectDir: string;
  /** Resolved agent id (may be empty → create). */
  agentId?: string;
  configPath?: string;
  log: (line: string) => void;
}

export interface DeploySummary {
  agentId: string;
  flows: number;
  tools: number;
  skills: number;
}

export async function deployProject(ctx: DeployContext): Promise<DeploySummary> {
  await ctx.client.authenticate();
  const agentId = await deployAgent(ctx);
  const flows = await deployFlows(ctx, agentId);
  const tools = await deployTools(ctx);
  const skills = await deploySkills(ctx);
  return { agentId, flows, tools, skills };
}

async function deployAgent(ctx: DeployContext): Promise<string> {
  const file = join(ctx.projectDir, "agent.json");
  if (!existsSync(file)) throw new Error(`agent.json not found in ${ctx.projectDir}`);
  const agent = JSON.parse(readFileSync(file, "utf8")) as Record<string, unknown>;
  if (ctx.agentId) {
    await ctx.client.put(`/api/ai-agent/${encodeURIComponent(ctx.agentId)}`, { ...agent, id: ctx.agentId });
    ctx.log(`↻ agent updated (${ctx.agentId})`);
    return ctx.agentId;
  }
  const created = await ctx.client.post<{ id?: string }>("/api/ai-agent", agent);
  const newId = created.id ?? "";
  ctx.log(`✓ agent created (${newId})`);
  // Persist the new id so subsequent deploys update in place.
  if (newId && ctx.configPath && existsSync(ctx.configPath)) {
    try {
      const cfg = JSON.parse(readFileSync(ctx.configPath, "utf8")) as Record<string, unknown>;
      cfg.agentId = newId;
      writeFileSync(ctx.configPath, JSON.stringify(cfg, null, 2) + "\n", "utf8");
      ctx.log(`  wrote agentId to ${basename(ctx.configPath)}`);
    } catch {
      ctx.log("  (could not write agentId back to turing.config.json)");
    }
  }
  return newId;
}

async function deployFlows(ctx: DeployContext, agentId: string): Promise<number> {
  const dir = join(ctx.projectDir, "flows");
  if (!existsSync(dir)) return 0;
  const files = readdirSync(dir).filter((f) => f.endsWith(".chat-flow.json"));
  if (files.length === 0) return 0;
  const bundle = files.map((f) => buildFlowImportPayload(JSON.parse(readFileSync(join(dir, f), "utf8"))));
  await ctx.client.post(`/api/ai-agent/${encodeURIComponent(agentId)}/chat-flow/import-bundle`, bundle);
  ctx.log(`✓ ${files.length} flow(s) deployed`);
  return files.length;
}

async function deployTools(ctx: DeployContext): Promise<number> {
  const dir = join(ctx.projectDir, "tools");
  if (!existsSync(dir)) return 0;
  const files = readdirSync(dir).filter((f) => f.endsWith(".groovy"));
  if (files.length === 0) return 0;
  const existing = await fetchExistingByTitle(ctx.client, "/api/custom-tool");
  for (const f of files) {
    const name = f.replace(/\.groovy$/, "");
    const script = readFileSync(join(dir, f), "utf8");
    const sidecarPath = join(dir, `${name}.tool.json`);
    const sidecar = existsSync(sidecarPath)
      ? (JSON.parse(readFileSync(sidecarPath, "utf8")) as Record<string, unknown>)
      : undefined;
    const payload = buildToolPayload(name, script, sidecar);
    const existingId = existing.get(String(payload.title));
    if (existingId) {
      await ctx.client.put(`/api/custom-tool/${encodeURIComponent(existingId)}`, { ...payload, id: existingId });
      ctx.log(`↻ tool '${payload.title}'`);
    } else {
      await ctx.client.post("/api/custom-tool", payload);
      ctx.log(`✓ tool '${payload.title}'`);
    }
  }
  return files.length;
}

async function deploySkills(ctx: DeployContext): Promise<number> {
  const dir = join(ctx.projectDir, "skills");
  if (!existsSync(dir)) return 0;
  const skillDirs = readdirSync(dir).filter((entry) => {
    const abs = join(dir, entry);
    return statSync(abs).isDirectory() && existsSync(join(abs, "SKILL.md"));
  });
  for (const skill of skillDirs) {
    const entries = collectZipEntries(join(dir, skill), "");
    const zip = buildZip(entries);
    await ctx.client.postMultipart("/api/skill/import", "file", `${skill}.zip`, zip);
    ctx.log(`✓ skill '${skill}' (${entries.length} file(s))`);
  }
  return skillDirs.length;
}

/* ─────────────────────── helpers ─────────────────────── */

async function fetchExistingByTitle(client: TuringClient, path: string): Promise<Map<string, string>> {
  const list = await client.get<{ id?: string; title?: string }[]>(path);
  const byTitle = new Map<string, string>();
  for (const item of list) if (item.id && item.title) byTitle.set(item.title, item.id);
  return byTitle;
}

/** Recursively collects a folder's files as ZIP entries (SKILL.md at root). */
function collectZipEntries(dir: string, prefix: string): ZipEntry[] {
  const out: ZipEntry[] = [];
  for (const entry of readdirSync(dir)) {
    const abs = join(dir, entry);
    const rel = prefix ? `${prefix}/${entry}` : entry;
    if (statSync(abs).isDirectory()) {
      out.push(...collectZipEntries(abs, rel));
    } else {
      out.push({ name: rel, data: new Uint8Array(readFileSync(abs)) });
    }
  }
  return out;
}

function titleCase(slug: string): string {
  return slug.replace(/[-_]+/g, " ").replace(/\b\w/g, (c) => c.toUpperCase()).trim();
}
