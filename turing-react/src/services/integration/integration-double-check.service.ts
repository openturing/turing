import type { TurIntegrationDoubleCheck } from "@/models/integration/integration-double-check.model";
import type { TurIntegrationMonitoring } from "@/models/integration/integration-monitoring.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationDoubleCheckService {
  private readonly integrationId: string;
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

  async getUnprocessed(source: string): Promise<TurIntegrationMonitoring> {
    const { data } = await this.axiosInstance.get<TurIntegrationMonitoring>(
      `${this.connectorUrl}/monitoring/indexing/unprocessed/${source}`
    );
    return data;
  }

  async indexById(source: string, contentId: string): Promise<Record<string, string>> {
    const { data } = await this.axiosInstance.get<Record<string, string>>(
      `${this.connectorUrl}/index/${source}/id/${contentId}`
    );
    return data;
  }
}
