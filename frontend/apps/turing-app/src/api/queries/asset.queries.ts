import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { TurAssetItem } from "@/models/asset/asset-item.model";
import { TurAssetService } from "@/services/asset/asset.service";
import { queryKeys } from "./keys";

const service = new TurAssetService();

/**
 * T1018 — one folder's listing, keyed by its prefix so walking back up a tree is
 * served from cache rather than re-listed.
 */
export function useAssets(prefix: string) {
  return useQuery<TurAssetItem[]>({
    queryKey: queryKeys.assets.list(prefix),
    queryFn: () => service.query(prefix),
  });
}

/**
 * T1018 — which of the given files have been trained into the RAG index.
 *
 * Takes the names the caller is showing rather than fetching for the whole
 * store, and is disabled when there are none or when RAG is off — the two
 * guards the browser had written as early returns inside an effect. The key
 * sorts the names so two callers showing the same folder share one entry.
 */
export function useAssetTrainingRecords(names: string[], enabled: boolean) {
  return useQuery<Record<string, string>>({
    queryKey: queryKeys.assets.trainingRecords(names),
    queryFn: () => service.getTrainingRecords(names),
    enabled: enabled && names.length > 0,
  });
}

/** T1018 — invalidate every asset listing after a write that changes one. */
export function useInvalidateAssets() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.assets.all() });
}

/** T1018 — start the RAG training pass over the stored assets. */
export function useStartAssetTraining() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => service.startTraining(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.assets.all() }),
  });
}
