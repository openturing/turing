import type { TurGroup } from "@/models/auth/group";
import axios from "axios";

export class TurGroupService {
  async query(): Promise<TurGroup[]> {
    const response = await axios.get<TurGroup[]>("/v2/group");
    return response.data;
  }

  async get(id: string): Promise<TurGroup> {
    const response = await axios.get<TurGroup>(`/v2/group/${id}`);
    return response.data;
  }

  async create(group: TurGroup): Promise<TurGroup> {
    const response = await axios.post<TurGroup>("/v2/group", group);
    return response.data;
  }

  async update(id: string, group: TurGroup): Promise<TurGroup> {
    const response = await axios.put<TurGroup>(`/v2/group/${id}`, group);
    return response.data;
  }

  async delete(id: string): Promise<boolean> {
    const response = await axios.delete(`/v2/group/${id}`);
    return response.status === 200;
  }
}
