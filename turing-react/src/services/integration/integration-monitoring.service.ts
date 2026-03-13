import type { TurIntegrationMonitoring } from "@/models/integration/integration-monitoring.model";
import type { TurIntegrationMonitoringPage } from "@/models/integration/integration-monitoring-page.model";
import type { TurIntegrationMonitoringRequest } from "@/models/integration/integration-monitoring-request.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationMonitoringService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;
  private endpointPrefix: string;
  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
    this.endpointPrefix = `/v2/integration/${this.integrationId}/connector/monitoring/indexing`;
  }

  async query(): Promise<TurIntegrationMonitoring> {
    const { data } = await this.axiosInstance.get<TurIntegrationMonitoring>(
      `${this.endpointPrefix}`
    );
    return data;
  }

  async get(source: string): Promise<TurIntegrationMonitoring> {
    const { data } = await this.axiosInstance.get<TurIntegrationMonitoring>(
      `${this.endpointPrefix}/${source}`
    );
    return data;
  }

  async search(request: TurIntegrationMonitoringRequest): Promise<TurIntegrationMonitoringPage> {
    const { data } = await this.axiosInstance.post<TurIntegrationMonitoringPage>(
      `${this.endpointPrefix}/_search`,
      request
    );
    return data;
  }
}
