import type { TurIntegrationInstance } from "@/models/integration/integration-instance.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationInstanceService {
  private readonly axiosInstance: AxiosInstance;

  constructor(axiosInstance: AxiosInstance = axios) {
    this.axiosInstance = axiosInstance;
  }

  async query(): Promise<TurIntegrationInstance[]> {
    const { data } = await this.axiosInstance.get<TurIntegrationInstance[]>(
      "/integration"
    );
    return data;
  }

  async get(id: string): Promise<TurIntegrationInstance> {
    const { data } = await this.axiosInstance.get<TurIntegrationInstance>(
      `/integration/${id}`
    );
    return data;
  }

  async getStructure(): Promise<TurIntegrationInstance[]> {
    const { data } = await this.axiosInstance.get<TurIntegrationInstance[]>(
      `/integration/structure`
    );
    return data;
  }

  async create(
    source: TurIntegrationInstance
  ): Promise<TurIntegrationInstance> {
    const { data } = await this.axiosInstance.post<TurIntegrationInstance>(
      `/integration`,
      source
    );
    return data;
  }

  async update(
    source: TurIntegrationInstance
  ): Promise<TurIntegrationInstance> {
    const { data } = await this.axiosInstance.put<TurIntegrationInstance>(
      `/integration/${source.id}`,
      source
    );
    return data;
  }

  async delete(source: TurIntegrationInstance): Promise<boolean> {
    const { status } = await this.axiosInstance.delete(
      `/integration/${source.id}`
    );
    return status === 200;
  }
}
