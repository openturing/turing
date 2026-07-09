import { useQuery } from "@tanstack/react-query";
import type {
  TurEmbeddingDimensionCheck,
  TurHuggingFaceModelList,
} from "@/models/embedding/huggingface-model.model.ts";
import type { TurLlmModelList } from "@/models/llm/llm-model-option.model.ts";
import { TurEmbeddingModelDiscoveryService } from "@/services/embedding/embedding-model-discovery.service";
import { queryKeys } from "./keys";

const service = new TurEmbeddingModelDiscoveryService();

/**
 * Lists ONNX-verified HuggingFace embedding models (T624), server-side searched
 * by `query`. Kept cached longer than the default since the catalog + verified
 * live list change rarely.
 */
export function useHuggingFaceModels(query: string, enabled = true) {
  return useQuery<TurHuggingFaceModelList>({
    queryKey: queryKeys.huggingFaceModels.list(query ?? ""),
    queryFn: () => service.listHuggingFaceModels(query?.trim() || undefined),
    enabled,
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * Probes a picked HuggingFace model's embedding dimension and compares it with
 * the current default (T627). Enabled only once a repo id is chosen.
 */
export function useHuggingFaceDimensionCheck(repoId: string | undefined, enabled = true) {
  return useQuery<TurEmbeddingDimensionCheck>({
    queryKey: queryKeys.huggingFaceModels.dimensionCheck(repoId ?? ""),
    queryFn: () => service.checkHuggingFaceDimension(repoId as string),
    enabled: enabled && Boolean(repoId?.trim()),
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * Lists the ONNX artifact variants a picked HuggingFace repo ships (T628), so
 * the picker can offer a size/latency-vs-accuracy choice. Enabled only once a
 * repo id is chosen.
 */
export function useHuggingFaceVariants(repoId: string | undefined, enabled = true) {
  return useQuery<string[]>({
    queryKey: queryKeys.huggingFaceModels.variants(repoId ?? ""),
    queryFn: () => service.listHuggingFaceVariants(repoId as string),
    enabled: enabled && Boolean(repoId?.trim()),
    staleTime: 5 * 60 * 1000,
  });
}

export interface UseLlmEmbeddingModelsParams {
  vendorId?: string;
  instanceId?: string;
  apiKey?: string;
  url?: string;
  providerOptionsJson?: string;
  /** Set false to hold the query (e.g. picker not open yet). */
  enabled?: boolean;
}

/**
 * Lists embedding-capable models for the chosen LLM vendor (T625). Mirrors
 * `useLlmModels` (T577): the key never stores the secret — only its presence
 * (`hasKey`) participates.
 */
export function useLlmEmbeddingModels(params: UseLlmEmbeddingModelsParams) {
  const { vendorId, instanceId, apiKey, url, providerOptionsJson, enabled = true } = params;
  const hasKey = Boolean(apiKey?.trim());
  return useQuery<TurLlmModelList>({
    queryKey: queryKeys.llmEmbeddingModels.list({
      vendorId: vendorId ?? "",
      instanceId,
      url,
      hasKey,
    }),
    queryFn: () =>
      service.listLlmEmbeddingModels({
        vendorId: vendorId as string,
        instanceId,
        apiKey: apiKey?.trim() || undefined,
        url: url?.trim() || undefined,
        providerOptionsJson,
      }),
    enabled: enabled && Boolean(vendorId),
    staleTime: 5 * 60 * 1000,
  });
}
