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
//# sourceMappingURL=transpile.d.ts.map