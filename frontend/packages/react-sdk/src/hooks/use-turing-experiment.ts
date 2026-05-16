import { useTuringFlowState, type UseTuringFlowStateOptions } from "./use-turing-flow-state";

export interface UseTuringExperimentReturn {
  /** Experiment id the conversation is in, or {@code null} when not part of any A/B test. */
  readonly experimentKey: string | null;
  /** Variant label the conversation was assigned to ({@code "control"}, {@code "treatment_v2"}, …). */
  readonly variantLabel: string | null;
  /** True while the underlying flow-state read is in flight. */
  readonly isLoading: boolean;
  /** Force a re-read of the state (rarely needed — variant assignment is sticky). */
  readonly refresh: () => void;
}

/**
 * Reads the A/B experiment context for the current conversation. Thin
 * convenience wrapper on top of {@link useTuringFlowState} that exposes
 * only the experiment fields — useful when an app wants variant-aware
 * UI without dragging the whole flow-state shape through props.
 *
 * @example
 * ```tsx
 * const { variantLabel } = useTuringExperiment();
 * return variantLabel === "lucas-alumni"
 *   ? <PeerToneBanner />
 *   : <ConsultantToneBanner />;
 * ```
 *
 * @since 2026.2.7
 */
export function useTuringExperiment(
  options: UseTuringFlowStateOptions = {},
): UseTuringExperimentReturn {
  const { state, isLoading, refresh } = useTuringFlowState(options);
  return {
    experimentKey: state?.experimentKey ?? null,
    variantLabel: state?.variantLabel ?? null,
    isLoading,
    refresh,
  };
}
