import type { TurIntegrationIndexingStats } from "@/models/integration/integration-indexing-stats.model";
import axios, { type AxiosInstance } from "axios";

export class TurIntegrationIndexingStatsService {
    private readonly axiosInstance: AxiosInstance;
    private endpointPrefix: string;

    constructor(integrationId: string, axiosInstance: AxiosInstance = axios) {
        this.axiosInstance = axiosInstance;
        this.endpointPrefix = `/v2/integration/${integrationId}/connector/monitoring/indexing`;
    }

    async getAll(): Promise<TurIntegrationIndexingStats[]> {
        const { data } = await this.axiosInstance.get<TurIntegrationIndexingStats[]>(
            `${this.endpointPrefix}/stats`
        );
        return data;
    }

    async getBySource(source: string): Promise<TurIntegrationIndexingStats[]> {
        const { data } = await this.axiosInstance.get<TurIntegrationIndexingStats[]>(
            `${this.endpointPrefix}/stats/${source}`
        );
        return data;
    }

    async getCountBySource(): Promise<Record<string, number>> {
        const { data } = await this.axiosInstance.get<Record<string, number>>(
            `${this.endpointPrefix}/stats/count`
        );
        return data;
    }
}
