/**
 * @viglet/turing-flow-dsl — typed DSL for Viglet Turing ES chat flows.
 *
 * <p>Authors write `flows/*.flow.ts` with full IDE inference, then either
 * call {@link transpileFlow} programmatically from their own build script
 * or run the bundled CLI ({@code turing-flow-dsl build}) to emit
 * editor-compatible JSON files for the {@code POST /chat-flow/import}
 * endpoint.
 *
 * <p>T98 / §VII.11.h. Catches typos like {@code triggerMode: "ONCEE"} at
 * {@code tsc} time rather than letting them through to server-side
 * validation.
 *
 * @since 2026.3.1
 */
export { defineBundle, defineFlow } from "./define-flow.js";
export { FlowSpecError, transpileBundle, transpileFlow } from "./transpile.js";
//# sourceMappingURL=index.js.map