import type { Edge, Node } from "@xyflow/react";

import type { FlowFormField, FlowNodeData } from "./types";

/**
 * T234 / §VII.13.e — pure graph transform that collapses a linear run of
 * question nodes (`aiQuestion` + single-field `formCapture`) into one native
 * multi-field `formCapture` (T107). No React, no side effects — the editor
 * wires the result into `setNodes`/`setEdges`, and the unit tests exercise
 * this function directly.
 *
 * @since 2026.3.1
 */

/** Why the forward walk stopped — surfaced in the confirm dialog. */
export type ConversionStopReason =
  | "branching" // current node has ≠1 outgoing edge, or the edge carries a sourceHandle
  | "fan_in" // the next node has more than one incoming edge
  | "non_question" // the next node is not a collapsible question (switch, slot, end, native form, …)
  | "routes_switch" // the next question's chips route a downstream switch — collapsing would drop the branch
  | "dead_end"; // the run ended on a node with no outgoing edge

export interface FormConversionPlan {
  /** The new `formCapture` node (reuses the first node's id + position). */
  readonly formNode: Node<FlowNodeData>;
  /** Ids of the nodes that were folded away (everything after the first). */
  readonly collapsedIds: string[];
  /** The resulting node array (first node replaced, the rest removed). */
  readonly nextNodes: Node<FlowNodeData>[];
  /** The resulting edge array (internal edges dropped, tail redirected). */
  readonly nextEdges: Edge[];
  /** The fields the form will carry — one per collapsed question, for preview. */
  readonly fields: FlowFormField[];
  /** How many question nodes were collapsed. */
  readonly runLength: number;
  /** Why the walk stopped (so the UI can explain a partial collapse). */
  readonly stopReason: ConversionStopReason;
}

const QUESTION_TYPES = new Set(["aiQuestion", "formCapture"]);

/** A node is collapsible iff it's an aiQuestion, or a formCapture that is NOT already a native form. */
function isCollapsibleQuestion(node: Node<FlowNodeData> | undefined): boolean {
  if (!node || !QUESTION_TYPES.has(node.data.type)) return false;
  // A formCapture that already declares formFields is itself a native form —
  // never re-collapse it.
  if (node.data.type === "formCapture" && (node.data.formFields?.length ?? 0) > 0) {
    return false;
  }
  return true;
}

/** Map a node's `validationRule` to a sensible default form-field widget type. */
function inferTypeFromValidation(rule: string | undefined): string {
  switch (rule) {
    case "email":
      return "email";
    case "phone":
      return "tel";
    case "number":
      return "number";
    case "date":
      return "date";
    default:
      return "text";
  }
}

/** First non-empty line of an instruction, trimmed and capped — used as the field label. */
function deriveLabel(node: Node<FlowNodeData>): string {
  const instr = node.data.aiInstruction?.split(/\r?\n/).find((l) => l.trim().length > 0);
  const base = (instr ?? node.data.label ?? "").trim();
  return base.length > 60 ? `${base.slice(0, 57)}…` : base;
}

function fieldFromNode(node: Node<FlowNodeData>): FlowFormField {
  const chips = node.data.inlineOptions ?? [];
  const isSelect = chips.length > 0;
  const field: FlowFormField = {
    name: node.data.outputVariable ?? "",
    label: deriveLabel(node),
    type: isSelect ? "select" : inferTypeFromValidation(node.data.validationRule),
    required: true,
  };
  if (node.data.validationRule) field.validationRule = node.data.validationRule;
  if (isSelect) field.options = [...chips];
  return field;
}

/**
 * Plans the collapse of the run starting at {@link startNodeId}. Returns
 * `null` when there is no run worth collapsing (start isn't a collapsible
 * question, or fewer than two consecutive questions chain off it).
 */
export function planFormConversion(
  nodes: Node<FlowNodeData>[],
  edges: Edge[],
  startNodeId: string,
): FormConversionPlan | null {
  const nodeById = new Map(nodes.map((n) => [n.id, n]));
  const outgoing = new Map<string, Edge[]>();
  const incomingCount = new Map<string, number>();
  for (const e of edges) {
    const list = outgoing.get(e.source);
    if (list) list.push(e);
    else outgoing.set(e.source, [e]);
    incomingCount.set(e.target, (incomingCount.get(e.target) ?? 0) + 1);
  }

  const start = nodeById.get(startNodeId);
  if (!isCollapsibleQuestion(start)) return null;

  const run: Node<FlowNodeData>[] = [start!];
  let stopReason: ConversionStopReason = "dead_end";

  // Walk forward through the simple linear chain.
  for (;;) {
    const current = run[run.length - 1];
    const outs = outgoing.get(current.id) ?? [];
    if (outs.length !== 1) {
      stopReason = "branching";
      break;
    }
    const edge = outs[0];
    if (edge.sourceHandle) {
      stopReason = "branching";
      break;
    }
    const next = nodeById.get(edge.target);
    if (!next) {
      stopReason = "dead_end";
      break;
    }
    if ((incomingCount.get(next.id) ?? 0) > 1) {
      stopReason = "fan_in";
      break;
    }
    if (!isCollapsibleQuestion(next)) {
      stopReason = "non_question";
      break;
    }
    // The next question's chips route a downstream switch → folding it into a
    // plain form field would silently drop the branch. Stop before it.
    const nextOuts = outgoing.get(next.id) ?? [];
    const nextTarget = nextOuts.length === 1 ? nodeById.get(nextOuts[0].target) : undefined;
    if ((next.data.inlineOptions?.length ?? 0) > 0 && nextTarget?.data.type === "switch") {
      stopReason = "routes_switch";
      break;
    }
    run.push(next);
  }

  if (run.length < 2) return null;

  const first = run[0];
  const last = run[run.length - 1];
  const runIds = new Set(run.map((n) => n.id));
  const fields = run.map(fieldFromNode);

  const formNode: Node<FlowNodeData> = {
    ...first,
    type: "formCapture",
    data: {
      ...first.data,
      type: "formCapture",
      label: first.data.label || "Form",
      // Seed the form heading from the first question's instruction; the
      // author refines it after the deterministic collapse.
      aiInstruction: first.data.aiInstruction,
      formFields: fields,
      // The single-field carry-overs no longer apply to a multi-field form.
      outputVariable: undefined,
      validationRule: undefined,
      inlineOptions: undefined,
    },
  };

  // Rewire edges: drop every edge sourced from a run node (all internal or the
  // tail), keep predecessor + unrelated edges, then re-add the tail redirected
  // to the new form node.
  const tailEdge = (outgoing.get(last.id) ?? []).find((e) => !runIds.has(e.target));
  const nextEdges: Edge[] = edges.filter((e) => {
    if (runIds.has(e.source) && runIds.has(e.target)) return false; // internal
    if (runIds.has(e.source)) return false; // tail (re-added) / stray run sources
    return true; // predecessor (target = first) + unrelated
  });
  if (tailEdge) {
    nextEdges.push({ ...tailEdge, source: first.id });
  }

  const collapsedIds = run.slice(1).map((n) => n.id);
  const nextNodes = nodes
    .filter((n) => !runIds.has(n.id) || n.id === first.id)
    .map((n) => (n.id === first.id ? formNode : n));

  return {
    formNode,
    collapsedIds,
    nextNodes,
    nextEdges,
    fields,
    runLength: run.length,
    stopReason,
  };
}
