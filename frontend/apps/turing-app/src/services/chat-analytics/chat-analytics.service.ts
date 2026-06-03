import axios from "axios";
import type {
  TurChatAnalyticsHealth,
  TurChatAnalyticsScorecardParams,
  TurChatAnalyticsScorecardRow,
  TurChatAnalyticsSession,
  TurChatAnalyticsSessionsParams,
  TurChatAnalyticsTimeseriesParams,
  TurChatAnalyticsTimeseriesPoint,
  TurChatAnalyticsRouterDecision,
  TurChatAnalyticsToolLatencyParams,
  TurChatAnalyticsToolLatencyRow,
  TurChatAnalyticsTranscript,
  TurChatSlotSseChannels,
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

  async toolLatency(
    params: TurChatAnalyticsToolLatencyParams = {},
  ): Promise<TurChatAnalyticsToolLatencyRow[]> {
    const response = await axios.get<TurChatAnalyticsToolLatencyRow[]>(
      "/system/chat-analytics/tool-latency",
      { params },
    );
    return response.data ?? [];
  }

  /**
   * Recent chat-flow router decisions for one conversation (T89), newest
   * first. Reads the in-memory ring on the server — returns an empty array
   * when the conversation never routed or its decisions were evicted.
   */
  async routerDecisions(
    conversationId: string,
    limit = 50,
  ): Promise<TurChatAnalyticsRouterDecision[]> {
    const response = await axios.get<TurChatAnalyticsRouterDecision[]>(
      "/system/chat-analytics/router-decisions",
      { params: { conversationId, limit } },
    );
    return response.data ?? [];
  }

  /**
   * Live snapshot of the open slot-stream SSE channels on the node (T90) —
   * open channels, total connections, and per-channel refcounts. Server-side
   * counterpart to the vanilla SDK's {@code _slotsSseOpenChannelCount()}.
   */
  async slotSseChannels(): Promise<TurChatSlotSseChannels> {
    const response = await axios.get<TurChatSlotSseChannels>(
      "/system/chat-analytics/slot-sse-channels",
    );
    return response.data;
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
