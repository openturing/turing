import type { FlowSpec } from "./types.js";

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
export function defineFlow<T extends FlowSpec>(spec: T): T {
  return spec;
}
