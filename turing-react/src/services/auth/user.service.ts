import type { TurUser } from "@/models/auth/user";
import axios from "axios";

export class TurUserService {
  async get(): Promise<TurUser> {
    const response = await axios.get<TurUser>(`/v2/user/current`);
    return response.data;
  }

  async getByUsername(username: string): Promise<TurUser> {
    const response = await axios.get<TurUser>(`/v2/user/${username}`);
    return response.data;
  }

  async update(username: string, user: Partial<TurUser>): Promise<TurUser> {
    const response = await axios.put<TurUser>(`/v2/user/${username}`, user);
    return response.data;
  }

  async uploadAvatar(username: string, file: File): Promise<void> {
    const formData = new FormData();
    formData.append("file", file);
    await axios.post(`/v2/user/${username}/avatar`, formData, {
      headers: { "Content-Type": "multipart/form-data" },
    });
  }

  async deleteAvatar(username: string): Promise<void> {
    await axios.delete(`/v2/user/${username}/avatar`);
  }

  async getAvatarUrl(username: string): Promise<string> {
    const response = await axios.get(`/v2/user/${username}/avatar`, {
      responseType: "blob",
    });
    return URL.createObjectURL(response.data);
  }

  async saveAvatarUrl(username: string, avatarUrl: string | null): Promise<void> {
    await axios.put(`/v2/user/${username}/avatar-url`, { avatarUrl });
  }
}
