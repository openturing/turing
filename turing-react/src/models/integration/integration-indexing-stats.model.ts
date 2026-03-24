export interface TurIntegrationIndexingStats {
    id: number;
    provider: string;
    source: string;
    operationType: "INDEX_ALL" | "REINDEX_ALL" | "DRY_SCAN";
    startTime: string;
    endTime: string;
    documentCount: number;
    documentsPerMinute: number;
    environment: string | null;
    locale: string | null;
    sites: string[];
}
