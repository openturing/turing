import type { TurUser } from "@/models/auth/user";
import axios from "axios";

export class TurAdminUserService {
  async query(): Promise<TurUser[]> {
    const response = await axios.get<TurUser[]>("/v2/user");
    return response.data;
  }

  async get(username: string): Promise<TurUser> {
    const response = await axios.get<TurUser>(`/v2/user/${username}`);
    return response.data;
  }

  async create(user: TurUser): Promise<TurUser> {
    const response = await axios.post<TurUser>("/v2/user", user);
    return response.data;
  }

  async update(username: string, user: TurUser): Promise<TurUser> {
    const response = await axios.put<TurUser>(`/v2/user/${username}`, user);
    return response.data;
  }

  async delete(username: string): Promise<boolean> {
    const response = await axios.delete(`/v2/user/${username}`);
    return response.status === 200;
  }
}
