import axios from "axios";
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts";

export interface TurStoreCollectionInfo {
  name: string;
  id: string;
  documentCount: number;
}

export class TurStoreInstanceService {
  async query(): Promise<TurStoreInstance[]> {
    const response = await axios.get<TurStoreInstance[]>("/store");
    return response.data;
  }
  async get(id: string): Promise<TurStoreInstance> {
    const response = await axios.get<TurStoreInstance>(`/store/${id}`);
    return response.data;
  }
  async create(turStoreInstance: TurStoreInstance): Promise<TurStoreInstance> {
    const response = await axios.post<TurStoreInstance>("/store",
      turStoreInstance
    );
    return response.data;
  }
  async update(turStoreInstance: TurStoreInstance): Promise<TurStoreInstance> {
    const response = await axios.put<TurStoreInstance>(
      `/store/${turStoreInstance.id.toString()}`,
      turStoreInstance
    );
    return response.data;
  }
  async delete(turStoreInstance: TurStoreInstance): Promise<boolean> {
    const response = await axios.delete<TurStoreInstance>(
      `/store/${turStoreInstance.id.toString()}`
    );
    return  response.status == 200;
  }

  async collections(id: string): Promise<TurStoreCollectionInfo[]> {
    const response = await axios.get<TurStoreCollectionInfo[]>(`/store/${id}/collections`);
    return response.data;
  }

  async clearCollection(id: string, collectionName: string): Promise<void> {
    await axios.delete(`/store/${id}/collections/${encodeURIComponent(collectionName)}/clear`);
  }

  async deleteCollection(id: string, collectionName: string): Promise<void> {
    await axios.delete(`/store/${id}/collections/${encodeURIComponent(collectionName)}`);
  }
}
