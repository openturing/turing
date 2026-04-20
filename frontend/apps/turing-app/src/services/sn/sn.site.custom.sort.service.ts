import type {
  TurSNSiteCustomSort,
  TurSNSiteCustomSortFieldOption,
} from "@/models/sn/sn-site-custom-sort.model";
import axios from "axios";

export class TurSNSiteCustomSortService {
  async query(snSiteId: string): Promise<TurSNSiteCustomSort[]> {
    const response = await axios.get<TurSNSiteCustomSort[]>(
      `/sn/${snSiteId}/custom-sort`,
    );
    return response.data;
  }

  async get(
    snSiteId: string,
    customSortId: string,
  ): Promise<TurSNSiteCustomSort> {
    const response = await axios.get<TurSNSiteCustomSort>(
      `/sn/${snSiteId}/custom-sort/${customSortId}`,
    );
    return response.data;
  }

  async getFieldOptions(
    snSiteId: string,
  ): Promise<TurSNSiteCustomSortFieldOption[]> {
    const response = await axios.get<TurSNSiteCustomSortFieldOption[]>(
      `/sn/${snSiteId}/custom-sort/fields`,
    );
    return response.data;
  }

  async create(
    snSiteId: string,
    customSort: TurSNSiteCustomSort,
  ): Promise<TurSNSiteCustomSort> {
    const response = await axios.post<TurSNSiteCustomSort>(
      `/sn/${snSiteId}/custom-sort`,
      customSort,
    );
    return response.data;
  }

  async update(
    snSiteId: string,
    customSort: TurSNSiteCustomSort,
  ): Promise<TurSNSiteCustomSort> {
    if (!customSort.id) throw new Error("Custom sort id is required.");
    const response = await axios.put<TurSNSiteCustomSort>(
      `/sn/${snSiteId}/custom-sort/${customSort.id}`,
      customSort,
    );
    return response.data;
  }

  async delete(snSiteId: string, customSortId: string): Promise<boolean> {
    const response = await axios.delete<boolean>(
      `/sn/${snSiteId}/custom-sort/${customSortId}`,
    );
    return response.data === true || response.status === 200;
  }
}
