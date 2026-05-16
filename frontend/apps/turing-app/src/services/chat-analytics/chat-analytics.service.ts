import axios from "axios";
import type {
  TurChatAnalyticsHealth,
  TurChatAnalyticsScorecardParams,
  TurChatAnalyticsScorecardRow,
  TurChatAnalyticsSession,
  TurChatAnalyticsSessionsParams,
  TurChatAnalyticsTimeseriesParams,
  TurChatAnalyticsTimeseriesPoint,
  TurChatAnalyticsTranscript,
} from "@/models/chat-analytics/chat-analytics.model";

/**
 * Read API for chat analytics — paired with the Spring controller
 * {@code TurChatAnalyticsAPI}. Engine-agnostic on the wire (Mongo or Redis
 * decide the underlying storage based on {@code turing.logging.engine}).
 *
 * @since 2026.2.7
 */
export class TurChatAnalyticsService {
  async health(): Promise<TurChatAnalyticsHealth> {
    const response = await axios.get<TurChatAnalyticsHealth>("/system/chat-analytics/health");
    return response.data;
  }

  async sessions(params: TurChatAnalyticsSessionsParams = {}): Promise<TurChatAnalyticsSession[]> {
    const response = await axios.get<TurChatAnalyticsSession[]>("/system/chat-analytics/sessions", {
      params,
    });
    return response.data;
  }

  async session(conversationId: string): Promise<TurChatAnalyticsSession | null> {
    try {
      const response = await axios.get<TurChatAnalyticsSession>(
        `/system/chat-analytics/sessions/${encodeURIComponent(conversationId)}`,
      );
      return response.data;
    } catch (err) {
      // 404 = session was purged or never enriched into the store.
      if (axios.isAxiosError(err) && err.response?.status === 404) return null;
      throw err;
    }
  }

  async scorecard(
    params: TurChatAnalyticsScorecardParams = {},
  ): Promise<TurChatAnalyticsScorecardRow[]> {
    const response = await axios.get<TurChatAnalyticsScorecardRow[]>(
      "/system/chat-analytics/scorecard",
      { params },
    );
    return response.data ?? [];
  }

  async timeseries(
    params: TurChatAnalyticsTimeseriesParams = {},
  ): Promise<TurChatAnalyticsTimeseriesPoint[]> {
    const response = await axios.get<TurChatAnalyticsTimeseriesPoint[]>(
      "/system/chat-analytics/timeseries",
      { params },
    );
    return response.data ?? [];
  }

  async transcript(conversationId: string, limit = 200): Promise<TurChatAnalyticsTranscript | null> {
    try {
      const response = await axios.get<TurChatAnalyticsTranscript>(
        `/system/chat-analytics/sessions/${encodeURIComponent(conversationId)}/transcript`,
        { params: { limit } },
      );
      return response.data;
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 404) return null;
      throw err;
    }
  }
}
