/**
 * T98 / §VII.11.h — typed DSL surface for Viglet Turing ES chat flows.
 *
 * <p>Every union / interface in this file mirrors a backend enum or JPA
 * column on {@code com.viglet.turing.persistence.model.agent.TurChatFlow}
 * and {@code com.viglet.turing.genai.flow.ChatFlowNode}. When the backend
 * adds a new value (e.g. a new guardrail strategy), update the union here
 * — the transpiler is purely structural so it doesn't need to know about
 * the value, but authors lose compile-time safety until the union grows.
 *
 * <p>The unions are intentionally written as string literals (not
 * {@code as const} arrays) so a typo like {@code tone: "PROFESSIONA"}
 * fails at {@code tsc} time with a clear error pointing at the bad value,
 * instead of falling through at runtime to a server-side validation error.
 */

/** {@code TurChatFlowGuardrailMethod} on the backend. */
export type GuardrailMethod = "HEURISTIC" | "LLM_JUDGE" | "STRUCTURED_OUTPUT";

/** {@code TurChatFlowTriggerMode} on the backend. */
export type TriggerMode = "ONCE" | "ALWAYS";

/** {@code TurChatFlowTriggerLanguage} (T25 / §II.2.1). */
export type TriggerLanguage = "AUTO" | "PT" | "EN";

/** {@code ChatFlowNode.NodeData.toolSource}. */
export type ToolSource = "NATIVE" | "MCP";

/** {@code ChatFlowNode.NodeData.slotOperation}. */
export type SlotOperation = "SET" | "DELETE";

/**
 * Soft-fail policy for the LLM_JUDGE guardrail on a slot-collecting
 * aiQuestion node ({@code TurChatFlow.onJudgeReject}, §2026.2.31).
 */
export type OnJudgeReject = "advance_with_literal" | "reprompt" | "block";

/**
 * Validation rule applied to an aiQuestion's captured answer before the
 * engine advances. Mirrors the validators wired into the runtime. The
 * backend additionally accepts the Brazilian-document rules {@code cpf},
 * {@code cnpj} and {@code cep} — kept in the union so a {@code formCapture}
 * node can validate those without losing compile-time safety.
 */
export type ValidationRule =
  | "email"
  | "phone"
  | "url"
  | "number"
  | "date"
  | "cpf"
  | "cnpj"
  | "cep"
  | "none";

/** {@code TurPersonaTone} on the backend (persona voice register). */
export type PersonaTone = "FORMAL" | "CASUAL" | "TECHNICAL" | "EXECUTIVE";

/** {@code TurPersonaLanguageStyle} on the backend (sentence construction). */
export type PersonaLanguageStyle =
  | "NEUTRAL"
  | "DIRECT"
  | "NARRATIVE"
  | "PERSUASIVE"
  | "INSTRUCTIONAL";

/**
 * All node kinds the editor (and engine) understand. New types added on
 * the backend must be listed here for the DSL to compile-check them.
 */
export type NodeType =
  | "start"
  | "end"
  | "aiQuestion"
  | "formCapture"
  | "condition"
  | "functionCall"
  | "scheduleAgent"
  | "subFlow"
  | "subFlowSwitch"
  | "persona"
  | "switch"
  | "slot"
  | "writeSlot"
  | "suspend";

/**
 * One branch on a {@code switch} or {@code subFlowSwitch} node —
 * {@code id} is also the edge {@code sourceHandle}. {@link subFlowId} is
 * only honored by {@code subFlowSwitch} nodes (T47): the engine descends
 * into the linked sub-flow instead of routing to an outgoing edge.
 */
export interface SwitchOption {
  id: string;
  label: string;
  /** T47 — id of the sub-flow this option enters. Ignored on plain switches. */
  subFlowId?: string;
  /** Cached display name of the linked sub-flow. */
  subFlowName?: string;
}

/* ─────────────────────── Per-node DSL shapes ─────────────────────── */

