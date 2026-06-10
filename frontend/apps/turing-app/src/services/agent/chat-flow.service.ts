import axios from "axios";
import type { TurAIAgentSlot } from "@/models/agent/ai-agent-slot.model";
import type {
  ChatFlowGeneration,
  TurChatFlow,
  TurChatFlowFunnelReport,
  TurChatFlowLintIssue,
  TurChatFlowTriggerConflict,
  TurChatFlowVariantRequest,
  TurChatFlowVariantResponse,
  TurFormLabelTidyRequest,
  TurFormLabelTidyResponse,
} from "@/models/agent/chat-flow.model";
import type { TurChatFlowSubmission } from "@/models/agent/chat-flow-submission.model";
import type { AiAuthoringRequest, AiAuthoringResponse } from "@/models/ai-authoring/ai-authoring.model";
import type { TurPersona } from "@/models/persona/persona.model";

/**
 * Wire shape of the chat-flow export JSON, mirrored by the backend's
 * {@code TurChatFlowImportDto}. Carries the flow itself plus the embedded
 * persona catalog the import endpoint needs to materialise voices that
 * the operator's agent does not yet have.
 *
 * @since 2026.2.7
 */
export interface TurChatFlowImportPayload {
  chatFlow: TurChatFlow;
  personas?: TurPersona[];
  /**
   * Typed slots referenced by the flow's {@code outputVariable} fields. Names
   * not yet declared on the target agent are auto-created at import time so
   * the editor opens with a populated slot dropdown.
   */
  slots?: TurAIAgentSlot[];
}

/**
 * Agent-scoped chat flow client. Every endpoint requires the parent agent id;
 * flows can never be addressed without their owning agent.
 *
 * @since 2026.2.5
 */
export class TurChatFlowService {
  async query(agentId: string): Promise<TurChatFlow[]> {
    const response = await axios.get<TurChatFlow[]>(`/ai-agent/${agentId}/chat-flow`);
    return response.data;
  }
  async get(agentId: string, id: string): Promise<TurChatFlow> {
    const response = await axios.get<TurChatFlow>(`/ai-agent/${agentId}/chat-flow/${id}`);
    return response.data;
  }
  async create(agentId: string, turChatFlow: TurChatFlow): Promise<TurChatFlow> {
    const response = await axios.post<TurChatFlow>(`/ai-agent/${agentId}/chat-flow`, turChatFlow);
    return response.data;
  }
  async update(agentId: string, turChatFlow: TurChatFlow): Promise<TurChatFlow> {
    const response = await axios.put<TurChatFlow>(`/ai-agent/${agentId}/chat-flow/${turChatFlow.id}`, turChatFlow);
    return response.data;
  }
  async delete(agentId: string, turChatFlow: TurChatFlow): Promise<boolean> {
    const response = await axios.delete<TurChatFlow>(`/ai-agent/${agentId}/chat-flow/${turChatFlow.id}`);
    return response.status == 200;
  }

  /**
   * Imports a flow exported via the editor's "Export JSON" button. Any
   * persona in {@code payload.personas} whose name is not yet in the
   * catalog is created on the backend and attached to {@code agentId};
   * `personaId` references inside the graph are rewritten before save.
   *
   * @since 2026.2.7
   */
  async import(agentId: string, payload: TurChatFlowImportPayload): Promise<TurChatFlow> {
    const response = await axios.post<TurChatFlow>(
      `/ai-agent/${agentId}/chat-flow/import`,
      payload,
    );
    return response.data;
  }

  /**
   * Imports a bundle of chat flows in a single round-trip. Sub-flow references between bundle
   * items are auto-wired by the backend (placeholder {@code id} on one item matches a
   * {@code subFlowId} on another). Personas are de-duplicated by name across the whole bundle.
   * Returns the persisted flows in the same order as the request.
   *
   * @since 2026.2.7
   */
  async importBundle(
    agentId: string,
    bundle: TurChatFlowImportPayload[],
  ): Promise<TurChatFlow[]> {
    const response = await axios.post<TurChatFlow[]>(
      `/ai-agent/${agentId}/chat-flow/import-bundle`,
      bundle,
    );
    return response.data;
  }

