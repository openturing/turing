import { useQuery } from '@tanstack/react-query';
import { fetchChatEnabled, type ChatEnabledResponse } from '@viglet/turing-react-sdk';
import { queryKeys } from './keys';

/**
 * T853 / §LXIV.2 (Block BQ) — the backend answer-readiness probe,
 * `GET /api/sn/{site}/chat/enabled`. Its `diagnoseAgentReadiness` walk is the
 * only gate that knows a `VECTORLESS_STRUCTURED` site answers through the
 * catalog copilot (no embedding model, no vector store), so the console must ask
 * it rather than re-derive readiness from the site's agent flags.
 *
 * <p>The `reason` it returns is the point: a site that cannot answer gets a
 * disabled tile naming the condition to fix instead of a tile that silently
 * disappears.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function useSnChatEnabled(siteName: string | undefined) {
  return useQuery<ChatEnabledResponse>({
    queryKey: siteName
      ? queryKeys.snChatEnabled.bySite(siteName)
      : ['sn-chat-enabled', 'pending'],
    queryFn: () => fetchChatEnabled(siteName as string),
    enabled: Boolean(siteName),
    staleTime: 60_000,
  });
}
