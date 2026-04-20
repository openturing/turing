import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model.ts";
import axios from "axios";

export class TurSNSiteLocaleService {
  async query(siteId: string): Promise<TurSNSiteLocale[]> {
    const response = await axios.get<TurSNSiteLocale[]>(`/sn/${siteId}/locale`);
    return response.data;
  }
  async get(siteId: string, id: string): Promise<TurSNSiteLocale> {
    const response = await axios.get<TurSNSiteLocale>(
      `/sn/${siteId}/locale/${id}`,
    );
    return response.data;
  }
  async create(
    turSNSiteId: string,
    turSNSiteLocale: TurSNSiteLocale,
  ): Promise<TurSNSiteLocale> {
    console.log(turSNSiteLocale);
    const response = await axios.post<TurSNSiteLocale>(
      `/sn/${turSNSiteId}/locale`,
      turSNSiteLocale,
    );
    return response.data;
  }
  async update(
    turSNSiteId: string,
    turSNSiteLocale: TurSNSiteLocale,
  ): Promise<TurSNSiteLocale> {
    const response = await axios.put<TurSNSiteLocale>(
      `/sn/${turSNSiteId}/locale/${turSNSiteLocale.id.toString()}`,
      turSNSiteLocale,
    );
    return response.data;
  }
  async delete(turSNSiteLocale: TurSNSiteLocale): Promise<boolean> {
    const response = await axios.delete<TurSNSiteLocale>(
      `/sn/${turSNSiteLocale.turSNSite.id.toString()}/locale/${turSNSiteLocale.id.toString()}`,
    );
    return response.status == 200;
  }
  async saveOrdering(
    siteId: string,
    locales: TurSNSiteLocale[],
  ): Promise<TurSNSiteLocale[]> {
    const response = await axios.put<TurSNSiteLocale[]>(
      `/sn/${siteId}/locale/ordering`,
      locales,
    );
    return response.data;
  }
}
