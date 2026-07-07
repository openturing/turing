import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/api/queries/keys";
import { TurEvalStudioService } from "@/services/eval/eval-studio.service";

/**
 * T599 / Block AJ — read hooks for the Eval Studio's agent-decoupled assets.
 *
 * @since 2026.3.4
 */
const service = new TurEvalStudioService();

export function useEvalDatasets() {
  return useQuery({
    queryKey: queryKeys.evalStudio.datasets(),
    queryFn: () => service.listDatasets(),
  });
}

export function useEvalGraderStacks() {
  return useQuery({
    queryKey: queryKeys.evalStudio.graderStacks(),
    queryFn: () => service.listGraderStacks(),
  });
}
