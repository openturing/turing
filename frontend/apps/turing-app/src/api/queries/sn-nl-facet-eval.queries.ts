import { useMutation, useQuery } from '@tanstack/react-query';
import type {
  TurNLFacetEvalPack,
  TurNLFacetEvalReport,
} from '@/models/sn/sn-nl-facet-eval.model.ts';
import { TurSNNLFacetEvalService } from '@/services/sn/sn.nl-facet-eval.service';

const service = new TurSNNLFacetEvalService();

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
