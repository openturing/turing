import type { TurIntegrationIndexingManager } from "@/models/integration/integration-indexing-manager.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationIndexingManagerService {
  private integrationId: string;
  private readonly axiosInstance: AxiosInstance;

  constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
    this.integrationId = integrationId;
    this.axiosInstance = axiosInstance;
  }

  setIntegrationId(integrationId: string): this {
    this.integrationId = integrationId;
    return this;
  }

  private getUrl(sourceName: string): string {
    return `/v2/integration/${this.integrationId}/aem/index/${sourceName}`;
  }

  async index(
    sourceName: string,
    payload: TurIntegrationIndexingManager,
  ): Promise<void> {
    await this.axiosInstance.post(this.getUrl(sourceName), payload);
  }

  async deindex(
    sourceName: string,
    payload: TurIntegrationIndexingManager,
  ): Promise<void> {
    await this.axiosInstance.post(this.getUrl(sourceName), payload);
  }

  async submit(
    sourceName: string,
    payload: TurIntegrationIndexingManager,
  ): Promise<void> {
    await this.axiosInstance.post(this.getUrl(sourceName), payload);
  }
}
