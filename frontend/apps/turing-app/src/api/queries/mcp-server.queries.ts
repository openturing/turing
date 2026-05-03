import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurMcpServer } from '@/models/mcp/mcp-server.model.ts';
import { TurMcpServerService } from '@/services/mcp/mcp-server.service';
import { queryKeys } from './keys';

const service = new TurMcpServerService();

export function useMcpServers() {
  return useQuery<TurMcpServer[]>({
    queryKey: queryKeys.mcpServers.list(),
    queryFn: () => service.query(),
  });
}

function invalidateMcpServers(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.mcpServers.all() });
}

export function useCreateMcpServer() {
  const queryClient = useQueryClient();
  return useMutation<TurMcpServer, Error, TurMcpServer>({
    mutationFn: (server) => service.create(server),
    onSuccess: () => invalidateMcpServers(queryClient),
  });
}

export function useUpdateMcpServer() {
  const queryClient = useQueryClient();
  return useMutation<TurMcpServer, Error, TurMcpServer>({
    mutationFn: (server) => service.update(server),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.mcpServers.detail(saved.id), saved);
      }
      invalidateMcpServers(queryClient);
    },
  });
}

export function useDeleteMcpServer() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurMcpServer>({
    mutationFn: (server) => service.delete(server),
    onSuccess: (_ok, server) => {
      if (server?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.mcpServers.detail(server.id) });
      }
      invalidateMcpServers(queryClient);
    },
  });
}