  /**
   * Submissions: every flow run that reached an end node, newest first.
   * Used by the AI Agent History page.
   */
  async submissions(agentId: string, flowId: string): Promise<TurChatFlowSubmission[]> {
    const response = await axios.get<TurChatFlowSubmission[]>(
      `/ai-agent/${agentId}/chat-flow/${flowId}/submissions`,
    );
    return response.data;
  }

  /**
   * T91 — pairs of flows on this agent whose trigger descriptions overlap
   * enough that the procedural router cannot reliably separate them. The
   * chat-flow editor renders these as inline warnings on the affected flow
   * and as an agent-wide panel on the list page.
   */
  async triggerConflicts(agentId: string): Promise<TurChatFlowTriggerConflict[]> {
    const response = await axios.get<TurChatFlowTriggerConflict[]>(
      `/ai-agent/${agentId}/chat-flow/trigger-conflicts`,
    );
    return response.data;
  }

  /**
   * T94 — static-analysis warnings for a single chat flow. Powers the
   * sidebar panel in the admin editor.
   */
  async lint(agentId: string, flowId: string): Promise<TurChatFlowLintIssue[]> {
    const response = await axios.get<TurChatFlowLintIssue[]>(
      `/ai-agent/${agentId}/chat-flow/${flowId}/lint`,
    );
    return response.data;
  }

  /**
   * T85 — per-node funnel report for a single chat flow. Powers the
   * editor's sidebar panel that surfaces drop-off + completion counts
   * per interactive node.
   */
  async funnel(agentId: string, flowId: string): Promise<TurChatFlowFunnelReport> {
    const response = await axios.get<TurChatFlowFunnelReport>(
      `/ai-agent/${agentId}/chat-flow/${flowId}/funnel`,
    );
    return response.data;
  }

  /**
   * T97 / §VII.11.g — ask the default LLM for a tone/length variant of
   * an existing flow. The response carries a candidate {@link TurChatFlow}
   * the dialog can save through the standard create endpoint after the
   * author reviews it. Nothing is persisted on the backend yet.
   */
  async generateVariant(
    agentId: string,
    flowId: string,
    request: TurChatFlowVariantRequest,
  ): Promise<TurChatFlowVariantResponse> {
    const response = await axios.post<TurChatFlowVariantResponse>(
      `/ai-agent/${agentId}/chat-flow/${flowId}/variant`,
      request,
    );
    return response.data;
  }

  /**
   * T236 / §VII.13.g — opt-in "tidy labels" pass on a T234 form conversion.
   * Sends the fields the converter derived client-side (whose labels are raw
   * question instructions) and gets back the same fields with short, clean
   * form labels. Nothing is persisted — the convert-to-form dialog applies
   * the tidied labels only when the author confirms.
   */
  async tidyFormLabels(
    agentId: string,
    request: TurFormLabelTidyRequest,
  ): Promise<TurFormLabelTidyResponse> {
    const response = await axios.post<TurFormLabelTidyResponse>(
      `/ai-agent/${agentId}/chat-flow/tidy-labels`,
      request,
    );
    return response.data;
  }

  /**
   * AI Authoring chat — first turn invokes the planning skill on the
   * backend, subsequent turns do incremental edits. Always returns a
   * conversational reply + the FULL updated state.
   */
  async aiChat(
    agentId: string,
    request: AiAuthoringRequest<ChatFlowGeneration>,
  ): Promise<AiAuthoringResponse<ChatFlowGeneration>> {
    const response = await axios.post<AiAuthoringResponse<ChatFlowGeneration>>(
      `/ai-agent/${agentId}/chat-flow/chat`,
      request,
    );
    return response.data;
  }
}
