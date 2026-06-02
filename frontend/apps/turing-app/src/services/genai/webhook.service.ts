import axios from "axios";
import type { TurChatWebhook } from "@/models/genai/webhook.model";

/**
 * Webhook catalog client (T62). Webhooks are deployment-wide: one
 * "push-to-salesforce" webhook serves every flow. Backed by
 * {@code /api/genai/webhook} CRUD endpoints.
 *
 * @since 2026.3.1
 */
export class TurChatWebhookService {
  async query(): Promise<TurChatWebhook[]> {
    const response = await axios.get<TurChatWebhook[]>("/genai/webhook");
    return response.data;
  }

  async get(id: string): Promise<TurChatWebhook> {
    const response = await axios.get<TurChatWebhook>(`/genai/webhook/${id}`);
    return response.data;
  }

  async create(webhook: TurChatWebhook): Promise<TurChatWebhook> {
    const response = await axios.post<TurChatWebhook>("/genai/webhook", webhook);
    return response.data;
  }

  async update(webhook: TurChatWebhook): Promise<TurChatWebhook> {
    const response = await axios.put<TurChatWebhook>(
      `/genai/webhook/${webhook.id}`,
      webhook,
    );
    return response.data;
  }

  async delete(webhook: TurChatWebhook): Promise<boolean> {
    const response = await axios.delete(`/genai/webhook/${webhook.id}`);
    return response.status === 200;
  }
}
