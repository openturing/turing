import type { TurRole } from "@/models/auth/role";
import axios from "axios";

export class TurRoleService {
  async query(): Promise<TurRole[]> {
    const response = await axios.get<TurRole[]>("/v2/role");
    return response.data;
  }

  async get(id: string): Promise<TurRole> {
    const response = await axios.get<TurRole>(`/v2/role/${id}`);
    return response.data;
  }

  async create(role: TurRole): Promise<TurRole> {
    const response = await axios.post<TurRole>("/v2/role", role);
    return response.data;
  }

  async update(id: string, role: TurRole): Promise<TurRole> {
    const response = await axios.put<TurRole>(`/v2/role/${id}`, role);
    return response.data;
  }

  async delete(id: string): Promise<boolean> {
    const response = await axios.delete(`/v2/role/${id}`);
    return response.status === 200;
  }
}
