import type { TurDiscoveryAPI } from "@/models/auth/discovery";
import type { TurRestInfo } from "@/models/auth/rest-info";
import axios from "axios";

export class TurAuthorizationService {
  async login(username: string, password: string): Promise<TurRestInfo> {
    const baseURL = axios.defaults.baseURL ?? "";
    const response = await fetch(`${baseURL}/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ username, password }),
    });
    if (!response.ok) {
      throw new Error("Invalid credentials");
    }
    return response.json() as Promise<TurRestInfo>;
  }

  async discovery(): Promise<TurDiscoveryAPI> {
    const response = await axios.create().get<TurDiscoveryAPI>("/discovery");
    return response.data;
  }


}
