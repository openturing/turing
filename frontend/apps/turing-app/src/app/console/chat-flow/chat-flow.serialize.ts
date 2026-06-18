import type { Edge, Node } from "@xyflow/react";

import { failureEdgeOverlay } from "./components/flow-edges";
import type { FlowNodeData, FlowNodeType, FlowSwitchOption } from "./types";

export const DEFAULT_LABEL: Record<FlowNodeType, string> = {
  start: "START",
  end: "END",
  aiQuestion: "AI QUESTION",
  formCapture: "FORM CAPTURE",
  condition: "CONDITION",
  functionCall: "FUNCTION CALL",
  scheduleAgent: "SCHEDULED ROUTINE",
  subFlow: "SUB FLOW",
  subFlowSwitch: "SUB-FLOW SWITCH",
  persona: "PERSONA SWITCH",
  switch: "SWITCH",
  slot: "SLOT",
  writeSlot: "WRITE SLOT",
  webhook: "WEBHOOK",
  suspend: "SUSPEND",
  humanApproval: "HUMAN APPROVAL",
  planningStep: "PLANNING STEP",
  iteratePlan: "ITERATE PLAN",
};

/**
 * Generates the initial `FlowNodeData` for a freshly dropped node. Switch nodes seed two empty
 * options so the user sees the multi-handle shape immediately and can rename in place.
 *
 * @since 2026.2.7
 */
export function buildDefaultNodeData(type: FlowNodeType): FlowNodeData {
  const base: FlowNodeData = { label: DEFAULT_LABEL[type], type };
  if (type === "switch" || type === "subFlowSwitch") {
    // subFlowSwitch reuses the switchOptions shape — each option will also
    // get a subFlowId picker via FlowProperties (T47). Seed both kinds with
    // two empty options so the multi-row shape is visible immediately.
    base.switchOptions = [
      { id: newSwitchOptionId(), label: "Option 1" },
      { id: newSwitchOptionId(), label: "Option 2" },
    ];
  }
  if (type === "slot") {
    base.slotOperation = "SET";
  }
  if (type === "planningStep" || type === "iteratePlan") {
    // Both default to the reserved `__plan` slot (the backend's
    // DEFAULT_PLAN_SLOT) so a planningStep → iteratePlan pair wires up with no
    // extra config; the author can point them at a custom slot if needed.
    base.outputVariable = "__plan";
  }
  if (type === "iteratePlan") {
    base.completionMode = "mark_done";
  }
  return base;
}

export function newSwitchOptionId(): string {
  return `opt-${crypto.randomUUID().slice(0, 8)}`;
}

export function ensureSwitchOptions(data: FlowNodeData): FlowSwitchOption[] {
  return Array.isArray(data.switchOptions) ? data.switchOptions : [];
}

/**
 * Starting zoom level for the canvas. We surface this value to the user as
 * "100%", mapping the raw React Flow zoom to a percentage relative to this
 * baseline (so zooming out reads as < 100% and zooming in as > 100%,
 * regardless of the underlying transform).
 */
export const INITIAL_ZOOM = 0.8;
export const INITIAL_VIEWPORT = { x: 40, y: 40, zoom: INITIAL_ZOOM };

export const INITIAL_NODES: Node<FlowNodeData>[] = [
  {
    id: "start-1",
    type: "start",
    position: { x: 80, y: 240 },
    data: { label: "START", type: "start" },
  },
];

export const INITIAL_EDGES: Edge[] = [];

interface SerializedFlow {
  nodes: { id: string; type?: string; position: { x: number; y: number }; data: FlowNodeData }[];
  edges: {
    id: string;
    source: string;
    target: string;
    sourceHandle?: string | null;
    targetHandle?: string | null;
    label?: string | null;
  }[];
}

export function serializeGraph(nodes: Node<FlowNodeData>[], edges: Edge[]): string {
  const payload: SerializedFlow = {
    nodes: nodes.map((n) => ({
      id: n.id,
      type: n.type,
      position: n.position,
      data: n.data,
    })),
    edges: edges.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle,
      targetHandle: e.targetHandle,
      label: typeof e.label === "string" ? e.label : null,
    })),
  };
  return JSON.stringify(payload, null, 2);
}

export function deserializeGraph(json: string | null | undefined): {
  nodes: Node<FlowNodeData>[];
  edges: Edge[];
} {
  if (!json?.trim()) {
    return { nodes: INITIAL_NODES, edges: INITIAL_EDGES };
  }
  try {
    const parsed = JSON.parse(json) as Partial<SerializedFlow>;
    const nodes = (parsed.nodes ?? []).map((n) => ({
      id: n.id,
      type: n.type,
      position: n.position,
      data: n.data,
    })) as Node<FlowNodeData>[];
    const edges = (parsed.edges ?? []).map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle ?? null,
      targetHandle: e.targetHandle ?? null,
      label: e.label ?? undefined,
      // T50 — re-derive the visual type from sourceHandle so failure edges
      // persisted before/after T50 both render as red try/catch edges. The
      // overlay also sets the animated dashed look and the red arrow marker.
      ...failureEdgeOverlay(e.sourceHandle ?? null),
    })) as Edge[];
    return { nodes: nodes.length > 0 ? nodes : INITIAL_NODES, edges };
  } catch {
    return { nodes: INITIAL_NODES, edges: INITIAL_EDGES };
  }
}
