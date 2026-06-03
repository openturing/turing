import type {
  FlowEdge,
  FlowNode,
  FlowSpec,
  NodeType,
  SlotDeclaration,
  SwitchNode,
  TranspiledEdge,
  TranspiledFlow,
  TranspiledNode,
  TranspiledNodeData,
} from "./types.js";

/**
 * Thrown by {@link transpileFlow} when the DSL spec has a structural
 * problem the runtime engine cannot recover from (duplicate ids, missing
 * start node, dangling edge, condition node without two branches, switch
 * branch pointing at an undeclared {@link SwitchNode#switchOptions} id).
 *
 * <p>Compile-time enum typos are caught by TypeScript first; this error
 * is for the structural rules a static type system can't express.
 *
 * @since 2026.3.1
 */
export class FlowSpecError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "FlowSpecError";
  }
}

/** Default uppercase card title per node type — matches the editor's `DEFAULT_LABEL`. */
const DEFAULT_LABEL: Record<NodeType, string> = {
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
  suspend: "SUSPEND",
};

/**
 * Vertical waterfall spacing. The editor recomputes layout on first open
 * via {@code dagre}; these positions just guarantee the canvas isn't a
 * jumbled stack of overlapping cards if the author opens the flow before
 * running an auto-layout.
 */
const LAYOUT_Y_STEP = 120;
const LAYOUT_X_ORIGIN = 0;
const LAYOUT_Y_ORIGIN = 0;

/**
 * Translate a typed {@link FlowSpec} into the editor's wire JSON shape
 * ({@link TranspiledFlow}).
 *
 * <p>What the transpiler does:
 * <ul>
 *   <li>Fills the {@code label} default per node type when omitted.</li>
 *   <li>Lays nodes out in a vertical waterfall (x=0, y=index*120) so an
 *       author opening the file in the editor gets a readable canvas
 *       before running an auto-layout.</li>
 *   <li>Synthesizes edge ids ({@code e1}, {@code e2}, …) when the author
 *       didn't supply one.</li>
 *   <li>Coerces empty optional fields ({@code description},
 *       {@code triggerDescription}) to {@code null} so the output matches
 *       what {@code buildImportPayloadFromExport} expects.</li>
 *   <li>Sets defaults for the three trigger-related enums
 *       ({@code guardrailMethod=LLM_JUDGE}, {@code triggerMode=ONCE},
 *       {@code triggerLanguage=AUTO}) — same defaults the import endpoint
 *       falls back to when the field is missing or invalid.</li>
 * </ul>
 *
 * <p>What it validates:
 * <ul>
 *   <li>Exactly one {@code start} node, no duplicate node ids.</li>
 *   <li>Every edge {@code source}/{@code target} matches a declared node.</li>
 *   <li>Every {@code condition} node has exactly one outgoing edge with
 *       {@code sourceHandle="yes"} and one with {@code sourceHandle="no"}.</li>
 *   <li>Every outgoing edge of a {@code switch} node has a
 *       {@code sourceHandle} matching one of the node's
 *       {@code switchOptions} ids.</li>
 * </ul>
 *
 * <p>Throws {@link FlowSpecError} on any of the above. Anything else
 * (unreachable nodes, dead-end {@code aiQuestion} nodes) is the chat-flow
 * linter's job at edit time, not the transpiler's at build time.
 *
 * @since 2026.3.1
 */
export function transpileFlow(spec: FlowSpec): TranspiledFlow {
  const nodes = spec.nodes ?? [];
  const edges = spec.edges ?? [];
  validate(nodes, edges);

  const transpiledNodes = nodes.map((node, index) => buildNode(node, index));
  const transpiledEdges = edges.map((edge, index) => buildEdge(edge, index));

  const result: TranspiledFlow = {
    name: spec.name,
    description: trimOrNull(spec.description),
    guardrailMethod: spec.guardrailMethod ?? "LLM_JUDGE",
    triggerDescription: trimOrNull(spec.triggerDescription),
    triggerMode: spec.triggerMode ?? "ONCE",
    triggerLanguage: spec.triggerLanguage ?? "AUTO",
    graph: {
      nodes: transpiledNodes,
      edges: transpiledEdges,
    },
  };

  if (spec.id !== undefined) {
    result.id = spec.id;
  }
  if (spec.slots && spec.slots.length > 0) {
    result.slots = dedupeSlots(spec.slots, nodes);
  }
  if (spec.personas && spec.personas.length > 0) {
    result.personas = [...spec.personas];
  }
  return result;
}

