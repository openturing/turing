import { useEffect, useRef, useState } from "react";

interface UseSmoothedEtaOptions {
  /**
   * EMA smoothing factor in (0,1]. Higher = more responsive, lower = smoother.
   * Default 0.15 picks up trend changes within a few samples without
   * jittering on per-doc speed variance.
   */
  alpha?: number;
  /**
   * Minimum interval (ms) between display-state updates. The internal
   * smoothed value updates on every input change, but the rendered value
   * only refreshes after this throttle so React doesn't re-render on every
   * SSE poll. Default 2000.
   */
  minIntervalMs?: number;
  /**
   * Optional re-trigger token: when this value changes the EMA is recomputed
   * even if {@code rawMs} happens to equal the previous sample. Useful when
   * the same numeric ETA is reported across multiple progress events but
   * each one represents real forward progress.
   */
  tick?: unknown;
}

/**
 * Smooths a noisy {@code estimatedRemainingMillis} signal using exponential
 * moving average so the displayed countdown stays steady even when
 * individual units of work happen faster or slower than average. Returns
 * {@code -1} when there is no estimate yet (caller renders a placeholder /
 * hides the line).
 *
 * <p>Used by the export, import and RAG reindex progress UIs — same logic
 * the SSE poller would otherwise duplicate.</p>
 *
 * @since 2026.2.4
 */
export function useSmoothedEta(
  rawMs: number | null | undefined,
  options?: UseSmoothedEtaOptions,
): number {
  const alpha = options?.alpha ?? 0.15;
  const minIntervalMs = options?.minIntervalMs ?? 2000;
  const smoothedRef = useRef<number | null>(null);
  const lastUpdateRef = useRef(0);
  const [display, setDisplay] = useState<number>(-1);

  useEffect(() => {
    if (rawMs == null || rawMs <= 0) {
      smoothedRef.current = null;
      lastUpdateRef.current = 0;
      setDisplay(-1);
      return;
    }
    if (smoothedRef.current === null) {
      smoothedRef.current = rawMs;
      lastUpdateRef.current = Date.now();
      setDisplay(rawMs);
      return;
    }
    smoothedRef.current = alpha * rawMs + (1 - alpha) * smoothedRef.current;
    const now = Date.now();
    if (now - lastUpdateRef.current >= minIntervalMs) {
      lastUpdateRef.current = now;
      setDisplay(smoothedRef.current);
    }
  }, [rawMs, options?.tick, alpha, minIntervalMs]);

  return display;
}
