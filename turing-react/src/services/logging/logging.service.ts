import type { TurLoggingGeneral } from "@/models/logging/logging-general.model";
import type { TurLoggingIndexing } from "@/models/logging/logging-indexing.model";
import type { TurLoggingPage } from "@/models/logging/logging-page.model";
import axios from "axios";

export interface TurGeneralLoggingParams {
  page?: number;
  pageSize?: number;
  level?: string;
  dateFrom?: string;
  dateTo?: string;
  search?: string;
  sort?: "asc" | "desc";
}

export interface TurIndexingLoggingParams {
  page?: number;
  pageSize?: number;
  dateFrom?: string;
  dateTo?: string;
  status?: string;
  contentId?: string;
  resultStatus?: string;
  url?: string;
  sort?: "asc" | "desc";
}

export class TurLoggingInstanceService {
  async server(params?: TurGeneralLoggingParams): Promise<TurLoggingPage<TurLoggingGeneral>> {
    const response = await axios.get<TurLoggingPage<TurLoggingGeneral>>("/logging", { params });
    return response.data;
  }
  async aem(params?: TurGeneralLoggingParams): Promise<TurLoggingPage<TurLoggingGeneral>> {
    const response = await axios.get<TurLoggingPage<TurLoggingGeneral>>("/logging/aem", { params });
    return response.data;
  }
  async indexing(params?: TurIndexingLoggingParams): Promise<TurLoggingPage<TurLoggingIndexing>> {
    const response = await axios.get<TurLoggingPage<TurLoggingIndexing>>("/logging/indexing", { params });
    return response.data;
  }
}
