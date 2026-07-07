/**
 * Tiny pub/sub bridge so any component can open the "Ask Atlas" copilot and
 * pre-send a question — used by the glass-box "Why #1?" affordance (T444) and
 * the ambient proactive copilot (T445) to hand a prompt to the chat panel
 * without prop-drilling through the storefront tree.
 */
type Listener = (question: string) => void;

const listeners = new Set<Listener>();

/** Open the chat and send `question`. No-op until the launcher subscribes. */
export function askAtlas(question: string): void {
  for (const l of listeners) l(question);
}

/** Subscribe the chat launcher; returns an unsubscribe function. */
export function onAskAtlas(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
