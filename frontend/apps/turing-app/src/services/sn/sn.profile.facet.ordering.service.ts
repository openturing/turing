import type { TurProfile } from "@/models/profile/profile.model.ts";
import type { TurSNSiteFacetOrdering } from "@/models/sn/sn-site-facet-ordering.model.ts";
import axios from "axios";

/**
 * Facet ordering of a global profile for a site (spec 002) — mirrors the backend
 * {@code TurProfileFacetOrderingService}. Profiles are global and serve the whole
 * product; they are managed under {@link TurProfileService} (Management). Here the
 * site only selects one of them and configures its facet ordering for this site.
 */
export class TurSNProfileFacetOrderingService {
  /** Global profiles available to select on this site. */
  async query(siteId: string): Promise<TurProfile[]> {
    const response = await axios.get<TurProfile[]>(`/sn/${siteId}/facet/profile`);
    return response.data;
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
