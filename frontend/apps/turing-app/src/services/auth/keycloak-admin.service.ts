import type { TurKeycloakGroup } from "@/models/auth/keycloak-group";
import type { TurKeycloakUser } from "@/models/auth/keycloak-user";
import axios from "axios";

export class TurKeycloakAdminService {
  async listUsers(): Promise<TurKeycloakUser[]> {
    const response = await axios.get<TurKeycloakUser[]>("/v2/keycloak/users");
    return response.data;
  }

  async getUser(username: string): Promise<TurKeycloakUser> {
    const response = await axios.get<TurKeycloakUser>(`/v2/keycloak/users/${encodeURIComponent(username)}`);
    return response.data;
  }

  async listGroups(): Promise<TurKeycloakGroup[]> {
    const response = await axios.get<TurKeycloakGroup[]>("/v2/keycloak/groups");
    return response.data;
  }

  async getGroup(id: string): Promise<TurKeycloakGroup> {
    const response = await axios.get<TurKeycloakGroup>(`/v2/keycloak/groups/${encodeURIComponent(id)}`);
    return response.data;
  }
}