/**
 * Transpile a multi-flow bundle (a main flow plus the sub-flows it
 * descends into) into the array wire shape consumed by the editor's
 * "Import Bundle" button and the backend
 * {@code POST /api/ai-agent/{agentId}/chat-flow/import-bundle} endpoint.
 *
 * <p>Beyond running every flow through {@link transpileFlow} (which checks
 * intra-flow structure), the bundle pass adds the cross-flow rules a
 * single-flow transpile cannot see:
 * <ul>
 *   <li>Every {@link FlowSpec#id} is present and unique — the transient id
 *       a {@code subFlow}/{@code subFlowSwitch} reference resolves against.</li>
 *   <li>Every {@code subFlow} node's {@code subFlowId} and every
 *       {@code subFlowSwitch}/{@code switch} option's {@code subFlowId}
 *       points at a flow declared in the same bundle. A typo here would
 *       otherwise only surface at runtime as a silently-skipped descent
 *       (the engine logs "Sub Flow … not found" and advances).</li>
 * </ul>
 *
 * <p>Side effect: populates each {@code subFlowName} (on {@code subFlow}
 * nodes and on matched {@code switchOptions}) from the referenced flow's
 * {@code name}, mirroring what the backend import endpoint writes so the
 * emitted JSON renders with readable labels in the editor.
 *
 * <p>The returned array is the import-bundle payload: serialize it with
 * {@code JSON.stringify(bundle, null, 2)} and drop it on the endpoint.
 * The backend re-assigns fresh UUIDs and rewrites the transient
 * {@code subFlowId}/{@code personaId} references on its side.
 *
 * @throws FlowSpecError on a missing/duplicate flow id or a dangling
 *     cross-flow {@code subFlowId} reference.
 * @since 2026.3.1
 */
export function transpileBundle(specs: readonly FlowSpec[]): TranspiledFlow[] {
  if (!specs || specs.length === 0) {
    throw new FlowSpecError("Bundle has no flows.");
  }
  const idToName = new Map<string, string>();
  for (const spec of specs) {
    const id = spec.id?.trim();
    if (!id) {
      throw new FlowSpecError(
        `Flow '${spec.name}' has no id — every flow in a bundle needs a stable id for cross-flow subFlowId wiring.`,
      );
    }
    if (idToName.has(id)) {
      throw new FlowSpecError(`Duplicate flow id in bundle: '${id}'.`);
    }
    idToName.set(id, spec.name);
  }

  // Validate every cross-flow subFlowId reference resolves inside the bundle.
  for (const spec of specs) {
    for (const ref of collectSubFlowRefs(spec)) {
      if (!idToName.has(ref.subFlowId)) {
        throw new FlowSpecError(
          `Flow '${spec.id}' node '${ref.nodeId}' references subFlowId '${ref.subFlowId}', ` +
            `which is not a flow in the bundle. Known ids: ${[...idToName.keys()].join(", ")}.`,
        );
      }
    }
  }

  return specs.map((spec) => {
    const transpiled = transpileFlow(spec);
    fillSubFlowNames(transpiled, idToName);
    return transpiled;
  });
}

interface SubFlowRef {
  nodeId: string;
  subFlowId: string;
}

/** Every {@code subFlowId} a flow points at — from {@code subFlow} nodes and switch options. */
function collectSubFlowRefs(spec: FlowSpec): SubFlowRef[] {
  const refs: SubFlowRef[] = [];
  for (const node of spec.nodes) {
    if (node.type === "subFlow" && node.subFlowId?.trim()) {
      refs.push({ nodeId: node.id, subFlowId: node.subFlowId.trim() });
    }
    if (node.type === "subFlowSwitch" || node.type === "switch") {
      for (const option of node.switchOptions) {
        if (option.subFlowId?.trim()) {
          refs.push({ nodeId: node.id, subFlowId: option.subFlowId.trim() });
        }
      }
    }
  }
  return refs;
}

/** Writes the referenced flow's name into {@code subFlowName} on transpiled nodes/options. */
function fillSubFlowNames(transpiled: TranspiledFlow, idToName: Map<string, string>): void {
  for (const node of transpiled.graph.nodes) {
    const data = node.data as Record<string, unknown>;
    const subFlowId = typeof data.subFlowId === "string" ? data.subFlowId : undefined;
    if (subFlowId && idToName.has(subFlowId)) {
      data.subFlowName = idToName.get(subFlowId);
    }
    const options = data.switchOptions;
    if (Array.isArray(options)) {
      for (const option of options as Array<Record<string, unknown>>) {
        const optId = typeof option.subFlowId === "string" ? option.subFlowId : undefined;
        if (optId && idToName.has(optId)) {
          option.subFlowName = idToName.get(optId);
        }
      }
    }
  }
}

