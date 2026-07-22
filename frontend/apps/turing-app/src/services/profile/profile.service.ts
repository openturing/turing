import type { TurProfile } from "@/models/profile/profile.model.ts";
import axios from "axios";

export interface TurProfilePayload {
  name: string;
  description?: string;
}

/** CRUD of global profiles, managed under the console "Management" section (spec 002). */
export class TurProfileService {
  async query(): Promise<TurProfile[]> {
    const response = await axios.get<TurProfile[]>(`/profile`);
    return response.data;
  }

  async create(payload: TurProfilePayload): Promise<TurProfile> {
    const response = await axios.post<TurProfile>(`/profile`, payload);
    return response.data;
  }

  async update(profileId: string, payload: TurProfilePayload): Promise<TurProfile> {
    const response = await axios.put<TurProfile>(`/profile/${profileId}`, payload);
    return response.data;
  }

  async setDefault(profileId: string): Promise<TurProfile[]> {
    const response = await axios.put<TurProfile[]>(`/profile/${profileId}/default`);
    return response.data;
  }

  async remove(profileId: string): Promise<void> {
    await axios.delete(`/profile/${profileId}`);
  }
}
