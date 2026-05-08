import type { Edge, Node } from "@xyflow/react";

import type { FlowNodeData, FlowNodeType } from "./types";

export const DEFAULT_LABEL: Record<FlowNodeType, string> = {
  start: "START",
  end: "END",
  aiQuestion: "AI QUESTION",
  condition: "CONDITION",
  functionCall: "FUNCTION CALL",
  subFlow: "SUB FLOW",
  persona: "PERSONA SWITCH",
};

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
      type: "smoothstep",
    })) as Edge[];
    return { nodes: nodes.length > 0 ? nodes : INITIAL_NODES, edges };
  } catch {
    return { nodes: INITIAL_NODES, edges: INITIAL_EDGES };
  }
}
