import axios from "axios";

export interface TurAnnResultItem {
  id: string;
  content: string;
  score?: number | null;
  metadata: Record<string, unknown>;
}

export interface TurAnnFacetItem {
  value: string;
  count: number;
}

export interface TurAnnSearchResponse {
  query: string;
  locale: string;
  topK: number;
  page: number;
  pageSize: number;
  totalHits: number;
  hasMore: boolean;
  results: TurAnnResultItem[];
  facets: Record<string, TurAnnFacetItem[]>;
}

export interface TurAnnSearchRequest {
  query: string;
  locale?: string;
  topK?: number;
  page?: number;
  pageSize?: number;
  filters?: Record<string, string[]>;
}

export class TurAnnSearchService {
  async search(siteName: string, body: TurAnnSearchRequest): Promise<TurAnnSearchResponse> {
    const response = await axios.post<TurAnnSearchResponse>(`/ann/${siteName}/search`, body);
    return response.data;
  }
}
