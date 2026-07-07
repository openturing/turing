import { useQuery } from "@tanstack/react-query";
import type { TurLlmModelList } from "@/models/llm/llm-model-option.model.ts";
import { TurLLMModelService } from "@/services/llm/llm-model.service";
import { queryKeys } from "./keys";

const service = new TurLLMModelService();

export interface UseLlmModelsParams {
  vendorId?: string;
  instanceId?: string;
  apiKey?: string;
  url?: string;
  providerOptionsJson?: string;
  /** Set false to hold the query (e.g. picker not open yet). */
  enabled?: boolean;
}

/**
 * Lists the selectable models for the chosen vendor (T577). The typed API key
 * is sent to the backend so a live vendor query can run, but it is deliberately
 * kept out of the query key — only its presence (`hasKey`) participates, so the
 * cache never stores the secret.
 */
export function useLlmModels(params: UseLlmModelsParams) {
  const { vendorId, instanceId, apiKey, url, providerOptionsJson, enabled = true } = params;
  const hasKey = Boolean(apiKey?.trim());
  return useQuery<TurLlmModelList>({
    queryKey: queryKeys.llmModels.list({
      vendorId: vendorId ?? "",
      instanceId,
      url,
      hasKey,
    }),
    queryFn: () =>
      service.listModels({
        vendorId: vendorId as string,
        instanceId,
        apiKey: apiKey?.trim() || undefined,
        url: url?.trim() || undefined,
        providerOptionsJson,
      }),
    enabled: enabled && Boolean(vendorId),
    // Models change rarely; keep them cached longer than the default 30s.
    staleTime: 5 * 60 * 1000,
  });
}
