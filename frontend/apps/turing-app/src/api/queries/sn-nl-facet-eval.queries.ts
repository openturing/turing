import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurEvalDataset } from '@/models/eval/eval-studio.model';
import type {
  TurNLFacetEvalPack,
  TurNLFacetEvalReport,
} from '@/models/sn/sn-nl-facet-eval.model.ts';
import { TurSNNLFacetEvalService } from '@/services/sn/sn.nl-facet-eval.service';

const service = new TurSNNLFacetEvalService();

const NL_FACET_DATASETS_KEY = ['sn-nl-facet-eval', 'datasets'] as const;

/** T385 — whether a usable default LLM is configured (the eval can run). */
export function useNLFacetEvalAvailable(enabled = true) {
  return useQuery<boolean>({
    queryKey: ['sn-nl-facet-eval', 'available'],
    queryFn: () => service.available(),
    enabled,
  });
}

/** T385 — run an NL→facet eval pack and return the pass/fail report. */
export function useRunNLFacetEval() {
  return useMutation<TurNLFacetEvalReport, Error, TurNLFacetEvalPack>({
    mutationFn: (pack) => service.run(pack),
  });
}

/** T601 — the saved NL→facet eval datasets (folded into the Eval Studio). */
export function useNLFacetDatasets(enabled = true) {
  return useQuery<TurEvalDataset[]>({
    queryKey: NL_FACET_DATASETS_KEY,
    queryFn: () => service.listDatasets(),
    enabled,
  });
}

/** T601 — import an eval pack as a reusable dataset; refreshes the dataset list. */
export function useImportNLFacetDataset() {
  const queryClient = useQueryClient();
  return useMutation<TurEvalDataset, Error, TurNLFacetEvalPack>({
    mutationFn: (pack) => service.importDataset(pack),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NL_FACET_DATASETS_KEY });
    },
  });
}

/** T601 — run a saved NL→facet dataset and return the pass/fail report. */
export function useRunNLFacetDataset() {
  return useMutation<TurNLFacetEvalReport, Error, string>({
    mutationFn: (id) => service.runDataset(id),
  });
}

/** T601 — delete a saved NL→facet dataset; refreshes the dataset list. */
export function useDeleteNLFacetDataset() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, string>({
    mutationFn: (id) => service.deleteDataset(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NL_FACET_DATASETS_KEY });
    },
  });
}
