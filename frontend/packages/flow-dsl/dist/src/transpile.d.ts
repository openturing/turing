import type { FlowSpec, TranspiledFlow } from "./types.js";
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
export declare class FlowSpecError extends Error {
    constructor(message: string);
}
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
export declare function transpileFlow(spec: FlowSpec): TranspiledFlow;
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
export declare function transpileBundle(specs: readonly FlowSpec[]): TranspiledFlow[];
//# sourceMappingURL=transpile.d.ts.map