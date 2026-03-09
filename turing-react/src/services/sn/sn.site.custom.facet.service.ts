import type {
  TurSNSiteCustomFacet,
  TurSNSiteCustomFacetFieldOption,
} from "@/models/sn/sn-site-custom-facet.model";
import axios from "axios";

type BackendCustomFacet = {
  id?: string;
  name: string;
  defaultLabel?: string;
  label?: Record<string, string>;
  facetPosition?: number;
  facetType?: "DEFAULT" | "AND" | "OR";
  facetItemType?: "DEFAULT" | "AND" | "OR";
  items?: BackendCustomFacetItem[];
  fieldExtId?: string;
  fieldExtName?: string;
  fieldExtType?: string;
};

type BackendCustomFacetItem = {
  id?: string;
  label: string;
  position?: number;
  rangeStart?: number | null;
  rangeEnd?: number | null;
  rangeStartDate?: string | null;
  rangeEndDate?: string | null;
};

type BackendFieldExt = {
  id: string;
  name: string;
  type?: string;
  customFacets?: BackendCustomFacet[];
};

export class TurSNSiteCustomFacetService {
  private async getFields(snSiteId: string): Promise<BackendFieldExt[]> {
    const response = await axios.get<BackendFieldExt[]>(
      `/sn/${snSiteId}/field/ext`,
    );
    return response.data;
  }

  private async getCustomFacets(
    snSiteId: string,
  ): Promise<TurSNSiteCustomFacet[]> {
    const response = await axios.get<TurSNSiteCustomFacet[]>(
      `/sn/${snSiteId}/custom-facet`,
    );
    return response.data;
  }

  async query(snSiteId: string): Promise<TurSNSiteCustomFacet[]> {
    const customFacets = await this.getCustomFacets(snSiteId);
    return [...customFacets].sort(
      (a, b) =>
        (a.facetPosition ?? Number.MAX_SAFE_INTEGER) -
        (b.facetPosition ?? Number.MAX_SAFE_INTEGER),
    );
  }

  async get(
    snSiteId: string,
    customFacetId: string,
  ): Promise<TurSNSiteCustomFacet> {
    const response = await axios.get<TurSNSiteCustomFacet>(
      `/sn/${snSiteId}/custom-facet/${customFacetId}`,
    );
    return response.data;
  }

  async getFieldOptions(
    snSiteId: string,
  ): Promise<TurSNSiteCustomFacetFieldOption[]> {
    const fields = await this.getFields(snSiteId);
    return fields
      .map((field) => ({ id: field.id, name: field.name, type: field.type }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }

  async create(
    snSiteId: string,
    customFacet: TurSNSiteCustomFacet,
  ): Promise<TurSNSiteCustomFacet> {
    const response = await axios.post<TurSNSiteCustomFacet>(
      `/sn/${snSiteId}/custom-facet`,
      customFacet,
    );
    return response.data;
  }

  async update(
    snSiteId: string,
    customFacet: TurSNSiteCustomFacet,
  ): Promise<TurSNSiteCustomFacet> {
    if (!customFacet.id) throw new Error("Custom facet id is required.");
    const response = await axios.put<TurSNSiteCustomFacet>(
      `/sn/${snSiteId}/custom-facet/${customFacet.id}`,
      customFacet,
    );
    return response.data;
  }

  async delete(snSiteId: string, customFacetId: string): Promise<boolean> {
    const response = await axios.delete<boolean>(
      `/sn/${snSiteId}/custom-facet/${customFacetId}`,
    );
    return response.data === true || response.status === 200;
  }
}
