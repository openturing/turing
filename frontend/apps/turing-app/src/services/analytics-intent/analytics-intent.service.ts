import axios from "axios";
import type {
  TurAnalyticsIntent,
  ReindexAnalyticsIntentResponse,
} from "@/models/analytics-intent/analytics-intent.model";

/**
 * Agent-scoped analytics intent catalog service. Mirrors the backend
 * routes at {@code /api/ai-agent/{agentId}/analytics-intent}; the
 * reindex endpoint is the Phase B bootstrap path that flushes the
 * catalog into the SE-side index used by {@code TurSeMltIntentClassifier}.
 */
export class TurAnalyticsIntentService {
  async query(agentId: string): Promise<TurAnalyticsIntent[]> {
    const response = await axios.get<TurAnalyticsIntent[]>(
      `/ai-agent/${agentId}/analytics-intent`,
    );
    return response.data;
  }

  async get(agentId: string, id: string): Promise<TurAnalyticsIntent> {
    const response = await axios.get<TurAnalyticsIntent>(
      `/ai-agent/${agentId}/analytics-intent/${id}`,
    );
    return response.data;
  }

  async create(agentId: string, intent: TurAnalyticsIntent): Promise<TurAnalyticsIntent> {
    const response = await axios.post<TurAnalyticsIntent>(
      `/ai-agent/${agentId}/analytics-intent`,
      intent,
    );
    return response.data;
  }

  async update(agentId: string, intent: TurAnalyticsIntent): Promise<TurAnalyticsIntent> {
    const response = await axios.put<TurAnalyticsIntent>(
      `/ai-agent/${agentId}/analytics-intent/${intent.id}`,
      intent,
    );
    return response.data;
  }

  async delete(agentId: string, intent: TurAnalyticsIntent): Promise<boolean> {
    const response = await axios.delete(
      `/ai-agent/${agentId}/analytics-intent/${intent.id}`,
    );
    return response.status === 204 || response.status === 200;
  }

  async reindex(agentId: string): Promise<ReindexAnalyticsIntentResponse> {
    const response = await axios.post<ReindexAnalyticsIntentResponse>(
      `/ai-agent/${agentId}/analytics-intent/reindex`,
    );
    return response.data;
  }
}
