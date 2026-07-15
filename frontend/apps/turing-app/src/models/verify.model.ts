/**
 * Result of a live "verify" probe against a configured LLM instance or
 * embedding model. Mirrors the backend `TurModelVerifyResult` record. Never
 * carries the API key or request payload.
 */
export interface TurModelVerifyResult {
  /** `true` when the model answered the minimal probe call. */
  ok: boolean;
  /** Human-readable detail (model + dimensions on success, error on failure). */
  message: string;
  /** Wall-clock milliseconds the probe round-trip took. */
  latencyMs: number;
}
