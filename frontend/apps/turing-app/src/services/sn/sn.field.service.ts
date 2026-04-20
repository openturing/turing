import type { TurSNStatusFields } from "@/models/sn/sn-field-status.model.ts";
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model.ts";
import axios from "axios";

export class TurSNFieldService {
  async query(id: string): Promise<TurSNSiteField[]> {
    const response = await axios.get<TurSNSiteField[]>(`/sn/${id}/field/ext`);
    return response.data;
  }
  async getStatusFields(id: string): Promise<TurSNStatusFields> {
    const response = await axios.get<TurSNStatusFields>(
      `/sn/${id}/field/check`,
    );
    return response.data;
  }
  async get(id: string, fieldId: string): Promise<TurSNSiteField> {
    const response = await axios.get<TurSNSiteField>(
      `/sn/${id}/field/ext/${fieldId}`,
    );
    return response.data;
  }
  async create(
    turSNSiteId: string,
    turSNField: TurSNSiteField,
  ): Promise<TurSNSiteField> {
    const response = await axios.post<TurSNSiteField>(
      `/sn/${turSNSiteId}/field/ext`,
      turSNField,
    );
    return response.data;
  }
  async update(
    turSNSiteId: string,
    turSNField: TurSNSiteField,
  ): Promise<TurSNSiteField> {
    const response = await axios.put<TurSNSiteField>(
      `/sn/${turSNSiteId}/field/ext/${turSNField.id.toString()}`,
      turSNField,
    );
    return response.data;
  }
  async delete(
    turSNSiteId: string,
    turSNField: TurSNSiteField,
  ): Promise<boolean> {
    const response = await axios.delete<TurSNSiteField>(
      `/sn/${turSNSiteId}/field/ext/${turSNField.id.toString()}`,
    );
    return response.status == 200;
  }
}
