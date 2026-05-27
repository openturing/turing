/**
 * Identity helper used purely for IDE inference: returns the spec verbatim,
 * but ensures the literal-typed shape (e.g. {@code triggerMode: "ONCE"})
 * is preserved through the type system so a downstream typo like
 * {@code triggerMode: "ONCEE"} fails at compile time.
 *
 * <p>Authors write:
 * <pre>{@code
 *   import { defineFlow } from "@viglet/turing-flow-dsl";
 *   export default defineFlow({
 *     name: "Lead capture",
 *     triggerMode: "ONCE",       // ✅ valid
 *     // triggerMode: "ONCEE",   // ❌ compile error
 *     nodes: [...],
 *     edges: [...],
 *   });
 * }</pre>
 *
 * <p>No runtime validation happens here — that's {@link transpileFlow}'s
 * job. Keeping {@code defineFlow} a pure identity lets you compose specs
 * cheaply (extend, override, snapshot for diffing) without surprise side
 * effects.
 *
 * @since 2026.3.1
 */
export function defineFlow(spec) {
    return spec;
}
/**
 * Identity helper for a multi-flow bundle (a main flow plus the sub-flows
 * it descends into). Same inference trick as {@link defineFlow}, but for an
 * array — preserves each element's literal-typed shape so node/enum typos
 * across the whole bundle fail at {@code tsc} time. Feed the result to
 * {@code transpileBundle} to emit the import-bundle JSON array.
 *
 * <pre>{@code
 *   export const flows = defineBundle([
 *     defineFlow({ id: "main", name: "Main", nodes: [...], edges: [...] }),
 *     defineFlow({ id: "sub",  name: "Sub",  nodes: [...], edges: [...] }),
 *   ]);
 * }</pre>
 *
 * @since 2026.3.1
 */
export function defineBundle(specs) {
    return specs;
}
//# sourceMappingURL=define-flow.js.map