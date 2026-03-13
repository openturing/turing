import type { TurIntegrationIndexing } from "./integration-indexing.model";

export type TurIntegrationMonitoringPage = {
  sources: string[];
  environments: string[];
  locales: string[];
  sites: string[];
  content: TurIntegrationIndexing[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
