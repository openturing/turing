export interface TurIntegrationIndexingManager {
  attribute: "ID" | "URL";
  paths: string[];
  event: "PUBLISHING" | "UNPUBLISHING" | "INDEXING" | "DEINDEXING";
  recursive?: boolean;
}
