import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurCustomTool } from '@/models/customtool/custom-tool.model.ts';
import { TurCustomToolService } from '@/services/customtool/custom-tool.service';
import { queryKeys } from './keys';

const service = new TurCustomToolService();

export function useCustomTools() {
  return useQuery<TurCustomTool[]>({
    queryKey: queryKeys.customTools.list(),
    queryFn: () => service.query(),
  });
}

function invalidateCustomTools(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.customTools.all() });
}

export function useCreateCustomTool() {
  const queryClient = useQueryClient();
  return useMutation<TurCustomTool, Error, TurCustomTool>({
    mutationFn: (tool) => service.create(tool),
    onSuccess: () => invalidateCustomTools(queryClient),
  });
}

export function useUpdateCustomTool() {
  const queryClient = useQueryClient();
  return useMutation<TurCustomTool, Error, TurCustomTool>({
    mutationFn: (tool) => service.update(tool),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.customTools.detail(saved.id), saved);
      }
      invalidateCustomTools(queryClient);
    },
  });
}

export function useDeleteCustomTool() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurCustomTool>({
    mutationFn: (tool) => service.delete(tool),
    onSuccess: (_ok, tool) => {
      if (tool?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.customTools.detail(tool.id) });
      }
      invalidateCustomTools(queryClient);
    },
  });
}
