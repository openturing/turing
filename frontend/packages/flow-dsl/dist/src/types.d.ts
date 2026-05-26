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
 * engine advances. Mirrors the validators wired into the runtime.
 */
export type ValidationRule = "email" | "phone" | "url" | "number" | "date" | "none";
/**
 * All node kinds the editor (and engine) understand. New types added on
 * the backend must be listed here for the DSL to compile-check them.
 */
export type NodeType = "start" | "end" | "aiQuestion" | "formCapture" | "condition" | "functionCall" | "subFlow" | "persona" | "switch" | "slot" | "writeSlot";
/** One branch on a {@code switch} node — {@code id} is also the edge {@code sourceHandle}. */
export interface SwitchOption {
    id: string;
    label: string;
}
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
export interface FormCaptureNode extends NodeBase {
    type: "formCapture";
    aiInstruction?: string;
    outputVariable?: string;
    validationRule?: ValidationRule;
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
    aiInstruction?: string;
}
/** Sub-shape: MCP-server-hosted tool. */
interface FunctionCallMcpNode extends NodeBase {
    type: "functionCall";
    toolSource: "MCP";
    mcpServerId: string;
    aiInstruction?: string;
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
export type FlowNode = StartNode | EndNode | AiQuestionNode | FormCaptureNode | ConditionNode | FunctionCallNode | SubFlowNode | PersonaNode | SwitchNode | SlotNode | WriteSlotNode;
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
/** Slot type supported by the runtime. Must match {@code TurAIAgentSlotType}. */
export type SlotType = "STRING" | "NUMBER" | "BOOLEAN" | "DATE" | "EMAIL" | "PHONE" | "URL" | "JSON";
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
/** Persona embedded in the export bundle — auto-created on import by name. */
export interface PersonaDeclaration {
    name: string;
    description?: string;
    systemPrompt?: string;
}
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
    position: {
        x: number;
        y: number;
    };
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
export {};
//# sourceMappingURL=types.d.ts.map