import axios from "axios";
import type { TurSECoreInfo } from "@/models/se/se-core-info.model.ts";
import type { TurSEInstance } from "@/models/se/se-instance.model.ts";
import type { TurSEVendor } from "@/models/se/se-vendor.model.ts";

export class TurSEInstanceService {
  async query(): Promise<TurSEInstance[]> {
    const response = await axios.get<TurSEInstance[]>("/se");
    return response.data;
  }
  async get(id: string): Promise<TurSEInstance> {
    const response = await axios.get<TurSEInstance>(`/se/${id}`);
    return response.data;
  }
  async create(turSEInstance: TurSEInstance): Promise<TurSEInstance> {
    const response = await axios.post<TurSEInstance>("/se",
      turSEInstance
    );
    return response.data;
  }
  async update(turSEInstance: TurSEInstance): Promise<TurSEInstance> {
    const response = await axios.put<TurSEInstance>(
      `/se/${turSEInstance.id.toString()}`,
      turSEInstance
    );
    return response.data;
  }
  async delete(turSEInstance: TurSEInstance): Promise<boolean> {
    const response = await axios.delete<TurSEInstance>(
      `/se/${turSEInstance.id.toString()}`
    );
    return  response.status == 200;
  }
  async getCores(id: string): Promise<TurSECoreInfo[]> {
    const response = await axios.get<TurSECoreInfo[]>(`/se/${id}/cores`);
    return response.data;
  }
  async deleteCore(id: string, core: string): Promise<void> {
    await axios.delete(`/se/${id}/cores/${core}`);
  }
  async createCore(id: string, name: string, locale: string): Promise<void> {
    await axios.post(`/se/${id}/cores`, { name, locale });
  }
  async clearCore(id: string, core: string): Promise<void> {
    await axios.delete(`/se/${id}/cores/${core}/documents`);
  }
  async getSystemInfo(id: string): Promise<Record<string, string>> {
    const response = await axios.get<Record<string, string>>(`/se/${id}/system-info`);
    return response.data;
  }
}

export class TurSEVendorService {
  async query(): Promise<TurSEVendor[]> {
    const response = await axios.get<TurSEVendor[]>("/se/vendor");
    return response.data;
  }
}