interface NodeBase {
  /** Stable, semantic id (e.g. {@code "ask-name"}). */
  id: string;
  /** Optional uppercase card title; the transpiler fills a sensible default. */
  label?: string;
}

export interface StartNode extends NodeBase {
  type: "start";
}

export interface EndNode extends NodeBase {
  type: "end";
}

export interface AiQuestionNode extends NodeBase {
  type: "aiQuestion";
  aiInstruction: string;
  outputVariable?: string;
  validationRule?: ValidationRule;
  overrideExistingValue?: boolean;
  inlineOptions?: readonly string[];
  onJudgeReject?: OnJudgeReject;
  toolsEnabled?: boolean;
  requiredTools?: readonly string[];
}

/**
 * Widget hint for a {@link FormField}. Mirrors the loose string the backend
 * accepts; unknown values degrade to a plain text input in the SDK.
 */
export type FormFieldType =
  | "text"
  | "email"
  | "tel"
  | "number"
  | "date"
  | "textarea"
  | "select";

/**
 * One field of a {@code formCapture} node's native multi-field form (T107).
 * Mirrors the backend {@code ChatFlowNode.FormField} record; each field maps
 * to one conversation slot named {@link name}.
 */
export interface FormField {
  /** Slot the captured value is written to. */
  name: string;
  /** Human-readable label rendered above the input. */
  label?: string;
  /** Widget hint; defaults to {@code "text"}. */
  type?: FormFieldType;
  /** When {@code false}, optional — does not block the node's satisfied check. */
  required?: boolean;
  /** Optional input placeholder text. */
  placeholder?: string;
  /** Validation-rule hint surfaced to the SDK for client-side checks. */
  validationRule?: ValidationRule;
  /** Choice labels for a {@code select} field; ignored for other types. */
  options?: readonly string[];
}

export interface FormCaptureNode extends NodeBase {
  type: "formCapture";
  aiInstruction?: string;
  outputVariable?: string;
  validationRule?: ValidationRule;
  /**
   * T107 — when present, the node renders as a NATIVE multi-field form (the
   * engine emits a `"form"` SSE event with these fields). Omit for the legacy
   * single-field capture into {@link outputVariable}.
   */
  formFields?: readonly FormField[];
}

export interface ConditionNode extends NodeBase {
  type: "condition";
  conditionExpression: string;
}

/** Sub-shape: native @Tool callable. */
interface FunctionCallNativeNode extends NodeBase {
  type: "functionCall";
  toolSource: "NATIVE";
  functionName: string;
  /** JSON input template — {@code {{slot}}} placeholders interpolated before the call. */
  aiInstruction?: string;
  /** Slot the tool's String result is written into. Omit for a side-effect-only call. */
  outputVariable?: string;
  /**
   * T49 — when {@code true}, a failed invocation routes the flow along the
   * outgoing edge whose {@code sourceHandle} is {@code "failure"} (wire one with
   * {@link FlowEdge#sourceHandle} = {@code "failure"}). When omitted, the engine
   * advances on the first outgoing edge as before.
   */
  continueOnFailure?: boolean;
}

/** Sub-shape: MCP-server-hosted tool. */
interface FunctionCallMcpNode extends NodeBase {
  type: "functionCall";
  toolSource: "MCP";
  mcpServerId: string;
  aiInstruction?: string;
  outputVariable?: string;
  continueOnFailure?: boolean;
}

export type FunctionCallNode = FunctionCallNativeNode | FunctionCallMcpNode;

export interface SubFlowNode extends NodeBase {
  type: "subFlow";
  subFlowId: string;
  subFlowName?: string;
}

export interface PersonaNode extends NodeBase {
  type: "persona";
  personaId: string;
  personaName?: string;
}

export interface SwitchNode extends NodeBase {
  type: "switch";
  switchVariable: string;
  switchOptions: readonly SwitchOption[];
}

