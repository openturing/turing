import type { TurAIAgentSlot } from "@/models/agent/ai-agent-slot.model";
import {
  CHAT_FLOW_GUARDRAIL_METHODS,
  CHAT_FLOW_TRIGGER_LANGUAGES,
  CHAT_FLOW_TRIGGER_MODES,
  type TurChatFlow,
  type TurChatFlowGuardrailMethod,
  type TurChatFlowTriggerLanguage,
  type TurChatFlowTriggerMode,
} from "@/models/agent/chat-flow.model";
import type { TurPersona } from "@/models/persona/persona.model";
import type { TurChatFlowImportPayload } from "@/services/agent/chat-flow.service";

/**
 * Wire shape of an exported chat-flow JSON entry. Matches what the editor's "Export JSON" action
 * writes; older exports (pre-Phase B) stored {@code nodes}/{@code edges} at the top level instead
 * of nested under {@code graph}, so we accept both.
 *
 * The optional {@code id} acts as a transient identifier when several flows ship in the same
 * array bundle — sub-flow {@code subFlowId} references inside graphs use it, and the backend
 * remaps it to the real persisted UUID at import time.
 *
 * @since 2026.2.7
 */
export interface ChatFlowExportEntry {
  id?: string;
  name?: string;
  description?: string | null;
  guardrailMethod?: TurChatFlowGuardrailMethod;
  triggerDescription?: string | null;
  triggerMode?: TurChatFlowTriggerMode;
  triggerLanguage?: TurChatFlowTriggerLanguage;
  personas?: TurPersona[];
  /**
   * Typed slots referenced by the flow's {@code outputVariable} fields. The
   * backend auto-creates any slot whose name is not yet declared on the
   * target agent — existing slots are left untouched.
   */
  slots?: TurAIAgentSlot[];
  graph?: { nodes?: unknown; edges?: unknown };
  /** Back-compat: pre-Phase-B exports stored nodes/edges at the top level. */
  nodes?: unknown;
  edges?: unknown;
}

/**
 * Translates a parsed export entry into the request shape the backend's `/import` and
 * `/import-bundle` endpoints expect. The transient {@code id} (when present) is preserved on the
 * outgoing {@code chatFlow.id} so the bundle endpoint can wire sub-flow references between items
 * — for the single-flow endpoint the id is harmlessly ignored.
 */
export function buildImportPayloadFromExport(
  entry: ChatFlowExportEntry,
): TurChatFlowImportPayload {
  const graphSource =
    entry.graph && (entry.graph.nodes || entry.graph.edges)
      ? entry.graph
      : { nodes: entry.nodes, edges: entry.edges };
  const chatFlow: TurChatFlow = {
    id: entry.id,
    name: typeof entry.name === "string" ? entry.name : "",
    description: typeof entry.description === "string" ? entry.description : null,
    definitionJson: JSON.stringify(graphSource),
    enabled: 1,
    guardrailMethod:
      entry.guardrailMethod && CHAT_FLOW_GUARDRAIL_METHODS.includes(entry.guardrailMethod)
        ? entry.guardrailMethod
        : "LLM_JUDGE",
    triggerDescription:
      typeof entry.triggerDescription === "string" ? entry.triggerDescription : null,
    triggerMode:
      entry.triggerMode && CHAT_FLOW_TRIGGER_MODES.includes(entry.triggerMode)
        ? entry.triggerMode
        : "ONCE",
    triggerLanguage:
      entry.triggerLanguage && CHAT_FLOW_TRIGGER_LANGUAGES.includes(entry.triggerLanguage)
        ? entry.triggerLanguage
        : "AUTO",
  };
  return { chatFlow, personas: entry.personas, slots: entry.slots };
}
