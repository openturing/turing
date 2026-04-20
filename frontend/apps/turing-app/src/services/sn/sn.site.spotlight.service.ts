import type { TurSNSiteSpotlight } from "@/models/sn/sn-site-spotlight.model.ts";
import axios from "axios";

export class TurSNSiteSpotlightService {
  async query(siteId: string): Promise<TurSNSiteSpotlight[]> {
    const response = await axios.get<TurSNSiteSpotlight[]>(
      `/sn/${siteId}/spotlight`,
    );
    return response.data;
  }
  async get(siteId: string, id: string): Promise<TurSNSiteSpotlight> {
    const response = await axios.get<TurSNSiteSpotlight>(
      `/sn/${siteId}/spotlight/${id}`,
    );
    return response.data;
  }
  async getStructure(siteId: string): Promise<TurSNSiteSpotlight> {
    const response = await axios.get<TurSNSiteSpotlight>(
      `/sn/${siteId}/spotlight/structure`,
    );
    return response.data;
  }
  async create(
    turSNSiteSpotlight: TurSNSiteSpotlight,
  ): Promise<TurSNSiteSpotlight> {
    const response = await axios.post<TurSNSiteSpotlight>(
      `/sn/${turSNSiteSpotlight.turSNSite.id.toString()}/spotlight`,
      turSNSiteSpotlight,
    );
    return response.data;
  }
  async update(
    turSNSiteSpotlight: TurSNSiteSpotlight,
  ): Promise<TurSNSiteSpotlight> {
    const response = await axios.put<TurSNSiteSpotlight>(
      `/sn/${turSNSiteSpotlight.turSNSite.id.toString()}/spotlight/${turSNSiteSpotlight.id.toString()}`,
      turSNSiteSpotlight,
    );
    return response.data;
  }
  async delete(turSNSiteSpotlight: TurSNSiteSpotlight): Promise<boolean> {
    const response = await axios.delete<TurSNSiteSpotlight>(
      `/sn/${turSNSiteSpotlight.turSNSite.id.toString()}/spotlight/${turSNSiteSpotlight.id.toString()}`,
    );
    return response.status == 200;
  }
}
