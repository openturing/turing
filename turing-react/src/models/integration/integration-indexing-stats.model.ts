export interface TurIntegrationIndexingStats {
    id: number;
    provider: string;
    source: string;
    operationType: "INDEX_ALL" | "REINDEX_ALL";
    startTime: string;
    endTime: string;
    documentCount: number;
    documentsPerMinute: number;
}
