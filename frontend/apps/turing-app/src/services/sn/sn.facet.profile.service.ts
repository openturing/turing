import type { TurSNSiteFacetOrdering } from "@/models/sn/sn-site-facet-ordering.model.ts";
import type { TurSNSiteFacetProfile } from "@/models/sn/sn-site-facet-profile.model.ts";
import axios from "axios";

export interface TurSNSiteFacetProfilePayload {
  name: string;
  description?: string;
}

export class TurSNFacetProfileService {
  async query(siteId: string): Promise<TurSNSiteFacetProfile[]> {
    const response = await axios.get<TurSNSiteFacetProfile[]>(
      `/sn/${siteId}/facet/profile`,
    );
    return response.data;
  }

  async create(
    siteId: string,
    payload: TurSNSiteFacetProfilePayload,
  ): Promise<TurSNSiteFacetProfile> {
    const response = await axios.post<TurSNSiteFacetProfile>(
      `/sn/${siteId}/facet/profile`,
      payload,
    );
    return response.data;
  }

  async update(
    siteId: string,
    profileId: string,
    payload: TurSNSiteFacetProfilePayload,
  ): Promise<TurSNSiteFacetProfile> {
    const response = await axios.put<TurSNSiteFacetProfile>(
      `/sn/${siteId}/facet/profile/${profileId}`,
      payload,
    );
    return response.data;
  }

  async setDefault(
    siteId: string,
    profileId: string,
  ): Promise<TurSNSiteFacetProfile[]> {
    const response = await axios.put<TurSNSiteFacetProfile[]>(
      `/sn/${siteId}/facet/profile/${profileId}/default`,
    );
    return response.data;
  }

  async remove(siteId: string, profileId: string): Promise<void> {
    await axios.delete(`/sn/${siteId}/facet/profile/${profileId}`);
  }

  async queryOrdering(
    siteId: string,
    profileId: string,
  ): Promise<TurSNSiteFacetOrdering[]> {
    const response = await axios.get<TurSNSiteFacetOrdering[]>(
      `/sn/${siteId}/facet/profile/${profileId}/ordering`,
    );
    return response.data;
  }

  async saveOrdering(
    siteId: string,
    profileId: string,
    fields: TurSNSiteFacetOrdering[],
  ): Promise<TurSNSiteFacetOrdering[]> {
    const response = await axios.put<TurSNSiteFacetOrdering[]>(
      `/sn/${siteId}/facet/profile/${profileId}/ordering`,
      fields,
    );
    return response.data;
  }
}
