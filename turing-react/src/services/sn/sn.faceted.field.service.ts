import type { TurSNSiteFacetOrdering } from "@/models/sn/sn-site-facet-ordering.model.ts";
import axios from "axios";

export class TurSNFacetedFieldService {
  async query(id: string): Promise<TurSNSiteFacetOrdering[]> {
    const response = await axios.get<TurSNSiteFacetOrdering[]>(
      `/sn/${id}/facet`,
    );
    return response.data;
  }

  async saveOrdering(
    id: string,
    fields: TurSNSiteFacetOrdering[],
  ): Promise<TurSNSiteFacetOrdering[]> {
    const response = await axios.put<TurSNSiteFacetOrdering[]>(
      `/sn/${id}/facet/ordering`,
      fields,
    );
    return response.data;
  }
}
