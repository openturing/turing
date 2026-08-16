/**
 * T959 / §XIV.10.4 — the client half of the quota refusal.
 *
 * The backend answers an over-limit request with 402 (a resource count) or 429
 * (the monthly token budget) and an RFC 7807 body typed
 * `tag:viglet.com,2026:errors/quota-exhausted`, carrying what ran out, when it
 * resets and — when the install shares a secret with the managed service — a
 * signed upgrade target.
 *
 * That refusal arrives wherever the request happened to be made, which is the
 * one place the operator is guaranteed to be looking and the last place a
 * per-call `catch` would reliably render it. So it is published here instead:
 * the axios interceptor reports it once, and the shell's banner subscribes.
 *
 * Deliberately a plain module store rather than React Query state or a context —
 * the reporter is an axios interceptor with no component tree above it.
 */

/** The type URI the backend stamps on a quota refusal. */
export const QUOTA_EXHAUSTED_TYPE = "tag:viglet.com,2026:errors/quota-exhausted";

export interface QuotaRefusal {
  /** HTTP status the refusal arrived with: 402 (resource count) or 429 (tokens). */
  status: number;
  /** Which limit was reached: `agents`, `sites`, `monthlyTokens`. */
  resource: string;
  used: number;
  limit: number;
  /** ISO-8601 instant the window rolls over, or null for a standing limit. */
  resetsAt: string | null;
  /** The signed upgrade target, or null when this install cannot sign one. */
  upgradeUrl: string | null;
  /** The server's own sentence, shown when the console has no better wording. */
  detail: string | null;
}

type Listener = (refusal: QuotaRefusal | null) => void;

let current: QuotaRefusal | null = null;
const listeners = new Set<Listener>();

function emit(): void {
  for (const listener of listeners) listener(current);
}

/**
 * Reads a quota refusal out of an arbitrary error response body. Returns null
 * for anything that isn't one — a 402/429 from some other layer (a proxy, a
 * provider passing its own rate limit through) must not be dressed up as a plan
 * limit, so the `type` URI is the gate, not the status code.
 */
export function parseQuotaRefusal(status: number, body: unknown): QuotaRefusal | null {
  if (status !== 402 && status !== 429) return null;
  if (typeof body !== "object" || body === null) return null;
  const problem = body as Record<string, unknown>;
  if (problem.type !== QUOTA_EXHAUSTED_TYPE) return null;
  return {
    status,
    resource: typeof problem.resource === "string" ? problem.resource : "",
    used: typeof problem.used === "number" ? problem.used : 0,
    limit: typeof problem.limit === "number" ? problem.limit : 0,
    resetsAt: typeof problem.resetsAt === "string" ? problem.resetsAt : null,
    upgradeUrl: typeof problem.upgradeUrl === "string" ? problem.upgradeUrl : null,
    detail: typeof problem.detail === "string" ? problem.detail : null,
  };
}

/** Publishes a refusal to the shell. A later refusal replaces an earlier one. */
export function reportQuotaRefusal(refusal: QuotaRefusal): void {
  current = refusal;
  emit();
}

/** Dismisses the banner. The next refusal raises it again. */
export function clearQuotaRefusal(): void {
  if (current === null) return;
  current = null;
  emit();
}

export function getQuotaRefusal(): QuotaRefusal | null {
  return current;
}

export function subscribeQuotaRefusal(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
