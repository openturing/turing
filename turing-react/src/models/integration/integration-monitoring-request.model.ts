export type TurIntegrationMonitoringRequest = {
  page: number;
  size: number;
  sortBy: string;
  sortDirection: "asc" | "desc";
  source?: string;
  objectId?: string;
  statuses?: string[];
  environment?: string;
  locale?: string;
  site?: string;
  dateFrom?: string;
  dateTo?: string;
};
