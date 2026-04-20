import type { TurKeycloakUser } from "./keycloak-user";

export type TurKeycloakGroup = {
  id: string;
  name: string;
  path?: string;
  realmRoles?: string[];
  subGroups?: TurKeycloakGroup[];
  members?: TurKeycloakUser[];
};
