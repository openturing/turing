import type { TurSNSiteMerge } from "@/models/sn/sn-site-merge.model.ts";
import axios from "axios";

export class TurSNSiteMergeService {
  async query(siteId: string): Promise<TurSNSiteMerge[]> {
    const response = await axios.get<TurSNSiteMerge[]>(`/sn/${siteId}/merge`);
    return response.data;
  }
  async get(siteId: string, id: string): Promise<TurSNSiteMerge> {
    const response = await axios.get<TurSNSiteMerge>(
      `/sn/${siteId}/merge/${id}`,
    );
    return response.data;
  }
  async getStructure(siteId: string): Promise<TurSNSiteMerge> {
    const response = await axios.get<TurSNSiteMerge>(
      `/sn/${siteId}/merge/structure`,
    );
    return response.data;
  }
  async create(turSNSiteMerge: TurSNSiteMerge): Promise<TurSNSiteMerge> {
    const response = await axios.post<TurSNSiteMerge>(
      `/sn/${turSNSiteMerge.turSNSite.id.toString()}/merge`,
      turSNSiteMerge,
    );
    return response.data;
  }
  async update(turSNSiteMerge: TurSNSiteMerge): Promise<TurSNSiteMerge> {
    const response = await axios.put<TurSNSiteMerge>(
      `/sn/${turSNSiteMerge.turSNSite.id.toString()}/merge/${turSNSiteMerge.id.toString()}`,
      turSNSiteMerge,
    );
    return response.data;
  }
  async delete(turSNSiteMerge: TurSNSiteMerge): Promise<boolean> {
    const response = await axios.delete<TurSNSiteMerge>(
      `/sn/${turSNSiteMerge.turSNSite.id.toString()}/merge/${turSNSiteMerge.id.toString()}`,
    );
    return response.status == 200;
  }
}
