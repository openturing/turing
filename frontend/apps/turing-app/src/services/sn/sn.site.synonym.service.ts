import type {
  TurSESynonymApplyResult,
  TurSNSynonym,
  TurSNSynonymAlgoliaImportRequest,
  TurSNSynonymSupport,
} from "@/models/sn/sn-site-synonym.model.ts";
import axios from "axios";

/** T662–T666 — client for the engine-agnostic synonym API. */
export class TurSNSiteSynonymService {
  async query(siteId: string, locale?: string): Promise<TurSNSynonym[]> {
    const response = await axios.get<TurSNSynonym[]>(`/sn/${siteId}/synonym`, {
      params: locale ? { locale } : undefined,
    });
    return response.data;
  }

  async get(siteId: string, id: string): Promise<TurSNSynonym> {
    const response = await axios.get<TurSNSynonym>(`/sn/${siteId}/synonym/${id}`);
    return response.data;
  }

  async create(siteId: string, synonym: TurSNSynonym): Promise<TurSNSynonym> {
    const response = await axios.post<TurSNSynonym>(`/sn/${siteId}/synonym`, synonym);
    return response.data;
  }

  async batch(siteId: string, synonyms: TurSNSynonym[]): Promise<TurSNSynonym[]> {
    const response = await axios.post<TurSNSynonym[]>(`/sn/${siteId}/synonym/batch`, synonyms);
    return response.data;
  }

  /** AI-assisted mining: candidate sets from the search log (never auto-applied). */
  async mine(siteId: string, locale: string, limit = 100): Promise<TurSNSynonym[]> {
    const response = await axios.post<TurSNSynonym[]>(`/sn/${siteId}/synonym/mine`, null, {
      params: { locale, limit },
    });
    return response.data;
  }

  async update(siteId: string, id: string, synonym: TurSNSynonym): Promise<TurSNSynonym> {
    const response = await axios.put<TurSNSynonym>(`/sn/${siteId}/synonym/${id}`, synonym);
    return response.data;
  }

  async delete(siteId: string, id: string): Promise<boolean> {
    const response = await axios.delete(`/sn/${siteId}/synonym/${id}`);
    return response.status === 200;
  }

  async support(siteId: string): Promise<TurSNSynonymSupport> {
    const response = await axios.get<TurSNSynonymSupport>(`/sn/${siteId}/synonym/support`);
    return response.data;
  }

  /** Push synonyms into the engine (all locales, or one). */
  async apply(
    siteId: string,
    locale?: string,
  ): Promise<Record<string, TurSESynonymApplyResult>> {
    const response = await axios.post<Record<string, TurSESynonymApplyResult>>(
      `/sn/${siteId}/synonym/apply`,
      null,
      { params: locale ? { locale } : undefined },
    );
    return response.data;
  }

  /** One-click "Import from Algolia" — closes the migration loop. */
  async importFromAlgolia(
    siteId: string,
    request: TurSNSynonymAlgoliaImportRequest,
  ): Promise<TurSNSynonym[]> {
    const response = await axios.post<TurSNSynonym[]>(
      `/sn/${siteId}/synonym/import/algolia`,
      request,
    );
    return response.data;
  }
}
