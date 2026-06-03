import type { Edge, Node } from "@xyflow/react";
import { describe, expect, it } from "vitest";

import { planFormConversion } from "../chat-flow.form-converter";
import type { FlowNodeData, FlowNodeType } from "../types";

/* ───────────────────────────── helpers ───────────────────────────── */

function node(
  id: string,
  type: FlowNodeType,
  data: Partial<FlowNodeData> = {},
): Node<FlowNodeData> {
  return {
    id,
    type,
    position: { x: 0, y: 0 },
    data: { label: id, type, ...data },
  };
}

function q(id: string, data: Partial<FlowNodeData> = {}): Node<FlowNodeData> {
  return node(id, "aiQuestion", data);
}

function edge(source: string, target: string, sourceHandle?: string): Edge {
  return { id: `${source}-${target}`, source, target, sourceHandle };
}

/* ───────────────────────────── tests ───────────────────────────── */

describe("planFormConversion (T234)", () => {
  it("collapses a 3-question linear run into one native form", () => {
    const nodes = [
      node("start", "start"),
      q("q1", { outputVariable: "name", aiInstruction: "Qual seu nome?" }),
      q("q2", { outputVariable: "email", validationRule: "email", aiInstruction: "Seu e-mail?" }),
      q("q3", { outputVariable: "role", inlineOptions: ["Aluno", "Gestor"] }),
      node("end", "end"),
    ];
    const edges = [
      edge("start", "q1"),
      edge("q1", "q2"),
      edge("q2", "q3"),
      edge("q3", "end"),
    ];

    const plan = planFormConversion(nodes, edges, "q1");
    expect(plan).not.toBeNull();
    expect(plan!.runLength).toBe(3);
    expect(plan!.stopReason).toBe("non_question");

    // The form node reuses q1's id + position and carries one field per question.
    expect(plan!.formNode.id).toBe("q1");
    expect(plan!.formNode.data.type).toBe("formCapture");
    expect(plan!.fields.map((f) => f.name)).toEqual(["name", "email", "role"]);
    // email field inherits its validationRule + inferred widget.
    expect(plan!.fields[1]).toMatchObject({ type: "email", validationRule: "email" });
    // chip list becomes a select with its options.
    expect(plan!.fields[2]).toMatchObject({ type: "select", options: ["Aluno", "Gestor"] });

    // q2/q3 removed; start, form(q1), end remain.
    expect(plan!.collapsedIds).toEqual(["q2", "q3"]);
    expect(plan!.nextNodes.map((n) => n.id).sort()).toEqual(["end", "q1", "start"]);

    // Edges rewired to start → q1(form) → end.
    expect(plan!.nextEdges.map((e) => `${e.source}->${e.target}`).sort()).toEqual([
      "q1->end",
      "start->q1",
    ]);
  });

  it("stops at a non-question node (switch) and collapses the run before it", () => {
    const nodes = [
      node("start", "start"),
      q("q1", { outputVariable: "name" }),
      q("q2", { outputVariable: "email" }),
      node("sw", "switch"),
      node("end", "end"),
    ];
    const edges = [edge("start", "q1"), edge("q1", "q2"), edge("q2", "sw"), edge("sw", "end")];

    const plan = planFormConversion(nodes, edges, "q1");
    expect(plan!.runLength).toBe(2);
    expect(plan!.stopReason).toBe("non_question");
    // The redirected tail points the form at the switch.
    expect(plan!.nextEdges).toContainEqual(expect.objectContaining({ source: "q1", target: "sw" }));
  });

  it("excludes a question whose chips route a downstream switch (routes_switch)", () => {
    const nodes = [
      q("q0", { outputVariable: "name" }),
      q("q1", { outputVariable: "email" }),
      q("q2", { outputVariable: "track", inlineOptions: ["Vendas", "Cursos"] }),
      node("sw", "switch"),
      node("end", "end"),
    ];
    const edges = [edge("q0", "q1"), edge("q1", "q2"), edge("q2", "sw"), edge("sw", "end")];

    const plan = planFormConversion(nodes, edges, "q0");
    expect(plan!.runLength).toBe(2);
    expect(plan!.stopReason).toBe("routes_switch");
    expect(plan!.fields.map((f) => f.name)).toEqual(["name", "email"]);
    // q2 (the routing question) survives untouched.
    expect(plan!.nextNodes.map((n) => n.id)).toContain("q2");
  });

  it("stops on fan-in (a downstream node with >1 incoming edge)", () => {
    const nodes = [q("q0", { outputVariable: "a" }), q("q1", { outputVariable: "b" }), q("q2"), node("x", "aiQuestion")];
    const edges = [edge("q0", "q1"), edge("q1", "q2"), edge("x", "q2")];

    const plan = planFormConversion(nodes, edges, "q0");
    expect(plan!.runLength).toBe(2);
    expect(plan!.stopReason).toBe("fan_in");
  });

  it("stops on branching (a node with >1 outgoing edge)", () => {
    const nodes = [q("q0", { outputVariable: "a" }), q("q1", { outputVariable: "b" }), node("e1", "end"), node("e2", "end")];
    const edges = [edge("q0", "q1"), edge("q1", "e1"), edge("q1", "e2")];

    const plan = planFormConversion(nodes, edges, "q0");
    expect(plan!.runLength).toBe(2);
    expect(plan!.stopReason).toBe("branching");
  });

  it("returns null for a single question (nothing to collapse)", () => {
    const nodes = [q("q1", { outputVariable: "name" }), node("end", "end")];
    const edges = [edge("q1", "end")];
    expect(planFormConversion(nodes, edges, "q1")).toBeNull();
  });

  it("refuses to re-collapse an existing native form", () => {
    const nodes = [
      node("form", "formCapture", { formFields: [{ name: "x" }] }),
      q("q1", { outputVariable: "y" }),
      node("end", "end"),
    ];
    const edges = [edge("form", "q1"), edge("q1", "end")];
    expect(planFormConversion(nodes, edges, "form")).toBeNull();
  });

  it("does not start from a non-question node", () => {
    const nodes = [node("start", "start"), q("q1", { outputVariable: "name" }), node("end", "end")];
    const edges = [edge("start", "q1"), edge("q1", "end")];
    expect(planFormConversion(nodes, edges, "start")).toBeNull();
  });
});
