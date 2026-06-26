/**
 * T127 — replay-as-test. Pulls a conversation's full snapshot from
 * {@code GET /api/chat/sessions/{id}/export} and freezes it as an
 * {@code *.eval.yaml} fixture: the visitor turns become {@code user:} steps and
 * the captured slots become a final assertion block. The author then tightens
 * assistant / tool assertions by hand — the recording is the regression
 * skeleton, not the final test.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

import type { TuringClient } from "../client.js";

/** Subset of {@code TurChatSessionExportDto} this feature consumes. */
export interface ConversationExport {
  conversationId: string;
  slots?: Record<string, string> | null;
  messages?: { role: string; content: string }[] | null;
}

export interface RecordOptions {
  /** Agent id to bake into the suite (so {@code turing eval} can run it as-is). */
  agentId?: string;
  /** Timestamp label for the description (caller passes it — keeps this pure). */
  stamp?: string;
}

/**
 * Builds the YAML text of a one-fixture suite from an exported conversation.
 * Pure (no IO) so it is unit-testable.
 */
export function buildFixtureYaml(exp: ConversationExport, options: RecordOptions = {}): string {
  const userTurns = (exp.messages ?? []).filter((m) => m.role === "user").map((m) => m.content);
  const slots = exp.slots ?? {};
  const id = `recorded-${sanitize(exp.conversationId)}`;
  const lines: string[] = [];
  if (options.agentId) lines.push(`agent: ${q(options.agentId)}`);
  lines.push("fixtures:");
  lines.push(`  - id: ${q(id)}`);
  const desc = `Frozen from conversation ${exp.conversationId}${options.stamp ? ` on ${options.stamp}` : ""}`;
  lines.push(`    description: ${q(desc)}`);
  lines.push("    conversation:");
  if (userTurns.length === 0) {
    lines.push("      # No visitor turns were found in the exported transcript.");
  }
  for (const turn of userTurns) {
    lines.push(`      - user: ${q(turn)}`);
  }
  const slotKeys = Object.keys(slots);
  if (slotKeys.length > 0) {
    lines.push("      # Slot snapshot frozen from the recorded conversation:");
    for (const key of slotKeys) {
      lines.push(`      - assert.slot.${key}: ${q(String(slots[key]))}`);
    }
  }
  lines.push("      # TODO: add assistant / tool assertions to lock in behaviour.");
  return lines.join("\n") + "\n";
}

/** Fetches the export and builds the fixture YAML. */
export async function recordFixture(
  client: TuringClient,
  conversationId: string,
  options: RecordOptions = {},
): Promise<string> {
  const exp = await client.get<ConversationExport>(
    `/api/chat/sessions/${encodeURIComponent(conversationId)}/export`,
  );
  return buildFixtureYaml(exp, options);
}

/** Double-quoted YAML scalar — JSON string syntax is valid YAML for our values. */
function q(value: string): string {
  return JSON.stringify(value);
}

function sanitize(id: string): string {
  const cleaned = id.replace(/[^A-Za-z0-9._-]/g, "-");
  return cleaned.length > 32 ? cleaned.slice(0, 32) : cleaned || "session";
}
