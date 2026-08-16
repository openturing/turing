import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useMemo } from "react";

import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import { queryKeys } from "@/api/queries/keys";
import type { TurGlobalSettings } from "@/models/system/global-settings.model";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";

const globalSettingsService = new TurGlobalSettingsService();

/** The global settings row, cached like every other read path (T958). */
export function useGlobalSettings() {
  return useQuery<TurGlobalSettings>({
    queryKey: queryKeys.globalSettings.current(),
    queryFn: () => globalSettingsService.query(),
  });
}

/**
 * T958 / §XIV.10.3 — the one answer every AI surface needs before it renders:
 * can this install talk to a model at all?
 *
 * <p>Two facts, deliberately kept apart. `hasEnabledLlm` is what actually makes
 * chat, agents, skills and semantic-navigation answers work, so it is what those
 * surfaces gate on. `hasDefaultLlm` is what the home activation card watches —
 * an install with instances but no default has made a choice the card should not
 * second-guess forever, but one with neither has not started.
 *
 * <p>`isResolved` guards the flash: until both reads land, a surface must render
 * neither its content nor the "turn AI on" state, or a working install blinks
 * the empty state on every navigation.
 */
export function useAiActivation() {
  const { data: llmInstances, isSuccess: llmLoaded } = useLlmInstances();
  const { data: settings, isSuccess: settingsLoaded } = useGlobalSettings();

  return useMemo(() => {
    const hasEnabledLlm = (llmInstances ?? []).some((instance) => instance.enabled === 1);
    const hasDefaultLlm = Boolean(settings?.defaultLlmId?.trim());
    return {
      hasEnabledLlm,
      hasDefaultLlm,
      /** No model at all — the home card offers the two ways to get one. */
      needsActivation: llmLoaded && settingsLoaded && !hasEnabledLlm && !hasDefaultLlm,
      isResolved: llmLoaded && settingsLoaded,
    };
  }, [llmInstances, settings, llmLoaded, settingsLoaded]);
}

/**
 * Invalidates everything a completed pairing changes — the instance list, the
 * global settings (the default LLM it may have set) and the feature flags the
 * AI surfaces read — so the ones that were showing "turn AI on" re-render as
 * working, without a reload.
 *
 * <p>Memoized on purpose: the pairing dialog's poll loop lives in an effect that
 * depends on this, and an identity that changed every render would tear the loop
 * down and restart it on each state update — resetting the code the operator is
 * reading out.
 */
export function useRefreshAfterActivation() {
  const queryClient = useQueryClient();
  return useCallback(() => {
    queryClient.invalidateQueries({ queryKey: queryKeys.llmInstances.all() });
    queryClient.invalidateQueries({ queryKey: queryKeys.globalSettings.all() });
    queryClient.invalidateQueries({ queryKey: queryKeys.features.all() });
  }, [queryClient]);
}