/**
 * T47 — like {@link SwitchNode} but each matched option carries a
 * {@code subFlowId} and the engine descends into the linked sub-flow
 * instead of routing through an outgoing edge.
 */
export interface SubFlowSwitchNode extends NodeBase {
  type: "subFlowSwitch";
  switchVariable: string;
  switchOptions: readonly SwitchOption[];
}

/**
 * T48 — asynchronous routine dispatch. Fires the configured routine into
 * the JMS queue and parks the flow on this node until the routine writes
 * the {@link outputVariable} slot (or the timeout elapses). On timeout the
 * engine takes the outgoing edge with {@code sourceHandle: "timeout"}
 * (falls back to the first edge when none is wired).
 */
export interface ScheduleAgentNode extends NodeBase {
  type: "scheduleAgent";
  /** Id of the {@code TurRoutine} to fire. */
  routineId: string;
  /** Cached display name of the linked routine. */
  routineName?: string;
  /** Slot the routine result is written into. */
  outputVariable?: string;
  /**
   * Tool input JSON template — {@code &#123;&#123;slotName&#125;&#125;}
   * placeholders are interpolated from the conversation's slot map before
   * the routine runs. Blank → {@code "{}"}.
   */
  aiInstruction?: string;
  /** Per-node timeout (ms). Falls back to the routine's default when omitted. */
  routineTimeoutMs?: number;
  /**
   * T49 — when {@code true}, an enqueue/resolution failure (missing routine,
   * disabled routine, JMS error) routes the flow along the {@code "failure"}
   * outgoing edge. Independent of the {@code "timeout"} edge, which still
   * handles the deadline-elapsed case.
   */
  continueOnFailure?: boolean;
}

/**
 * T121 — parks the flow cursor indefinitely. The engine stops walking on
 * this node; an external system / human-approval click / scheduled job
 * must call {@code POST /api/sn/{site}/genai/chat/resume} to pop it and
 * advance. Reads only {@link NodeBase#label} (surfaced as the parked
 * reason); no other fields.
 */
export interface SuspendNode extends NodeBase {
  type: "suspend";
}

/** Sub-shape: SET writes {@code slotValue}; DELETE clears the slot. */
interface SlotSetNode extends NodeBase {
  type: "slot";
  slotName: string;
  slotOperation: "SET";
  slotValue: string;
  overrideExistingValue?: boolean;
}

interface SlotDeleteNode extends NodeBase {
  type: "slot";
  slotName: string;
  slotOperation: "DELETE";
}

export type SlotNode = SlotSetNode | SlotDeleteNode;

export interface WriteSlotNode extends NodeBase {
  type: "writeSlot";
  slotName: string;
  slotValue: string;
}

/** Discriminated union of every node type the DSL accepts. */
export type FlowNode =
  | StartNode
  | EndNode
  | AiQuestionNode
  | FormCaptureNode
  | ConditionNode
  | FunctionCallNode
  | ScheduleAgentNode
  | SubFlowNode
  | SubFlowSwitchNode
  | PersonaNode
  | SwitchNode
  | SlotNode
  | WriteSlotNode
  | SuspendNode;

/* ─────────────────────── Edges ─────────────────────── */

/**
 * Edge from {@code source} to {@code target}. The {@code sourceHandle} is
 * required when {@code source} is a {@code condition} ({@code "yes" | "no"})
 * or {@code switch} (matches a {@code SwitchOption.id}); omit it otherwise.
 */
export interface FlowEdge {
  /** Optional explicit edge id — the transpiler synthesizes a stable one when omitted. */
  id?: string;
  source: string;
  target: string;
  sourceHandle?: string;
  targetHandle?: string;
  label?: string;
}

/* ─────────────────────── Slot / persona declarations ─────────────────────── */

/**
 * Slot type supported by the runtime — mirrors the backend
 * {@code TurAIAgentSlotType} enum exactly. (Email / phone / URL / number /
 * date are NOT slot types; they are {@link ValidationRule}s applied on the
 * capturing node — keep the two concepts separate.)
 */
