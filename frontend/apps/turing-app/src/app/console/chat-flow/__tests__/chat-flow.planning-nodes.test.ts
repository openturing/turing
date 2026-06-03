import { describe, expect, it } from "vitest";

import {
  buildDefaultNodeData,
  DEFAULT_LABEL,
  deserializeGraph,
  serializeGraph,
} from "../chat-flow.serialize";
import { NODE_COLORS } from "../types";
import type { FlowNodeData } from "../types";

/**
 * T108-3 — pins the editor wiring for the planningStep / iteratePlan node
 * types: default-data seeding, the label/colour maps stay exhaustive, and the
 * new `planSchema` / `completionMode` fields round-trip through the graph
 * serializer (so flow export/import preserves them).
 */
describe("planning node editor wiring (T108-3)", () => {
  it("seeds a planningStep with the reserved __plan slot", () => {
    const data = buildDefaultNodeData("planningStep");
    expect(data.type).toBe("planningStep");
    expect(data.outputVariable).toBe("__plan");
    // No completionMode on a planning step.
    expect(data.completionMode).toBeUndefined();
  });

  it("seeds an iteratePlan with __plan slot and mark_done completion", () => {
    const data = buildDefaultNodeData("iteratePlan");
    expect(data.type).toBe("iteratePlan");
    expect(data.outputVariable).toBe("__plan");
    expect(data.completionMode).toBe("mark_done");
  });

  it("has label + colour entries for both new node types", () => {
    expect(DEFAULT_LABEL.planningStep).toBeTruthy();
    expect(DEFAULT_LABEL.iteratePlan).toBeTruthy();
    expect(NODE_COLORS.planningStep).toBeDefined();
    expect(NODE_COLORS.iteratePlan).toBeDefined();
  });

  it("round-trips planSchema and completionMode through serialize/deserialize", () => {
    const planning: FlowNodeData = {
      label: "Plan",
      type: "planningStep",
      aiInstruction: "Break the goal into steps",
      outputVariable: "__plan",
      planSchema: "List<{id, title, status}>",
    };
    const iterate: FlowNodeData = {
      label: "Iterate",
      type: "iteratePlan",
      outputVariable: "__plan",
      subFlowId: "body-flow",
      completionMode: "remove",
    };
    const nodes = [
      { id: "p", type: "planningStep", position: { x: 0, y: 0 }, data: planning },
      { id: "i", type: "iteratePlan", position: { x: 10, y: 0 }, data: iterate },
    ];

    const json = serializeGraph(nodes, []);
    const back = deserializeGraph(json);

    const p = back.nodes.find((n) => n.id === "p");
    const i = back.nodes.find((n) => n.id === "i");
    expect(p?.data.planSchema).toBe("List<{id, title, status}>");
    expect(i?.data.completionMode).toBe("remove");
    expect(i?.data.subFlowId).toBe("body-flow");
  });
});