/* ─────────────────────── Validation ─────────────────────── */

function validate(nodes: readonly FlowNode[], edges: readonly FlowEdge[]): void {
  if (nodes.length === 0) {
    throw new FlowSpecError("Flow has no nodes.");
  }

  const ids = new Set<string>();
  for (const node of nodes) {
    if (!node.id) {
      throw new FlowSpecError(`Node of type '${node.type}' is missing an id.`);
    }
    if (ids.has(node.id)) {
      throw new FlowSpecError(`Duplicate node id: '${node.id}'.`);
    }
    ids.add(node.id);
  }

  const startCount = nodes.filter((n) => n.type === "start").length;
  if (startCount === 0) {
    throw new FlowSpecError("Flow has no 'start' node.");
  }
  if (startCount > 1) {
    throw new FlowSpecError(`Flow has ${startCount} 'start' nodes; expected exactly one.`);
  }

  for (const edge of edges) {
    if (!ids.has(edge.source)) {
      throw new FlowSpecError(`Edge source '${edge.source}' does not match any node id.`);
    }
    if (!ids.has(edge.target)) {
      throw new FlowSpecError(`Edge target '${edge.target}' does not match any node id.`);
    }
  }

  // Per-source-node structural rules.
  const byId = new Map(nodes.map((n) => [n.id, n] as const));
  const outgoingByNodeId = new Map<string, FlowEdge[]>();
  for (const edge of edges) {
    const list = outgoingByNodeId.get(edge.source) ?? [];
    list.push(edge);
    outgoingByNodeId.set(edge.source, list);
  }

  for (const [nodeId, outgoing] of outgoingByNodeId) {
    const node = byId.get(nodeId);
    if (!node) continue;
    if (node.type === "condition") {
      validateConditionBranches(node.id, outgoing);
    } else if (node.type === "switch") {
      validateSwitchBranches(node, outgoing);
    }
  }

  // Non-start nodes must have at least one incoming edge, otherwise the
  // engine can never reach them. Catching this here is friendlier than
  // discovering it in production.
  const incomingTargets = new Set(edges.map((e) => e.target));
  for (const node of nodes) {
    if (node.type !== "start" && !incomingTargets.has(node.id)) {
      throw new FlowSpecError(
        `Node '${node.id}' (${node.type}) has no incoming edge — it is unreachable.`,
      );
    }
  }
}

function validateConditionBranches(nodeId: string, outgoing: FlowEdge[]): void {
  const handles = outgoing.map((e) => e.sourceHandle);
  const yesCount = handles.filter((h) => h === "yes").length;
  const noCount = handles.filter((h) => h === "no").length;
  if (yesCount !== 1 || noCount !== 1 || outgoing.length !== 2) {
    throw new FlowSpecError(
      `Condition node '${nodeId}' must have exactly two outgoing edges with sourceHandle "yes" and "no" (got yes=${yesCount}, no=${noCount}, total=${outgoing.length}).`,
    );
  }
}

function validateSwitchBranches(node: SwitchNode, outgoing: FlowEdge[]): void {
  const optionIds = new Set(node.switchOptions.map((o) => o.id));
  let wildcards = 0;
  for (const edge of outgoing) {
    // A blank/absent sourceHandle is the wildcard (no-match) fallback the
    // engine resolves via ChatFlowOps#pickConditionEdge — legal and, per the
    // runtime docs, recommended on every switch for coverage. Only a
    // *non-blank* handle that matches no option id is an authoring error.
    if (!edge.sourceHandle || edge.sourceHandle.trim() === "") {
      wildcards++;
      continue;
    }
    if (!optionIds.has(edge.sourceHandle)) {
      throw new FlowSpecError(
        `Switch node '${node.id}' outgoing edge to '${edge.target}' uses sourceHandle '${edge.sourceHandle}' — must match one of: ${[...optionIds].join(", ")} (or be left blank for the wildcard fallback).`,
      );
    }
  }
  if (wildcards > 1) {
    throw new FlowSpecError(
      `Switch node '${node.id}' has ${wildcards} wildcard (blank-sourceHandle) edges — keep at most one as the no-match fallback.`,
    );
  }
}