export type SlotType = "STRING" | "INTEGER" | "BOOLEAN" | "FLOAT" | "TEXT";

/**
 * Slot the flow references via {@code outputVariable} or {@code slotName}.
 * The import endpoint auto-creates any slot whose name is not yet declared
 * on the target agent; existing slots are left untouched.
 */
export interface SlotDeclaration {
  name: string;
  description?: string;
  type?: SlotType;
}

/**
 * Persona embedded in the export bundle — upserted on import (looked up
 * case-insensitively by {@link name}). Mirrors the backend
 * {@code TurPersona} export shape so a DSL-authored persona round-trips
 * through {@code POST /chat-flow/import-bundle} with all its voice fields
 * intact.
 *
 * <p>{@link id} is the transient handle a {@code persona} node's
 * {@code personaId} points at; the import endpoint rewrites it to the
 * persisted UUID. Give every persona a stable {@code id} when any
 * {@code persona} node references it.
 *
 * <p><strong>Note:</strong> the prompt field is {@code systemInstruction}
 * (matching the backend column), not {@code systemPrompt} — the latter was
 * silently dropped on import in earlier DSL builds.
 */
export interface PersonaDeclaration {
  /** Transient id referenced by a {@code persona} node's {@code personaId}. */
  id?: string;
  name: string;
  description?: string;
  /** The persona's system prompt. Backend column: {@code systemInstruction}. */
  systemInstruction?: string;
  tone?: PersonaTone;
  /** 1–5 register dial; the backend coerces 0 → 3. */
  verbosity?: number;
  languageStyle?: PersonaLanguageStyle;
  /** Pipe-separated terms the voice must include (regex alternation). */
  mandatoryTerms?: string;
  /** Pipe-separated terms the voice must avoid. */
  forbiddenTerms?: string;
  enabled?: 0 | 1;
}

/* ─────────────────────── Top-level flow spec ─────────────────────── */

/**
 * The DSL author writes one of these per `.flow.ts` file and exports it
 * as the default export (or named export consumed by their build script).
 */
export interface FlowSpec {
  /** Optional transient id for cross-flow {@code subFlowId} wiring in a bundle. */
  id?: string;
  name: string;
  description?: string;
  enabled?: 0 | 1;
  guardrailMethod?: GuardrailMethod;
  triggerDescription?: string;
  triggerMode?: TriggerMode;
  triggerLanguage?: TriggerLanguage;
  nodes: readonly FlowNode[];
  edges: readonly FlowEdge[];
  slots?: readonly SlotDeclaration[];
  personas?: readonly PersonaDeclaration[];
}

/* ─────────────────────── Transpiled wire format ─────────────────────── */

/**
 * Output of {@link transpileFlow}. Matches the editor's "Export JSON" shape
 * ({@code ChatFlowExportEntry} on the frontend) so the file can be dropped
 * straight into the {@code POST /api/ai-agent/{agentId}/chat-flow/import}
 * endpoint or onto the editor's "Import JSON" button.
 */
export interface TranspiledFlow {
  id?: string;
  name: string;
  description: string | null;
  guardrailMethod: GuardrailMethod;
  triggerDescription: string | null;
  triggerMode: TriggerMode;
  triggerLanguage: TriggerLanguage;
  slots?: SlotDeclaration[];
  personas?: PersonaDeclaration[];
  graph: {
    nodes: TranspiledNode[];
    edges: TranspiledEdge[];
  };
}

export interface TranspiledNode {
  id: string;
  type: NodeType;
  position: { x: number; y: number };
  data: TranspiledNodeData;
}

/** Loosely-typed data bag — the editor and engine read fields by name. */
export type TranspiledNodeData = Record<string, unknown> & {
  label: string;
  type: NodeType;
};

export interface TranspiledEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle: string | null;
  targetHandle: string | null;
  label: string | null;
}
