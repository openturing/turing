import type { TurSNSiteStatus } from "@/models/sn/sn-site-monitoring.model.ts";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import axios from "axios";

export class TurSNSiteService {
  async query(): Promise<TurSNSite[]> {
    const response = await axios.get<TurSNSite[]>("/sn");
    return response.data;
  }
  async get(id: string): Promise<TurSNSite> {
    const response = await axios.get<TurSNSite>(`/sn/${id}`);
    return response.data;
  }

  async getStatus(id: string): Promise<TurSNSiteStatus> {
    const response = await axios.get<TurSNSiteStatus>(`/sn/${id}/monitoring`);
    return response.data;
  }

  async create(turSNSite: TurSNSite): Promise<TurSNSite> {
    const response = await axios.post<TurSNSite>("/sn", turSNSite);
    return response.data;
  }
  async update(turSNSite: TurSNSite): Promise<TurSNSite> {
    const response = await axios.put<TurSNSite>(
      `/sn/${turSNSite.id.toString()}`,
      turSNSite,
    );
    return response.data;
  }
  async delete(turSNSite: TurSNSite): Promise<boolean> {
    const response = await axios.delete<TurSNSite>(
      `/sn/${turSNSite.id.toString()}`,
    );
    return response.status == 200;
  }
  async export(turSNSite: TurSNSite): Promise<Blob | null> {
    const response = await axios
      .get<Blob>(`/sn/${turSNSite.id.toString()}/export`, {
        responseType: "blob",
      })
      .then((res) => res.data)
      .catch((error) => {
        console.error("Failed to export SN site", error);
        return null;
      });
    return response;
  }
}
