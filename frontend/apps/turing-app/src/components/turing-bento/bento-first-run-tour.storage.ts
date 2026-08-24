/**
 * T1024 — where the first-run tour remembers it has run.
 *
 * Split from the tour component: these three touch `localStorage` and render
 * nothing, and a module exporting both a component and a helper reloads whole
 * instead of preserving state. Every read is defensive, because private-mode or
 * disabled storage must never break the shell — an unavailable store reads as
 * "seen", so the tour cannot nag in a loop.
 */

/**
 * `v2` (T958) — the tour gained the "turn AI on" step, so existing installs see
 * it once more.
 */
export const BENTO_TOUR_SEEN_KEY = "turing.bento.tour.v2";

export function hasSeenBentoTour(): boolean {
  try {
    return globalThis.localStorage?.getItem(BENTO_TOUR_SEEN_KEY) === "1";
  } catch {
    return true;
  }
}

export function markBentoTourSeen() {
  try {
    globalThis.localStorage?.setItem(BENTO_TOUR_SEEN_KEY, "1");
  } catch {
    /* nothing to persist to; the in-memory dismissal still holds this session. */
  }
}
