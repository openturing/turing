/**
 * Stable per-visitor id for cross-conversation personal memory (T446). The
 * server keys `TurUserMemory` on (userId, agentId, key); the host owns the
 * stable userId. For the showcase we mint an anonymous opaque id once and keep
 * it in localStorage — a real deployment would use the authenticated user id.
 */
const KEY = "atlas-store-visitor-id";

export function getVisitorId(): string {
  try {
    let id = localStorage.getItem(KEY);
    if (!id) {
      id = `visitor-${crypto.randomUUID()}`;
      localStorage.setItem(KEY, id);
    }
    return id;
  } catch {
    return "visitor-anonymous";
  }
}
