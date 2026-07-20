import axios from "axios";
import type {
  TurGatewayCreatedKey,
  TurGatewayKeyUpsert,
  TurGatewayKeyUsageRow,
  TurGatewayKeyView,
} from "@/models/gateway/gateway.model.ts";

/**
 * T748 / §XLIX — REST client for the Governed LLM Gateway admin surface
 * (`/api/gateway`, ROLE_ADMIN). CRUD over virtual keys + per-key spend.
 */
export class TurGatewayService {
  async listKeys(): Promise<TurGatewayKeyView[]> {
    const response = await axios.get<TurGatewayKeyView[]>("/api/gateway/keys");
    return response.data;
  }

  async createKey(request: TurGatewayKeyUpsert): Promise<TurGatewayCreatedKey> {
    const response = await axios.post<TurGatewayCreatedKey>("/api/gateway/keys", request);
    return response.data;
  }

  async updateKey(id: string, request: TurGatewayKeyUpsert): Promise<TurGatewayKeyView> {
    const response = await axios.put<TurGatewayKeyView>(`/api/gateway/keys/${id}`, request);
    return response.data;
  }

  async rotateKey(id: string): Promise<TurGatewayCreatedKey> {
    const response = await axios.post<TurGatewayCreatedKey>(`/api/gateway/keys/${id}/rotate`);
    return response.data;
  }

  async deleteKey(id: string): Promise<boolean> {
    const response = await axios.delete(`/api/gateway/keys/${id}`);
    return response.status === 200;
  }

  async usage(): Promise<TurGatewayKeyUsageRow[]> {
    const response = await axios.get<TurGatewayKeyUsageRow[]>("/api/gateway/usage");
    return response.data;
  }
}
