/**
 * A reusable async job that a {@code scheduleAgent} chat-flow node fires
 * via JMS. Mirrors the {@code TurRoutineDto} record on the backend.
 *
 * @since 2026.3.1
 */
export type TurRoutineKind = "NATIVE" | "GROOVY";

export interface TurRoutine {
  id?: string;
  name: string;
  description?: string;
  kind: TurRoutineKind;
  /**
   * Name of the {@code @Tool} method invoked when {@link kind} is
   * {@code NATIVE}. Resolved through the same registry the synchronous
   * {@code functionCall} node uses.
   */
  nativeToolName?: string;
  /**
   * Groovy script body executed when {@link kind} is {@code GROOVY}. The
   * last expression of the script is written to the scheduleAgent node's
   * outputVariable. Ignored for {@code NATIVE} routines.
   */
  groovyScript?: string;
  /**
   * Per-routine timeout fallback (ms). A {@code scheduleAgent} node may
   * override per-step via its own {@code routineTimeoutMs}.
   */
  defaultTimeoutMs?: number;
  enabled?: boolean;
}
