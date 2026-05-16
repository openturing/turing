import { useQuery } from '@tanstack/react-query';
import type {
  TurChatAnalyticsHealth,
  TurChatAnalyticsSession,
  TurChatAnalyticsSessionsParams,
  TurChatAnalyticsTimeseriesParams,
  TurChatAnalyticsTimeseriesPoint,
  TurChatAnalyticsTranscript,
} from '@/models/chat-analytics/chat-analytics.model';
import { TurChatAnalyticsService } from '@/services/chat-analytics/chat-analytics.service';
import { queryKeys } from './keys';

const service = new TurChatAnalyticsService();

export function useChatAnalyticsHealth() {
  return useQuery<TurChatAnalyticsHealth>({
    queryKey: queryKeys.chatAnalytics.health(),
    queryFn: () => service.health(),
  });
}

export function useChatAnalyticsSessions(params: TurChatAnalyticsSessionsParams = {}) {
  return useQuery<TurChatAnalyticsSession[]>({
    // Stringify params into the key so changing filters refetches; the inner
    // factory wraps the raw params object in the key array deterministically.
    queryKey: queryKeys.chatAnalytics.sessions(params as Record<string, unknown>),
    queryFn: () => service.sessions(params),
  });
}

export function useChatAnalyticsTimeseries(params: TurChatAnalyticsTimeseriesParams = {}) {
  return useQuery<TurChatAnalyticsTimeseriesPoint[]>({
    queryKey: queryKeys.chatAnalytics.timeseries(params as Record<string, unknown>),
    queryFn: () => service.timeseries(params),
  });
}

export function useChatAnalyticsTranscript(conversationId: string | null) {
  return useQuery<TurChatAnalyticsTranscript | null>({
    queryKey: conversationId
      ? queryKeys.chatAnalytics.transcript(conversationId)
      : ['chat-analytics', 'transcript', 'none'],
    queryFn: () => service.transcript(conversationId as string),
    enabled: Boolean(conversationId),
  });
}
