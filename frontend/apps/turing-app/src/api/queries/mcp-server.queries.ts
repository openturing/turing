import { useQuery } from '@tanstack/react-query';
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
