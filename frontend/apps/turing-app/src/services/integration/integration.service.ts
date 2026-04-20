import axios from "axios";
import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model.ts";

export class TurIntegrationInstanceService {
  async query(): Promise<TurIntegrationInstance[]> {
    const response = await axios.get<TurIntegrationInstance[]>("/integration");
    return response.data;
  }
  async get(id: string): Promise<TurIntegrationInstance> {
    const response = await axios.get<TurIntegrationInstance>(`/integration/${id}`);
    return response.data;
  }
  async create(turIntegrationInstance: TurIntegrationInstance): Promise<TurIntegrationInstance> {
    const response = await axios.post<TurIntegrationInstance>("/integration",
      turIntegrationInstance
    );
    return response.data;
  }
  async update(turIntegrationInstance: TurIntegrationInstance): Promise<TurIntegrationInstance> {
    const response = await axios.put<TurIntegrationInstance>(
      `/integration/${turIntegrationInstance.id.toString()}`,
      turIntegrationInstance
    );
    return response.data;
  }
  async delete(turIntegrationInstance: TurIntegrationInstance): Promise<boolean> {
    const response = await axios.delete<TurIntegrationInstance>(
      `/integration/${turIntegrationInstance.id.toString()}`
    );
    return  response.status == 200;
  }
}
