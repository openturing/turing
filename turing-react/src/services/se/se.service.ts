import axios from "axios";
import type { TurSEInstance } from "@/models/se/se-instance.model.ts";

export class TurSEInstanceService {
  async query(): Promise<TurSEInstance[]> {
    const response = await axios.get<TurSEInstance[]>("/se");
    return response.data;
  }
  async get(id: string): Promise<TurSEInstance> {
    const response = await axios.get<TurSEInstance>(`/se/${id}`);
    return response.data;
  }
  async create(turSEInstance: TurSEInstance): Promise<TurSEInstance> {
    const response = await axios.post<TurSEInstance>("/se",
      turSEInstance
    );
    return response.data;
  }
  async update(turSEInstance: TurSEInstance): Promise<TurSEInstance> {
    const response = await axios.put<TurSEInstance>(
      `/se/${turSEInstance.id.toString()}`,
      turSEInstance
    );
    return response.data;
  }
  async delete(turSEInstance: TurSEInstance): Promise<boolean> {
    const response = await axios.delete<TurSEInstance>(
      `/se/${turSEInstance.id.toString()}`
    );
    return  response.status == 200;
  }
  async getCores(id: string): Promise<string[]> {
    const response = await axios.get<string[]>(`/se/${id}/cores`);
    return response.data;
  }
}
