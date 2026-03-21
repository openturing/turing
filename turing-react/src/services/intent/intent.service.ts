import axios from "axios";
import type { TurIntent } from "@/models/intent/intent.model";

export class TurIntentService {
  async query(): Promise<TurIntent[]> {
    const response = await axios.get<TurIntent[]>("/intent");
    return response.data;
  }
  async queryEnabled(): Promise<TurIntent[]> {
    const response = await axios.get<TurIntent[]>("/intent/enabled");
    return response.data;
  }
  async get(id: string): Promise<TurIntent> {
    const response = await axios.get<TurIntent>(`/intent/${id}`);
    return response.data;
  }
  async create(intent: TurIntent): Promise<TurIntent> {
    const response = await axios.post<TurIntent>("/intent", intent);
    return response.data;
  }
  async update(intent: TurIntent): Promise<TurIntent> {
    const response = await axios.put<TurIntent>(
      `/intent/${intent.id}`,
      intent
    );
    return response.data;
  }
  async delete(intent: TurIntent): Promise<boolean> {
    const response = await axios.delete(`/intent/${intent.id}`);
    return response.status === 200;
  }
}
