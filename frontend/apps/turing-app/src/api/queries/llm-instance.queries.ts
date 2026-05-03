import { useQuery } from '@tanstack/react-query';
import type { TurLLMInstance } from '@/models/llm/llm-instance.model.ts';
import { TurLLMInstanceService } from '@/services/llm/llm.service';
import { queryKeys } from './keys';

const service = new TurLLMInstanceService();

export function useLlmInstances() {
  return useQuery<TurLLMInstance[]>({
    queryKey: queryKeys.llmInstances.list(),
    queryFn: () => service.query(),
  });
}