/* ─────────────────────── Per-node materialization ─────────────────────── */

function buildNode(node: FlowNode, index: number): TranspiledNode {
  const label = node.label ?? DEFAULT_LABEL[node.type];
  const data: TranspiledNodeData = { label, type: node.type };
  copyDefinedFields(data, node, NODE_DATA_KEYS_BY_TYPE[node.type]);
  return {
    id: node.id,
    type: node.type,
    position: { x: LAYOUT_X_ORIGIN, y: LAYOUT_Y_ORIGIN + index * LAYOUT_Y_STEP },
    data,
  };
}

function buildEdge(edge: FlowEdge, index: number): TranspiledEdge {
  return {
    id: edge.id ?? `e${index + 1}`,
    source: edge.source,
    target: edge.target,
    sourceHandle: edge.sourceHandle ?? null,
    targetHandle: edge.targetHandle ?? null,
    label: edge.label ?? null,
  };
}

/**
 * Whitelist of fields the transpiler copies into {@code data} per node
 * type. Anything not in the list is dropped — keeps the output JSON minimal
 * and prevents authors from accidentally smuggling fields the engine
 * doesn't understand (which would silently rot at deserialize time).
 */
const NODE_DATA_KEYS_BY_TYPE: Record<NodeType, readonly string[]> = {
  start: [],
  end: [],
  aiQuestion: [
    "aiInstruction",
    "outputVariable",
    "validationRule",
    "overrideExistingValue",
    "inlineOptions",
    "onJudgeReject",
    "toolsEnabled",
    "requiredTools",
  ],
  formCapture: ["aiInstruction", "outputVariable", "validationRule", "formFields"],
  condition: ["conditionExpression"],
  functionCall: [
    "toolSource",
    "functionName",
    "mcpServerId",
    "aiInstruction",
    "outputVariable",
    "continueOnFailure",
  ],
  scheduleAgent: [
    "routineId",
    "routineName",
    "outputVariable",
    "aiInstruction",
    "routineTimeoutMs",
    "continueOnFailure",
  ],
  subFlow: ["subFlowId", "subFlowName"],
  subFlowSwitch: ["switchVariable", "switchOptions"],
  persona: ["personaId", "personaName"],
  switch: ["switchVariable", "switchOptions"],
  slot: ["slotName", "slotOperation", "slotValue", "overrideExistingValue"],
  writeSlot: ["slotName", "slotValue"],
  suspend: [],
};

function copyDefinedFields(
  target: TranspiledNodeData,
  source: FlowNode,
  keys: readonly string[],
): void {
  // FlowNode is a discriminated union; the typed indexer below is safe
  // because every key in NODE_DATA_KEYS_BY_TYPE is guaranteed by the
  // matching branch of the union.
  const indexed = source as unknown as Record<string, unknown>;
  for (const key of keys) {
    const value = indexed[key];
    if (value !== undefined && value !== null) {
      // Defensive copy of arrays so the transpiled output cannot share
      // mutable references with the author's source spec.
      target[key] = Array.isArray(value) ? [...value] : value;
    }
  }
}

/* ─────────────────────── Misc helpers ─────────────────────── */

function trimOrNull(value: string | undefined): string | null {
  if (value === undefined) return null;
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

/**
 * Deduplicate slot declarations by name (case-sensitive — backend treats
 * slot names as exact identifiers) and warn if a slot referenced by an
 * {@code outputVariable}/{@code slotName} on a node is missing. The warning
 * surfaces as an additional empty declaration so the import endpoint
 * auto-creates the slot at install time — fail-soft is friendlier than
 * blocking the build on a forgotten slot row.
 */
function dedupeSlots(
  declared: readonly SlotDeclaration[],
  nodes: readonly FlowNode[],
): SlotDeclaration[] {
  const byName = new Map<string, SlotDeclaration>();
  for (const slot of declared) {
    if (!byName.has(slot.name)) {
      byName.set(slot.name, { ...slot });
    }
  }
  for (const node of nodes) {
    const referenced = slotReferencedBy(node);
    if (referenced && !byName.has(referenced)) {
      byName.set(referenced, { name: referenced });
    }
  }
  return [...byName.values()];
}

function slotReferencedBy(node: FlowNode): string | null {
  switch (node.type) {
    case "aiQuestion":
    case "formCapture":
      return node.outputVariable ?? null;
    case "slot":
    case "writeSlot":
      return node.slotName;
    default:
      return null;
  }
}
