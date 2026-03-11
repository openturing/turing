import type { TurIntegrationDoubleCheck } from "@/models/integration/integration-double-check.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationDoubleCheckService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  private get connectorUrl(): string {
    return `/v2/integration/${this.integrationId}/connector`;
  }

  async get(connectorName: string): Promise<TurIntegrationDoubleCheck> {
    const { data } = await this.axiosInstance.get<TurIntegrationDoubleCheck>(
      `${this.connectorUrl}/validate/${connectorName}`
    );
    return data;
  }
}
