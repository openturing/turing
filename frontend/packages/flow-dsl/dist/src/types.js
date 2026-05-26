/**
 * T98 / §VII.11.h — typed DSL surface for Viglet Turing ES chat flows.
 *
 * <p>Every union / interface in this file mirrors a backend enum or JPA
 * column on {@code com.viglet.turing.persistence.model.agent.TurChatFlow}
 * and {@code com.viglet.turing.genai.flow.ChatFlowNode}. When the backend
 * adds a new value (e.g. a new guardrail strategy), update the union here
 * — the transpiler is purely structural so it doesn't need to know about
 * the value, but authors lose compile-time safety until the union grows.
 *
 * <p>The unions are intentionally written as string literals (not
 * {@code as const} arrays) so a typo like {@code tone: "PROFESSIONA"}
 * fails at {@code tsc} time with a clear error pointing at the bad value,
 * instead of falling through at runtime to a server-side validation error.
 */
export {};
//# sourceMappingURL=types.js.map